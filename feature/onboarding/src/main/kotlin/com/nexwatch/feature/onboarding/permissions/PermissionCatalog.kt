package com.nexwatch.feature.onboarding.permissions

import android.Manifest
import android.os.Build
import com.nexwatch.feature.onboarding.PermissionItem

/**
 * Maps each B1 permission checklist item (docs/design-prompt.md Batch 1) to the actual
 * Android permission strings it needs, version-gated. `sdkInt` is a parameter rather than
 * reading `Build.VERSION.SDK_INT` directly so this stays a pure, unit-testable function.
 */
object PermissionCatalog {

    fun runtimePermissions(item: PermissionItem, sdkInt: Int): List<String> = when (item) {
        PermissionItem.BLUETOOTH ->
            if (sdkInt >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyList()
            }

        PermissionItem.NOTIFICATIONS ->
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }

        PermissionItem.NOTIFICATION_ACCESS -> emptyList()

        PermissionItem.PHONE_AND_CONTACTS -> listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )

        PermissionItem.LOCATION -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /** Notification access has no runtime dialog; it's granted through a Settings deep link. */
    fun isSpecialAccess(item: PermissionItem): Boolean = item == PermissionItem.NOTIFICATION_ACCESS

    /** Every checklist item is required except location, which the Permissions screen lets you skip. */
    fun isOptional(item: PermissionItem): Boolean = item == PermissionItem.LOCATION
}
