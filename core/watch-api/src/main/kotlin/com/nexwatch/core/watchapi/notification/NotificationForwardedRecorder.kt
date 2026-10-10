package com.nexwatch.core.watchapi.notification

/**
 * One attempt to put a notification on the watch. Only notifications from apps the user allowed
 * are ever recorded, and only their title: §9.5 keeps message text out of every sink.
 */
data class ForwardingLogEntry(
    val atMs: Long,
    val packageName: String,
    val title: String,
    val outcome: ForwardingOutcome,
)

sealed interface ForwardingOutcome {
    data object Sent : ForwardingOutcome
    data class Skipped(val reason: ForwardingSkip) : ForwardingOutcome
    data class Failed(val reason: String) : ForwardingOutcome
}

enum class ForwardingSkip { DUPLICATE, THROTTLED, WATCH_DISCONNECTED, WATCH_BUSY }

/**
 * The forwarding log and the "forwarded today" count are kept by :core:data, but the writer
 * (NotificationForwarder) lives in :core:watch-fitcloud. Both sides may depend on
 * :core:watch-api, so the dependency is inverted through here, mirroring
 * NotificationForwardingSettingsProvider.
 */
interface NotificationForwardedRecorder {
    suspend fun record(entry: ForwardingLogEntry)
}
