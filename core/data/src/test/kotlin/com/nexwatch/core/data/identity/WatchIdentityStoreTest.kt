package com.nexwatch.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchIdentityStoreTest {

    // Real DataStore falls back to java.io.File.renameTo() when running on a plain JVM
    // (no Robolectric to fake Build.VERSION.SDK_INT), and that call never overwrites an
    // existing destination on Windows — so a real file-backed DataStore can't survive a
    // second write in this test on this host. WatchIdentityStore only ever talks to the
    // DataStore<Preferences> interface (data/updateData), so an in-memory fake exercises
    // its logic just as faithfully, without touching disk at all.
    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state.asStateFlow()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private fun newStore(): WatchIdentityStore {
        val dataStore: DataStore<Preferences> = InMemoryPreferencesDataStore()
        val dispatchers = object : CoroutineDispatchers {
            override val io = UnconfinedTestDispatcher()
            override val default = UnconfinedTestDispatcher()
        }
        return WatchIdentityStore(dataStore, dispatchers)
    }

    @Test
    fun `initial identity has no userId and is unbound`() = runTest {
        val store = newStore()
        val identity = store.identity.first()
        assertNull(identity.userId.takeIf { it.isNotEmpty() })
        assertFalse(identity.isBound)
        assertNull(identity.boundAddress)
    }

    @Test
    fun `ensureUserId generates once and persists`() = runTest {
        val store = newStore()
        val first = store.ensureUserId()
        val second = store.ensureUserId()
        assertEquals(first, second)
        assertEquals(first, store.identity.first().userId)
    }

    @Test
    fun `markBound then markUnbound keeping address preserves it`() = runTest {
        val store = newStore()
        store.markBound("AA:BB:CC:DD:EE:FF")
        assertTrue(store.identity.first().isBound)
        assertEquals("AA:BB:CC:DD:EE:FF", store.identity.first().boundAddress)

        store.markUnbound(keepAddress = true)
        val identity = store.identity.first()
        assertFalse(identity.isBound)
        assertEquals("AA:BB:CC:DD:EE:FF", identity.boundAddress)
    }

    @Test
    fun `markUnbound without keeping address clears it`() = runTest {
        val store = newStore()
        store.markBound("AA:BB:CC:DD:EE:FF")
        store.markUnbound(keepAddress = false)
        assertNull(store.identity.first().boundAddress)
    }
}
