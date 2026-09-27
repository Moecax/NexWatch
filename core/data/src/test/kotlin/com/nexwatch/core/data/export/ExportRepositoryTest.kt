package com.nexwatch.core.data.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.ExportHistoryEntry
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.model.RecordOrigin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

private const val DEVICE_ID = "AA:BB"

private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

class ExportRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `insertSteps then pageSteps round-trips every field`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val dedupeKey = "steps:$DEVICE_ID:1000"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(dedupeKey),
            RecordMeta(dedupeKey, DEVICE_ID, 1000L, 1300L, 0, Origin.MONITOR, ingestedAt = 1000L), 50, 40f, 2f)))

        val page = repo.pageSteps("", limit = 10)

        assertEquals(1, page.size)
        assertEquals(50, page[0].count)
        assertEquals(pkFor(dedupeKey), page[0].id)
        db.close()
    }

    @Test
    fun `pageWorkouts nests route and heart-rate series`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val dedupeKey = "workout:$DEVICE_ID:sport-1"
        val pk = pkFor(dedupeKey)
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pk,
            RecordMeta(dedupeKey, DEVICE_ID, 1000L, 2000L, 0, Origin.MONITOR, ingestedAt = 1000L),
            "sport-1", 1, 1000, 500f, 30f, 130, 160, 600)))
        db.workoutDao().insertRoute(listOf(WorkoutRouteEntity(workoutId = pk, atMs = 1000L, lat = 1.0, lon = 2.0, altitudeM = null)))

        val page = repo.pageWorkouts("", limit = 10)

        assertEquals(1, page.size)
        assertEquals(1, page[0].route.size)
        db.close()
    }

    @Test
    fun `insertWorkouts is idempotent on re-import`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val dedupeKey = "workout:$DEVICE_ID:sport-2"
        val record = HealthRecord.Workout(pkFor(dedupeKey), dedupeKey, DEVICE_ID, 1000L, 2000L,
            0, RecordOrigin.MONITOR, 1, false, 1000L, "sport-2", 1, 500f, 30f, null, null, null,
            emptyList(), emptyList())

        repo.insertWorkouts(listOf(record))
        repo.insertWorkouts(listOf(record)) // re-import same file twice

        assertEquals(1, repo.pageWorkouts("", limit = 10).size)
        db.close()
    }

    @Test
    fun `recordExport then recentExports round-trips counts`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)

        repo.recordExport(ExportHistoryEntry(atMs = 5_000L, uri = "content://a", format = "zip",
            range = "2026-01-01..2026-09-25", recordCounts = mapOf("steps" to 3)))

        val recent = repo.recentExports()
        assertEquals(1, recent.size)
        assertEquals(3, recent[0].recordCounts["steps"])
        db.close()
    }
}
