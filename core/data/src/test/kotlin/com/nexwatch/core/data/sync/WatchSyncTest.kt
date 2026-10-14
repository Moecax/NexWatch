package com.nexwatch.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.data.journal.JournalRepository
import com.nexwatch.core.data.normalize.DailySummaryAggregator
import com.nexwatch.core.data.normalize.HealthDataNormalizer
import com.nexwatch.core.data.syncengine.SyncScheduler
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

class WatchSyncTest {

    @Test
    fun `a watch that isn't connected leaves the last sync time alone`() = runTest {
        val db = inMemoryTestDatabase()
        val diagnostics = DiagnosticsStore(MemoryPreferences(), testDispatchers(this))
        val sync = watchSync(this, db, FakeWatchClient(), diagnostics)

        assertTrue(sync.run().isSuccess)

        assertNull(diagnostics.snapshot.first().lastSyncAt)
        assertEquals(WatchSyncStatus.Idle, sync.status.value)
        db.close()
    }

    @Test
    fun `a finished sync records its time and reports progress on the way`() = runTest {
        val db = inMemoryTestDatabase()
        val diagnostics = DiagnosticsStore(MemoryPreferences(), testDispatchers(this))
        val client = FakeWatchClient().apply { forceState(WatchState.Ready(battery = 80)) }
        val sync = watchSync(this, db, client, diagnostics)
        val seen = mutableListOf<WatchSyncStatus>()
        val watcher = backgroundScope.launch { sync.status.toList(seen) }

        assertTrue(sync.run().isSuccess)
        watcher.cancel()

        assertNotNull(diagnostics.snapshot.first().lastSyncAt)
        val running = seen.filterIsInstance<WatchSyncStatus.Running>()
        assertEquals(1f, running.mapNotNull { it.fraction }.last())
        assertTrue(running.any { it.dataType == "heart_rate" })
        assertEquals(WatchSyncStatus.Idle, sync.status.value)
        db.close()
    }
}

private suspend fun watchSync(scope: TestScope, db: NexWatchDatabase, client: FakeWatchClient, diagnostics: DiagnosticsStore): WatchSync {
    val dispatchers = testDispatchers(scope)
    val decoder = object : HealthDataDecoder {
        override fun decode(dataType: String, payloadJson: String) = emptyList<DecodedHealthRecord>()
    }
    val scheduler = object : SyncScheduler {
        override suspend fun enqueueEnabled() = Unit
        override fun enqueue(providerId: String) = Unit
        override fun enqueueAfter(providerId: String, delay: Duration) = Unit
        override fun schedulePeriodic(providerId: String) = Unit
        override fun cancelPeriodic(providerId: String) = Unit
        override fun cancel(providerId: String) = Unit
    }
    val coordinator = HealthSyncCoordinator(
        client,
        JournalRepository(db.rawIngestDao(), dispatchers),
        HealthDataNormalizer(db, decoder, dispatchers),
        DailySummaryAggregator(db, dispatchers),
        db.deviceDao(),
        db.rawIngestDao(),
        scheduler,
        WatchIdentityStore(MemoryPreferences(), dispatchers).apply { markBound("AA:BB:CC:DD:EE:FF") },
        DeviceRecorder(db.deviceDao(), dispatchers),
    )
    return WatchSync(coordinator, diagnostics, dispatchers)
}

private class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data get() = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
