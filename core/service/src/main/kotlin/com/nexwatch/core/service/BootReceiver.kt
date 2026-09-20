package com.nexwatch.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexwatch.core.data.identity.WatchIdentityStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §8.4: starts the service after a reboot or an app update, but only if a watch is
 * actually bound — an unbound install has nothing to reconnect to.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var identityStore: WatchIdentityStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (identityStore.identity.first().isBound) {
                    WatchConnectionService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
