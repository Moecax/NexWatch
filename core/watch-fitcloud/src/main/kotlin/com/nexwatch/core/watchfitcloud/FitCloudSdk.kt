package com.nexwatch.core.watchfitcloud

import android.app.Application
import com.topstep.fitcloud.sdk.v2.FcSDK
import com.topstep.fitcloud.sdk.v2.features.FcBuiltInFeatures
import timber.log.Timber

/**
 * Owns the one [FcSDK] instance (§4.1).
 *
 * Deliberately not a Hilt binding: Hilt builds its SingletonComponent in `:app`, so any
 * SDK type in the graph would drag `com.topstep.**` onto `:app`'s compile classpath and
 * break the rule that SDK types never leave this module. An explicit init from
 * `Application.onCreate()` keeps the type inside these four walls, and covers every
 * process start path — the launcher, the notification listener, boot, a WorkManager job.
 */
object FitCloudSdk {

    @Volatile
    private var instance: FcSDK? = null

    /**
     * Call from `Application.onCreate()`, on the main thread. Repeat calls are ignored.
     *
     * [verboseLogging] plants a Timber tree so the SDK's own connect/scan/sync tracing
     * reaches logcat. Debug builds only — §9.5 keeps release builds quiet, and the SDK logs
     * payload bytes at this level.
     */
    fun initialize(application: Application, verboseLogging: Boolean) {
        if (instance != null) return
        synchronized(this) {
            if (instance != null) return
            if (verboseLogging && Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
            val lifecycle = AppProcessLifecycleObserver().apply { attach() }
            instance = FcSDK.Builder(application, lifecycle)
                .setBuiltInFeatures(
                    // §4.1. The SDK drives these itself once enabled; nothing in the app
                    // re-implements call handling, media keys or clock sync on top.
                    FcBuiltInFeatures(
                        autoSetTime = true,
                        telephonyControl = true,
                        mediaControl = true,
                        musicControl = true,
                    ),
                )
                .build()
        }
    }

    internal fun require(): FcSDK = checkNotNull(instance) {
        "FitCloudSdk.initialize() must run in Application.onCreate() before the watch client is used"
    }
}
