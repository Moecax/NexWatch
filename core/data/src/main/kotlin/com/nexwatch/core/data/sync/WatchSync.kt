package com.nexwatch.core.data.sync

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.normalize.NoBoundWatchException
import com.nexwatch.core.watchapi.SyncProgress
import com.nexwatch.core.watchapi.WatchCommandTimeoutException
import com.nexwatch.core.watchapi.WatchNotReadyException
import com.nexwatch.core.watchapi.WatchSyncBusyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Long enough that the client's own 10-minute sync cap always fires first, short enough that
 * this mutex cannot be held for the rest of the day. The guard exists so that the lock is
 * released no matter what the SDK does — it is the reason a wedged sync can't block the next.
 */
internal val SYNC_GUARD_TIMEOUT = 12.minutes

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
        try {
            var completed = false
            val dataTypes = sortedSetOf<String>()
            val attempt = try {
                withTimeout(SYNC_GUARD_TIMEOUT) {
                    coordinator.syncAndNormalize { progress ->
                        if (progress.completed) completed = true
                        progress.batch?.let { dataTypes += it.dataType.removeSuffix("_measure").replace('_', ' ') }
                        _status.value = WatchSyncStatus.Running(progress.fraction(), progress.batch?.dataType)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Only this one: another CancellationException belongs to a caller that is
                // already unwinding, and swallowing it would strand this coroutine mid-stop.
                Result.failure(WatchCommandTimeoutException("sync"))
            }
            // The guard's timeout can also come back wrapped by the coordinator's runCatching
            // rather than thrown; either way it is the stall the guard exists for.
            val result = if (attempt.exceptionOrNull() is TimeoutCancellationException) {
                Result.failure(WatchCommandTimeoutException("sync"))
            } else {
                attempt
            }
            val now = System.currentTimeMillis()
            // The coordinator also succeeds, doing nothing, when the watch isn't connected;
            // only a sync the watch actually finished counts as "synced", so that case records
            // neither a time nor an attempt.
            val failure = result.exceptionOrNull()
            when {
                // The coordinator's runCatching also catches the caller's own cancellation;
                // that is the caller stopping, not the sync failing, so it isn't recorded.
                failure is CancellationException -> throw failure
                failure != null -> diagnostics.recordSyncFailed(now, failureReason(failure))
                completed -> diagnostics.recordSyncSucceeded(now, detail = dataTypes.joinToString(", ").ifEmpty { null })
            }
            result
        } finally {
            _status.value = WatchSyncStatus.Idle
        }
    }

    /**
     * Shown to the user after "Sync failed ·", so every branch is a sentence and none is a
     * class name. Built from the exception type alone: §9.5 keeps payload data out of every sink.
     */
    private fun failureReason(e: Throwable): String = when (e) {
        is WatchSyncBusyException -> "The watch was busy with another sync"
        is WatchNotReadyException -> "The watch disconnected during the sync"
        is WatchCommandTimeoutException -> "The watch stopped responding"
        is NoBoundWatchException -> "No paired watch to save the data to"
        else -> "Something went wrong. Try again"
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
