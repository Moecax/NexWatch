package com.nexwatch

import android.app.Application
import com.nexwatch.core.watchfitcloud.FitCloudSdk
import com.nexwatch.watch.WatchAutoConnect
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexWatchApplication : Application() {

    @Inject
    lateinit var watchAutoConnect: WatchAutoConnect

    override fun onCreate() {
        super.onCreate()
        // §4.1: every process start path goes through here — the launcher, the
        // notification listener, boot and WorkManager all begin with Application.onCreate().
        FitCloudSdk.initialize(this, verboseLogging = BuildConfig.DEBUG)
        watchAutoConnect.start()
    }
}
