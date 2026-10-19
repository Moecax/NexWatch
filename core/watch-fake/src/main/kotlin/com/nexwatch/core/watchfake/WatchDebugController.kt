package com.nexwatch.core.watchfake

import com.nexwatch.core.watchapi.WatchState

/**
 * Debug-only escape hatch (Phase 1 exit criteria: "a debug menu can force the app
 * through every WatchState"). Deliberately not part of WatchClient — production code
 * never calls this, only the debug screen does.
 */
interface WatchDebugController {
    fun forceState(state: WatchState)
    fun forceBattery(percent: Int, charging: Boolean = false)

    /** Consumed by the next sync, which never finishes: reproduces a stalled SDK stream. */
    var nextSyncHangs: Boolean

    /** Thrown by the next sync and then cleared. */
    var nextSyncError: Throwable?
}
