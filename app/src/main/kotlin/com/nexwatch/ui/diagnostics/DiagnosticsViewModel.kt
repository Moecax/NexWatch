package com.nexwatch.ui.diagnostics

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.BuildConfig
import com.nexwatch.core.data.diagnostics.DiagnosticEvent
import com.nexwatch.core.data.diagnostics.DiagnosticEventKind
import com.nexwatch.core.data.diagnostics.DiagnosticsSnapshot
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.identity.WatchIdentity
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.data.notification.WatchReachResult
import com.nexwatch.core.data.notification.WatchReachTest
import com.nexwatch.core.data.storage.StorageRepository
import com.nexwatch.core.data.storage.StorageSummary
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchfake.FakeWatchClient
import com.nexwatch.core.watchfake.WatchDebugController
import com.nexwatch.di.WatchImplPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** What the background-connection test is doing right now (design Batch 8 #5 logs card). */
sealed interface BackgroundTestResult {
    data object Idle : BackgroundTestResult
    data object Running : BackgroundTestResult
    data object Passed : BackgroundTestResult
    data class Failed(val reason: String) : BackgroundTestResult
}

/** The file the user picked with "Export logs", for the file row under the actions. */
data class LogExport(val displayName: String, val sizeBytes: Long, val uri: android.net.Uri)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    watchClient: WatchClient,
    private val debugController: WatchDebugController,
    private val watchImplPreference: WatchImplPreference,
    diagnosticsStore: DiagnosticsStore,
    private val storageRepository: StorageRepository,
    watchIdentityStore: WatchIdentityStore,
    private val reachTest: WatchReachTest,
) : ViewModel() {

    /**
     * Whether the client running *now* is the fake. The switch below is a preference that only
     * applies after a restart, so it can't say which client the debug hooks would reach.
     */
    val fakeInUse: Boolean = watchClient is FakeWatchClient

    /** The §12 Phase 4 fake/real toggle. Debug builds only; release has no choice to offer. */
    val showImplToggle: Boolean = BuildConfig.DEBUG

    private val _useRealWatch = MutableStateFlow(watchImplPreference.useRealWatch)
    val useRealWatch: StateFlow<Boolean> = _useRealWatch.asStateFlow()

    /** Takes effect on the next process start; the client is a @Singleton already in use. */
    fun setUseRealWatch(enabled: Boolean) {
        watchImplPreference.useRealWatch = enabled
        _useRealWatch.value = enabled
    }

    val capabilities: StateFlow<WatchCapabilities?> = watchClient.capabilities.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val state: StateFlow<WatchState> = watchClient.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WatchState.Unbound,
    )

    val diagnostics: StateFlow<DiagnosticsSnapshot> = diagnosticsStore.snapshot.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiagnosticsSnapshot(null, null, null),
    )

    val events: StateFlow<List<DiagnosticEvent>> = diagnosticsStore.events.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** The bound address: the hero card shows its last four characters, as the design does. */
    val identity: StateFlow<WatchIdentity?> = watchIdentityStore.identity.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _storage = MutableStateFlow<StorageSummary?>(null)
    val storage: StateFlow<StorageSummary?> = _storage.asStateFlow()

    init {
        refreshStorage()
    }

    /** Counted once per screen entry — the "Records on phone" KPI. Never on a timer. */
    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = runCatching { storageRepository.summary() }.getOrNull()
        }
    }

    // region Phase 4 dev controls (forceable fake states, fake/real choice)

    val forceableStates: List<Pair<String, WatchState>> = listOf(
        "Unbound" to WatchState.Unbound,
        "Bluetooth off" to WatchState.BluetoothOff,
        "Waiting to retry" to WatchState.Waiting(nextRetryAt = Instant.now().plusSeconds(30)),
        "Connecting" to WatchState.Connecting,
        "Ready (72%)" to WatchState.Ready(battery = 72),
        "Auth failed" to WatchState.AuthFailed(reason = "simulated mismatch"),
    )

    fun forceState(state: WatchState) = debugController.forceState(state)

    /** One-shot on the fake client: the next sync is all it applies to, so it can be watched. */
    fun armNextSyncToHang() {
        debugController.nextSyncHangs = true
    }

    fun armNextSyncToFail() {
        debugController.nextSyncError = IllegalStateException("simulated sync failure")
    }

    // endregion

    private val _backgroundTest = MutableStateFlow<BackgroundTestResult>(BackgroundTestResult.Idle)
    val backgroundTest: StateFlow<BackgroundTestResult> = _backgroundTest.asStateFlow()

    /** The same check onboarding's "Test" runs (WatchReachTest), so the two can't disagree. */
    fun runBackgroundTest() {
        if (_backgroundTest.value == BackgroundTestResult.Running) return
        _backgroundTest.value = BackgroundTestResult.Running
        viewModelScope.launch {
            _backgroundTest.value = when (val reach = reachTest.run()) {
                WatchReachResult.Passed -> BackgroundTestResult.Passed
                is WatchReachResult.Failed -> BackgroundTestResult.Failed(reach.reason)
            }
        }
    }

    private val _exportedLog = MutableStateFlow<LogExport?>(null)
    val exportedLog: StateFlow<LogExport?> = _exportedLog.asStateFlow()

    /**
     * Writes the persisted event log — not a fabricated tail — to the file the user
     * picked with the Storage Access Framework. Built here so the preview on screen
     * and the exported file are the same text.
     */
    fun exportLogs(uri: Uri) {
        val bytes = buildLogFile(
            events = events.value,
            firmware = capabilities.value?.firmwareVersion,
            address = identity.value?.boundAddress,
        ).toByteArray(Charsets.UTF_8)
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                        ?: error("Cannot open $uri")
                }.isSuccess
            }
            if (!written) return@launch
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                    }
                }.getOrNull()
            } ?: uri.lastPathSegment ?: "nexwatch-diagnostics.log"
            _exportedLog.value = LogExport(name, bytes.size.toLong(), uri)
        }
    }

}

private val LOG_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/**
 * One log line per timeline event, e.g. `14:02:31.877  sync   completed heart rate, steps`.
 * Shared by the on-screen preview and the exported file so they can't disagree.
 */
internal fun logLine(event: DiagnosticEvent): String {
    val time = Instant.ofEpochMilli(event.atMs).atZone(ZoneId.systemDefault()).format(LOG_TIME)
    val (tag, message) = when (event.kind) {
        DiagnosticEventKind.CONNECTED -> "watch" to "connected"
        DiagnosticEventKind.RECONNECTED -> "link" to "reconnected"
        DiagnosticEventKind.DISCONNECTED -> "link" to (event.detail ?: "disconnected")
        DiagnosticEventKind.SYNC_COMPLETED -> "sync" to (event.detail?.let { "completed $it" } ?: "completed")
    }
    return "$time  ${tag.padEnd(5)}  $message"
}

private fun buildLogFile(events: List<DiagnosticEvent>, firmware: String?, address: String?): String = buildString {
    appendLine("NexWatch diagnostics")
    appendLine("exported ${Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
    firmware?.let { appendLine("firmware $it") }
    address?.let { appendLine("watch ends ${it.takeLast(4)}") }
    appendLine()
    if (events.isEmpty()) {
        appendLine("no connection events recorded yet")
    } else {
        events.forEach { appendLine(logLine(it)) }
    }
}
