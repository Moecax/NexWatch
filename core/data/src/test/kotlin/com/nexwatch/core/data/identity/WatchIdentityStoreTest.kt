package com.nexwatch.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchIdentityStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): WatchIdentityStore {
        // tempFolder.newFile() pre-creates an empty destination file; on Windows,
        // DataStore's atomic rename-over-existing-file fails in that case, so hand it
        // a path in the temp dir instead and let DataStore create the file itself.
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { java.io.File(tempFolder.root, "test-${System.nanoTime()}.preferences_pb") },
        )
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
