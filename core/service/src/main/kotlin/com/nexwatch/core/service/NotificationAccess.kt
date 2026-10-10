package com.nexwatch.core.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * §8.5: notification forwarding only works while the user keeps NexWatch's listener enabled in
 * system settings, and they can revoke it at any time. Screens check on every resume.
 */
object NotificationAccess {

    // By name: the listener lives in :core:watch-fitcloud, which this module can't see. The
    // manifest declares it under this name, so R8 keeps it.
    private const val LISTENER_CLASS = "com.nexwatch.core.watchfitcloud.NotificationForwarder"

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens NexWatch's own toggle where the system has one, otherwise the list of listeners. */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context.packageName, LISTENER_CLASS).flattenToString(),
            )
            // Some OEM builds don't implement the detail screen.
            try {
                context.startActivity(detail)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}
