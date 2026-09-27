package com.nexwatch.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Minimal in-memory DataStore<Preferences> fake — no Android runtime needed for a JVM unit test. */
private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data get() = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class BackupPrefsTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `defaults to disabled with the default keep count`() = runTest(dispatcher) {
        val prefs = BackupPrefs(FakePreferencesDataStore(), dispatchers)
        val settings = prefs.settings.first()
        assertFalse(settings.enabled)
        assertEquals(5, settings.keepCount)
    }

    @Test
    fun `setFolder then setEnabled persists both`() = runTest(dispatcher) {
        val prefs = BackupPrefs(FakePreferencesDataStore(), dispatchers)
        prefs.setFolder("content://tree/abc")
        prefs.setEnabled(true)
        val settings = prefs.settings.first()
        assertEquals("content://tree/abc", settings.folderUri)
        assertEquals(true, settings.enabled)
    }
}
