package com.nexwatch.core.data.notification

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

class NotificationForwardingPrefsTest {

    private val tempDir = createTempDirectory("notif-prefs").toFile()
    private val dispatcher = StandardTestDispatcher()
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir, "notification_forwarding.preferences_pb") },
    )
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }
    private val prefs = NotificationForwardingPrefs(dataStore, dispatchers)

    @Test
    fun `defaults to enabled with the built-in messaging allowlist`() = runTest(dispatcher) {
        val settings = prefs.settings.first()
        assertTrue(settings.enabled)
        assertTrue(settings.allowedPackages.contains("com.whatsapp"))
    }

    @Test
    fun `setEnabled persists`() = runTest(dispatcher) {
        prefs.setEnabled(false)
        assertEquals(false, prefs.settings.first().enabled)
    }

    @Test
    fun `setAllowedPackages replaces the set`() = runTest(dispatcher) {
        prefs.setAllowedPackages(setOf("com.example.app"))
        assertEquals(setOf("com.example.app"), prefs.settings.first().allowedPackages)
    }

    @Test
    fun `setPackageAllowed adds to and removes from the default allowlist`() = runTest(dispatcher) {
        prefs.setPackageAllowed("com.google.android.gm", allowed = true)
        prefs.setPackageAllowed("com.whatsapp", allowed = false)
        val allowed = prefs.settings.first().allowedPackages
        assertTrue("com.google.android.gm" in allowed)
        assertTrue("com.whatsapp" !in allowed)
        assertTrue("org.telegram.messenger" in allowed)
    }
}
