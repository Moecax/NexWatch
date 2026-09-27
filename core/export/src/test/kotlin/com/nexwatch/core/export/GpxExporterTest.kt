package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val DEVICE_ID = "AA:BB"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

class GpxExporterTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `exports one trkpt per route point with correctly offset times`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val key = "workout:$DEVICE_ID:sport-1"
        val pk = pkFor(key)
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pk,
            RecordMeta(key, DEVICE_ID, 10_000L, 20_000L, 0, Origin.MONITOR, ingestedAt = 10_000L),
            "sport-1", 1, 10, 100f, 5f, null, null, null)))
        db.workoutDao().insertRoute(listOf(
            WorkoutRouteEntity(workoutId = pk, atMs = 10_000L, lat = 1.0, lon = 2.0, altitudeM = 5f),
            WorkoutRouteEntity(workoutId = pk, atMs = 15_000L, lat = 1.1, lon = 2.1, altitudeM = null),
        ))
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val out = ByteArrayOutputStream()

        val wrote = GpxExporter(repo).export(pk, out)

        assertTrue(wrote)
        val gpx = out.toString(Charsets.UTF_8.name())
        assertEquals(2, Regex("<trkpt").findAll(gpx).count())
        assertTrue(gpx.contains("""lat="1.0" lon="2.0""""))
        assertTrue(gpx.contains("<ele>5.0</ele>"))
        db.close()
    }

    @Test
    fun `a workout with no route points writes nothing rather than invalid GPX`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val key = "workout:$DEVICE_ID:sport-2"
        val pk = pkFor(key)
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pk,
            RecordMeta(key, DEVICE_ID, 10_000L, 20_000L, 0, Origin.MONITOR, ingestedAt = 10_000L),
            "sport-2", 1, 10, 0f, 5f, null, null, null))) // indoor workout, no GPS
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val out = ByteArrayOutputStream()

        val wrote = GpxExporter(repo).export(pk, out)

        assertFalse(wrote)
        assertEquals(0, out.size())
        db.close()
    }

    @Test
    fun `an unknown workout id writes nothing`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)

        assertFalse(GpxExporter(repo).export("does-not-exist", ByteArrayOutputStream()))
        db.close()
    }
}
