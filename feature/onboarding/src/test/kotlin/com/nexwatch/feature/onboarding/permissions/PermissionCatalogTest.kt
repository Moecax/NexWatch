package com.nexwatch.feature.onboarding.permissions

import android.Manifest
import android.os.Build
import com.nexwatch.feature.onboarding.PermissionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCatalogTest {

    @Test
    fun `bluetooth requires no runtime permission below API 31`() {
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissions(PermissionItem.BLUETOOTH, sdkInt = 30))
    }

    @Test
    fun `bluetooth requires scan and connect from API 31`() {
        val result = PermissionCatalog.runtimePermissions(PermissionItem.BLUETOOTH, sdkInt = Build.VERSION_CODES.S)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), result)
    }

    @Test
    fun `notifications require POST_NOTIFICATIONS from API 33`() {
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissions(PermissionItem.NOTIFICATIONS, sdkInt = 32))
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            PermissionCatalog.runtimePermissions(PermissionItem.NOTIFICATIONS, sdkInt = Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun `phone and contacts is four permissions at every supported SDK level`() {
        val result = PermissionCatalog.runtimePermissions(PermissionItem.PHONE_AND_CONTACTS, sdkInt = 26)
        assertEquals(
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS,
            ),
            result,
        )
    }

    @Test
    fun `notification access is a special access, not a runtime permission`() {
        assertTrue(PermissionCatalog.isSpecialAccess(PermissionItem.NOTIFICATION_ACCESS))
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissions(PermissionItem.NOTIFICATION_ACCESS, sdkInt = 34))
    }

    @Test
    fun `only location is optional`() {
        assertTrue(PermissionCatalog.isOptional(PermissionItem.LOCATION))
        assertFalse(PermissionCatalog.isOptional(PermissionItem.BLUETOOTH))
    }
}
