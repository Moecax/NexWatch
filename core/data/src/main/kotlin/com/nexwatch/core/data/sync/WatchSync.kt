package com.nexwatch.core.data.sync

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.watchapi.SyncProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WatchSyncStatus {
    data object Idle : WatchSyncStatus

    /** [fraction] is null until the watch says how many data types it will send. */
    data class Running(val fraction: Float?, val dataType: String?) : WatchSyncStatus
}

/**
 * The single way into [HealthSyncCoordinator]. The connection trigger, the WorkManager
 * safety net and the "Sync now" buttons all queue here, so two syncs never interleave,
 * screens can show progress, and the last successful sync is recorded for "Synced 4 min ago".
 */
@Singleton
class WatchSync @Inject constructor(
    private val coordinator: HealthSyncCoordinator,
    private val diagnostics: DiagnosticsStore,
    dispatchers: CoroutineDispatchers,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _status = MutableStateFlow<WatchSyncStatus>(WatchSyncStatus.Idle)
    val status: StateFlow<WatchSyncStatus> = _status.asStateFlow()

    /** Waits behind a sync that is already running instead of starting a second one beside it. */
    suspend fun run(): Result<Set<String>> = mutex.withLock {
        _status.value = WatchSyncStatus.Running(fraction = null, dataType = null)
        var completed = false
        try {
            val result = coordinator.syncAndNormalize { progress ->
                if (progress.completed) completed = true
                _status.value = WatchSyncStatus.Running(progress.fraction(), progress.batch?.dataType)
            }
            // The coordinator also succeeds, doing nothing, when the watch isn't connected;
            // only a sync the watch actually finished counts as "synced".
            if (result.isSuccess && completed) diagnostics.recordSync(System.currentTimeMillis())
            result
        } finally {
            _status.value = WatchSyncStatus.Idle
        }
    }

    /**
     * For buttons. Runs in this singleton's scope, not the screen's: a sync cancelled half-way
     * can leave daily summaries stale (see WatchConnectionService), so leaving the screen
     * must not stop it. A tap while one is running is ignored.
     */
    fun requestSync() {
        if (mutex.isLocked) return
        scope.launch { run() }
    }

    private fun SyncProgress.fraction(): Float? = when {
        completed -> 1f
        totalItems > 0 -> (itemsSynced.toFloat() / totalItems).coerceIn(0f, 1f)
        else -> null
    }
}
