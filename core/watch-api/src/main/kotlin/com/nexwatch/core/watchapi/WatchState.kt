package com.nexwatch.core.watchapi

import java.time.Instant

/**
 * Mirrors FcConnectorState (implementation-plan.md §4.2/§2): Unbound and BluetoothOff
 * are app-level states the SDK doesn't model directly, Waiting/Connecting/Ready/AuthFailed
 * map onto PRE_CONNECTING, CONNECTING+PRE_CONNECTED, CONNECTED and FcAuthException.
 */
sealed interface WatchState {
    data object Unbound : WatchState
    data object BluetoothOff : WatchState
    data class Waiting(val nextRetryAt: Instant?) : WatchState
    data object Connecting : WatchState
    data class Ready(val battery: Int?) : WatchState
    data class AuthFailed(val reason: String) : WatchState
}

/**
 * Thrown by every WatchClient command when [WatchState] isn't [WatchState.Ready], so
 * callers fail fast instead of hanging on a disconnected watch (§4.2).
 */
class WatchNotReadyException(val state: WatchState) :
    IllegalStateException("Watch not ready: $state")
