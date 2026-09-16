package com.nexwatch

import android.app.Application
import com.nexwatch.core.watchfitcloud.FitCloudSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // §4.1: every process start path goes through here. WatchConnectionService
        // (started from AppRoot once bound, BootReceiver, or CompanionPresenceService) now
        // owns the LOGIN reconnect that used to live here as WatchAutoConnect (§12 Phase 5).
        FitCloudSdk.initialize(this, verboseLogging = BuildConfig.DEBUG)
    }
}
