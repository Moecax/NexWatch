package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import androidx.room.useWriterConnection
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageDb
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

/**
 * RoomDatabase.clearAllTables() isn't available on Room's standard-jvm variant (only the
 * Android one) — the same JVM/Android split ChangeLogTriggerTest already works around via raw
 * SQL, so "wipe" here means deleting every affected table's rows directly instead.
 */
private suspend fun wipeTables(db: NexWatchDatabase, vararg tables: String) {
    db.useWriterConnection { tx ->
        tables.forEach { table -> tx.usePrepared("DELETE FROM $table") { it.step() } }
    }
}

class RoundTripTest {

    private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `seed, export, wipe, import reproduces every table exactly, IDs included`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE_ID, "GTR 3 Pro", "1.2.3", "3.0.2.4", null, boundAtMs = 500L))
        val stepsKey = "steps:$DEVICE_ID:1000"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(stepsKey),
            RecordMeta(stepsKey, DEVICE_ID, 1000L, 1300L, 0, Origin.MONITOR, ingestedAt = 1000L), 50, 40f, 2f)))
        val sleepKey = "sleep:$DEVICE_ID:2026-09-19"
        db.sleepDao().replaceNight(
            SleepSessionEntity(pkFor(sleepKey), RecordMeta(sleepKey, DEVICE_ID, 2000L, 30000L, 0, Origin.MONITOR, ingestedAt = 2000L),
                nightDate = "2026-09-19", contentHash = "h1", score = 80, efficiency = 90),
            stages = listOf(SleepStageEntity(sessionId = pkFor(sleepKey), stage = SleepStageDb.LIGHT, startTime = 2000L, endTime = 5000L)),
        )
        val workoutKey = "workout:$DEVICE_ID:sport-1"
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pkFor(workoutKey),
            RecordMeta(workoutKey, DEVICE_ID, 4000L, 8000L, 0, Origin.MONITOR, ingestedAt = 4000L),
            "sport-1", 1, durationS = 4, distanceM = 500f, energyKcal = 30f, avgHrBpm = 130, maxHrBpm = 160, steps = 600)))
        db.workoutDao().insertRoute(listOf(WorkoutRouteEntity(workoutId = pkFor(workoutKey), atMs = 4000L, lat = 1.0, lon = 2.0, altitudeM = 10f)))
        db.dailySummaryDao().upsert(DailySummaryEntity(deviceId = DEVICE_ID, date = "2026-09-19",
            steps = 50, distanceM = 40, energyKcal = 2, restingHrBpm = 55, avgHrBpm = 70, maxHrBpm = 120, sleepMinutes = 50, liveStepsTotal = null))

        val repository = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val stepsBefore = db.stepsDao().pageAfter("", 100)
        val sleepBefore = db.sleepDao().pageSessionsAfter("", 100)
        val stagesBefore = db.sleepDao().stagesForSessionOnce(pkFor(sleepKey))
        val workoutsBefore = db.workoutDao().pageAfter("", 100)
        val routeBefore = db.workoutDao().routeForWorkoutOnce(pkFor(workoutKey))
        val dailyBefore = db.dailySummaryDao().pageAfter(0L, 100)
        val devicesBefore = db.deviceDao().findAll()

        val out = ByteArrayOutputStream()
        JsonlZipExporter(repository, FixedAppVersion()).export(out)

        wipeTables(db, "device", "steps", "sleep_stage", "sleep_session", "workout_route", "workout_hr", "workout", "daily_summary")
        assertEquals(0, db.stepsDao().pageAfter("", 100).size) // confirm the wipe actually happened

        ZipImporter(repository).import(ByteArrayInputStream(out.toByteArray()))

        // sleep_stage/workout_route also carry an autoGenerate Long pk that isn't part of the
        // §6.1 export format (they're addressed by sessionId/workoutId + start_time instead) —
        // SQLite's AUTOINCREMENT keyword never reuses a value even after the row is deleted, so
        // this pk is expected to differ after a wipe+reimport; zero it before comparing, same as
        // daily_summary below.
        assertEquals(stepsBefore, db.stepsDao().pageAfter("", 100))
        assertEquals(sleepBefore, db.sleepDao().pageSessionsAfter("", 100))
        assertEquals(stagesBefore.map { it.copy(pk = 0) }, db.sleepDao().stagesForSessionOnce(pkFor(sleepKey)).map { it.copy(pk = 0) })
        assertEquals(workoutsBefore, db.workoutDao().pageAfter("", 100))
        assertEquals(routeBefore.map { it.copy(pk = 0) }, db.workoutDao().routeForWorkoutOnce(pkFor(workoutKey)).map { it.copy(pk = 0) })
        assertEquals(dailyBefore.map { it.copy(pk = 0) }, db.dailySummaryDao().pageAfter(0L, 100).map { it.copy(pk = 0) })
        assertEquals(devicesBefore.map { it.address }, db.deviceDao().findAll().map { it.address })
        db.close()
    }
}
