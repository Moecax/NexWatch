package com.nexwatch.core.service

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint

/**
 * §8.3: on API 31+, the system watches for the associated device instead of the app
 * scanning for it. When the watch appears, make sure WatchConnectionService is running;
 * when it disappears, do nothing — the service already handles disconnection via
 * WatchState, and CDM's job is presence, not connection management.
 */
@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class CompanionPresenceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        WatchConnectionService.start(this)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        super.onDeviceDisappeared(associationInfo)
    }
}
