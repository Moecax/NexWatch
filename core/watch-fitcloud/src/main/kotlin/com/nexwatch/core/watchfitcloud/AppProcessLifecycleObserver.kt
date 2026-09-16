package com.nexwatch.core.watchfitcloud

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.topstep.wearkit.base.ProcessLifecycleManager

/**
 * `FcSDK.Builder` requires a `ProcessLifecycleObserver`. The connector's retry cadence
 * depends on it — roughly every 5s in the foreground, backing off in the background (§2) —
 * so getting this wrong quietly costs battery rather than failing loudly.
 *
 * `ProcessLifecycleManager` implements the observer contract but leaves it to the host app
 * to say when the process is foregrounded, so AndroidX's ProcessLifecycleOwner drives it.
 */
internal class AppProcessLifecycleObserver :
    ProcessLifecycleManager(),
    DefaultLifecycleObserver {

    /** Must run on the main thread; callers do this from `Application.onCreate()`. */
    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = setForeground(true)

    override fun onStop(owner: LifecycleOwner) = setForeground(false)
}
