package com.nexwatch.core.data.syncengine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.useWriterConnection
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.syncapi.Op
import com.nexwatch.core.syncapi.PushOutcome
import com.nexwatch.core.syncapi.Readiness
import com.nexwatch.core.syncapi.RecordChange
import com.nexwatch.core.syncapi.RecordType
import com.nexwatch.core.syncapi.SyncConstraints
import com.nexwatch.core.syncapi.SyncProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val DEVICE = "AA:BB:CC:DD:EE:FF"
private const val T0 = 1_790_000_000_000L

/** §11's FakeSyncProvider: an idempotent store keyed by record id, plus a script of outcomes to return. */
private class FakeSyncProvider(
    override val id: String,
    override val supportedTypes: Set<RecordType> = setOf(RecordType.HEART_RATE, RecordType.STEPS, RecordType.SLEEP_SESSION),
) : SyncProvider {
    override val displayName = id
    override val constraints = SyncConstraints()

    var readiness: Readiness = Readiness.Ready
    val script = ArrayDeque<PushOutcome>()
    val batches = mutableListOf<List<RecordChange>>()
    val store = mutableMapOf<String, HealthRecord>()
    var onPush: suspend (List<RecordChange>) -> Unit = {}

    override suspend fun readiness() = readiness

    override suspend fun push(changes: List<RecordChange>): PushOutcome {
        onPush(changes)
        val outcome = script.removeFirstOrNull() ?: PushOutcome.Success
        if (outcome == PushOutcome.Success) {
            batches += changes
            changes.forEach { change ->
                if (change.op == Op.DELETE) store.remove(change.record.id) else store[change.record.id] = change.record
            }
        }
        return outcome
    }

    val deliveredIds: List<String> get() = batches.flatten().map { it.record.id }
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data get() = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class SyncEngineTest {

    private val db: NexWatchDatabase = inMemoryTestDatabase()
    private var nextAt = T0

    @After
    fun tearDown() = db.close()

    private fun TestScope.dispatchers() = object : CoroutineDispatchers {
        override val io = StandardTestDispatcher(testScheduler)
        override val default = io
    }

    private suspend fun TestScope.engineWith(vararg providers: FakeSyncProvider): Pair<SyncEngine, SyncPrefs> {
        val prefs = SyncPrefs(FakePreferencesDataStore(), dispatchers())
        providers.forEach { prefs.setEnabled(it.id, true) }
        val reader = SyncRecordReader(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(), dispatchers())
        return SyncEngine(providers.toSet(), prefs, db.syncCursorDao(), db.changeLogDao(), reader) to prefs
    }

    private fun meta(key: String, at: Long) = RecordMeta(key, DEVICE, at, at, 0, Origin.MONITOR, ingestedAt = at)

    private fun id(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

    private suspend fun insertHeartRate(count: Int): List<String> {
        val rows = List(count) {
            val at = nextAt++
            val key = "hr:$DEVICE:$at:MONITOR"
            HeartRateEntity(id(key), meta(key, at), bpm = 60 + it % 40)
        }
        db.healthSampleDao().insertHeartRate(rows)
        return rows.map { it.pk }
    }

    private suspend fun insertStep(): String {
        val at = nextAt++
        val key = "steps:$DEVICE:$at"
        db.stepsDao().insertAll(listOf(StepsEntity(id(key), meta(key, at), count = 10, distanceM = 7f, energyKcal = 0.4f)))
        return id(key)
    }

    private suspend fun exec(sql: String, arg: String) = db.useWriterConnection { transactor ->
        transactor.usePrepared(sql) { stmt ->
            stmt.bindText(1, arg)
            stmt.step()
        }
    }

    @Test
    fun `first run backfills every existing record, then tails new ones`() = runTest {
        val existing = insertHeartRate(1_200) + insertStep()
        val provider = FakeSyncProvider("a")
        val (engine, _) = engineWith(provider)

        assertEquals(SyncRunResult.CaughtUp, engine.run("a"))
        assertEquals(existing.toSet(), provider.store.keys)
        assertTrue(provider.batches.all { batch -> batch.all { it.seq == 0L } })
        val cursor = db.syncCursorDao().find("a")!!
        assertNull(cursor.snapshotState)
        assertEquals(db.changeLogDao().latestSeq(), cursor.lastSeq)

        val fresh = insertHeartRate(3)
        provider.batches.clear()
        assertEquals(SyncRunResult.CaughtUp, engine.run("a"))
        assertEquals(fresh, provider.deliveredIds)
        assertTrue(provider.batches.flatten().all { it.seq > 0 && it.op == Op.UPSERT })
        assertEquals(db.changeLogDao().latestSeq(), db.syncCursorDao().find("a")!!.lastSeq)
    }

    @Test
    fun `records written while the snapshot runs are not missed`() = runTest {
        insertHeartRate(1_100)
        val provider = FakeSyncProvider("a")
        val writtenDuringSnapshot = mutableListOf<String>()
        provider.onPush = { if (writtenDuringSnapshot.isEmpty()) writtenDuringSnapshot += insertHeartRate(5) + insertStep() }
        val (engine, _) = engineWith(provider)

        assertEquals(SyncRunResult.CaughtUp, engine.run("a"))

        assertTrue(provider.store.keys.containsAll(writtenDuringSnapshot))
        assertEquals(1_100 + writtenDuringSnapshot.size, provider.store.size)
    }

    @Test
    fun `a Retry keeps the cursor and the next run resumes the snapshot where it stopped`() = runTest {
        val all = insertHeartRate(1_200)
        val provider = FakeSyncProvider("a")
        provider.script += listOf(PushOutcome.Success, PushOutcome.Retry())
        val (engine, prefs) = engineWith(provider)

        assertEquals(SyncRunResult.Retry(null), engine.run("a"))
        assertEquals(SYNC_BATCH_SIZE, provider.store.size)
        val state = decodeSnapshotState(db.syncCursorDao().find("a")!!.snapshotState!!)
        assertEquals(SYNC_BATCH_SIZE, state.pushed)
        assertEquals(1_200, state.total)
        assertTrue("a" in prefs.enabledProviderIds.first())

        assertEquals(SyncRunResult.CaughtUp, engine.run("a"))
        assertEquals(all.toSet(), provider.store.keys)
        // Only the page that failed is sent again; the one that succeeded is not repeated.
        assertEquals(all.size, provider.deliveredIds.size)
    }

    @Test
    fun `a Retry while tailing redelivers the same batch, and the result has no duplicates`() = runTest {
        val provider = FakeSyncProvider("a")
        val (engine, _) = engineWith(provider)
        engine.run("a")
        val cursorBefore = db.syncCursorDao().find("a")!!.lastSeq

        val fresh = insertHeartRate(4)
        provider.script += PushOutcome.Retry()
        assertEquals(SyncRunResult.Retry(null), engine.run("a"))
        assertEquals(cursorBefore, db.syncCursorDao().find("a")!!.lastSeq)

        assertEquals(SyncRunResult.CaughtUp, engine.run("a"))
        assertEquals(fresh.toSet(), provider.store.keys)

        // Forcing a full re-send delivers everything again; the store still holds each record once.
        db.syncCursorDao().delete("a")
        assertEquals(SyncRunResult.CaughtUp, engine.run("a"))
        assertEquals(fresh.toSet(), provider.store.keys)
        assertEquals(fresh.size * 2, provider.deliveredIds.size)
    }

    @Test
    fun `a Fatal stops only that provider and keeps its cursor`() = runTest {
        insertHeartRate(10)
        val broken = FakeSyncProvider("broken").apply { script += PushOutcome.Fatal("token revoked") }
        val healthy = FakeSyncProvider("healthy")
        val (engine, prefs) = engineWith(broken, healthy)

        assertEquals(SyncRunResult.Fatal("token revoked"), engine.run("broken"))
        assertEquals(SyncRunResult.CaughtUp, engine.run("healthy"))

        assertEquals(setOf("healthy"), prefs.enabledProviderIds.first())
        assertEquals("token revoked", db.syncCursorDao().find("broken")!!.lastError)
        assertEquals(SyncRunResult.NotEnabled, engine.run("broken"))
        assertEquals(10, healthy.store.size)
        assertTrue(broken.store.isEmpty())

        prefs.setEnabled("broken", true)
        assertEquals(SyncRunResult.CaughtUp, engine.run("broken"))
        assertEquals(10, broken.store.size)
    }

    @Test
    fun `a provider that isn't ready gets nothing and isn't disabled`() = runTest {
        insertHeartRate(3)
        val provider = FakeSyncProvider("a").apply { readiness = Readiness.NeedsPermission(setOf("WRITE_HEART_RATE")) }
        val (engine, prefs) = engineWith(provider)

        assertTrue(engine.run("a") is SyncRunResult.NotReady)
        assertTrue(provider.batches.isEmpty())
        assertTrue("a" in prefs.enabledProviderIds.first())
    }

    @Test
    fun `only supported types are pushed, and unsupported ones don't hold the cursor back`() = runTest {
        val provider = FakeSyncProvider("a", supportedTypes = setOf(RecordType.STEPS))
        val (engine, _) = engineWith(provider)
        engine.run("a")

        insertHeartRate(5)
        val step = insertStep()
        insertHeartRate(5)
        provider.batches.clear()
        engine.run("a")

        assertEquals(listOf(step), provider.deliveredIds)
        assertEquals(db.changeLogDao().latestSeq(), db.syncCursorDao().find("a")!!.lastSeq)
    }

    @Test
    fun `a tombstone is pushed as DELETE at its current version, once per batch`() = runTest {
        val step = insertStep()
        val provider = FakeSyncProvider("a")
        val (engine, _) = engineWith(provider)
        engine.run("a")
        provider.batches.clear()

        exec("UPDATE steps SET version = 2 WHERE pk = ?", step)
        exec("UPDATE steps SET version = 3, deleted = 1 WHERE pk = ?", step)
        engine.run("a")

        val delivered = provider.batches.flatten()
        assertEquals(1, delivered.size)
        assertEquals(Op.DELETE, delivered.single().op)
        assertEquals(3, delivered.single().record.version)
        assertFalse(step in provider.store)
    }

    @Test
    fun `a corrected sleep night is pushed again with its new version`() = runTest {
        val key = "sleep:$DEVICE:2026-10-07"
        val session = SleepSessionEntity(id(key), meta(key, T0), "2026-10-07", contentHash = "v1", score = 80, efficiency = 90)
        db.sleepDao().replaceNight(session, emptyList())
        val provider = FakeSyncProvider("a")
        val (engine, _) = engineWith(provider)
        engine.run("a")

        db.sleepDao().replaceNight(session.copy(meta = session.meta.copy(version = 2), contentHash = "v2"), emptyList())
        engine.run("a")

        val night = provider.store.getValue(id(key)) as HealthRecord.SleepSession
        assertEquals(2, night.version)
        assertEquals("v2", night.contentHash)
    }

    @Test
    fun `compaction keeps the log above the lowest cursor and truncates it when no provider has one`() = runTest {
        insertHeartRate(3)
        val provider = FakeSyncProvider("a")
        val (engine, _) = engineWith(provider)
        engine.run("a")
        val cursor = db.syncCursorDao().find("a")!!.lastSeq
        insertHeartRate(2)
        val compactor = ChangeLogCompactor(db.changeLogDao(), db.syncCursorDao(), dispatchers())

        compactor.compact()
        assertEquals(2, db.changeLogDao().count())
        assertTrue(db.changeLogDao().findAfter(0, 10).all { it.seq > cursor })

        db.syncCursorDao().delete("a")
        compactor.compact()
        assertEquals(0, db.changeLogDao().count())

        // Seqs are AUTOINCREMENT, so a fresh provider's snapshot head never collides with a deleted seq.
        insertHeartRate(1)
        assertNotNull(db.changeLogDao().latestSeq())
        assertTrue(db.changeLogDao().latestSeq()!! > cursor)
    }
}
