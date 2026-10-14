package com.nexwatch.feature.onboarding.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexwatch.feature.onboarding.PermissionItem

/** Reads what Android has actually granted, for the checklist's resume re-check (docs spec §4). */
object PermissionRuntime {
    fun isGranted(context: Context, item: PermissionItem, sdkInt: Int): Boolean =
        if (PermissionCatalog.isSpecialAccess(item)) {
            isNotificationListenerEnabled(context)
        } else {
            PermissionCatalog.runtimePermissions(item, sdkInt).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    /** Where a permission Android has stopped asking about can still be turned on by hand. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun notificationListenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
