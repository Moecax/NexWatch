package com.nexwatch.core.database

import androidx.room.useWriterConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
private const val NOW = 1_700_000_000_000L

private fun meta(dedupeKey: String, start: Long = NOW) = RecordMeta(
    dedupeKey = dedupeKey,
    deviceId = DEVICE_ID,
    startTime = start,
    endTime = start,
    zoneOffsetSec = 0,
    origin = Origin.MONITOR,
    ingestedAt = NOW,
)

private fun pkFor(dedupeKey: String): String = UUID.nameUUIDFromBytes(dedupeKey.toByteArray()).toString()

class ChangeLogTriggerTest {

    @Test
    fun `inserting a heart rate row appends an UPSERT change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "hr:$DEVICE_ID:$NOW:MONITOR"
        val pk = pkFor(dedupeKey)
        db.healthSampleDao().insertHeartRate(listOf(HeartRateEntity(pk, meta(dedupeKey), bpm = 62)))

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertEquals(1, entries.size)
        assertEquals("heart_rate", entries[0].recordType)
        assertEquals(pk, entries[0].recordId)
        assertEquals("UPSERT", entries[0].op)
        assertEquals(1, entries[0].version)
        db.close()
    }

    @Test
    fun `updating a row's version and deleted flag appends a DELETE change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "steps:$DEVICE_ID:$NOW"
        val pk = pkFor(dedupeKey)
        db.stepsDao().insertAll(
            listOf(StepsEntity(pk, meta(dedupeKey), count = 100, distanceM = 80f, energyKcal = 4f)),
        )
        // RoomDatabase.query(sql, args) is Android-only; the multiplatform driver path used here
        // (see TestDatabase.kt) exposes raw SQL only via useWriterConnection/usePrepared.
        db.useWriterConnection { transactor ->
            transactor.usePrepared("UPDATE steps SET version = 2, deleted = 1 WHERE pk = ?") { stmt ->
                stmt.bindText(1, pk)
                stmt.step()
            }
        }

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertEquals(2, entries.size) // insert's UPSERT + update's DELETE
        assertEquals("DELETE", entries.last().op)
        assertEquals(2, entries.last().version)
        db.close()
    }

    @Test
    fun `sleep_session upsert-by-night appends its own change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "sleep:$DEVICE_ID:2026-09-19"
        val pk = pkFor(dedupeKey)
        db.sleepDao().replaceNight(
            SleepSessionEntity(pk, meta(dedupeKey), nightDate = "2026-09-19", contentHash = "abc", score = 80, efficiency = 90),
            stages = emptyList(),
        )

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertTrue(entries.any { it.recordType == "sleep_session" && it.recordId == pk })
        db.close()
    }

    @Test
    fun `workout insert appends a change_log entry but its route and hr children do not`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "workout:$DEVICE_ID:sport-1"
        val pk = pkFor(dedupeKey)
        db.workoutDao().insertWorkouts(
            listOf(
                WorkoutEntity(
                    pk, meta(dedupeKey), sportId = "sport-1", sportType = 1, durationS = 1_800,
                    distanceM = 5_000f, energyKcal = 300f, avgHrBpm = 130, maxHrBpm = 160, steps = 6_000,
                ),
            ),
        )
        db.workoutDao().insertRoute(listOf(WorkoutRouteEntity(workoutId = pk, atMs = NOW, lat = 1.0, lon = 2.0, altitudeM = 10f)))
        db.workoutDao().insertHeartRateSeries(listOf(WorkoutHrEntity(workoutId = pk, atMs = NOW, bpm = 140)))

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertEquals(1, entries.size) // only the workout row itself has a trigger
        assertEquals("workout", entries[0].recordType)
        db.close()
    }

    @Test
    fun `daily_summary insert appends no change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        db.dailySummaryDao().upsert(
            DailySummaryEntity(
                deviceId = DEVICE_ID, date = "2026-09-19", steps = 5_000, distanceM = 4_000, energyKcal = 200,
                restingHrBpm = 55, avgHrBpm = 70, maxHrBpm = 120, sleepMinutes = 420, liveStepsTotal = null,
            ),
        )

        assertEquals(0, db.changeLogDao().findAfter(afterSeq = 0, limit = 10).size)
        db.close()
    }
}
