package com.nexwatch.core.watchapi

/**
 * Messages the watch initiates (§8.6). Delivered on WatchClient.events; the app never
 * polls for these.
 */
sealed interface WatchEvent {
    data object FindPhoneRequested : WatchEvent
    data object CameraOpenRequested : WatchEvent
    data object CameraCloseRequested : WatchEvent
    data object HangUpRequested : WatchEvent
}
