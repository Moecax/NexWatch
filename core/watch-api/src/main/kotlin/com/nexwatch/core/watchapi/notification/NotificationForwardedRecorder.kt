package com.nexwatch.core.watchapi.notification

/**
 * §8.4's diagnostics screen needs to know when a notification was last forwarded, but that
 * timestamp is persisted by DiagnosticsStore in :core:data, and the writer (NotificationForwarder)
 * lives in :core:watch-fitcloud. Both sides may depend on :core:watch-api, so the dependency is
 * inverted through here, mirroring NotificationForwardingSettingsProvider.
 */
interface NotificationForwardedRecorder {
    suspend fun recordForwarded(atMs: Long)
}
