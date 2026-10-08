package com.nexwatch.core.sync.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.model.RecordOrigin
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutHrPoint
import com.nexwatch.core.model.WorkoutRoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private const val T0 = 1_790_000_000_000L
private const val EAT = 3 * 3600

class HealthConnectRecordMapperTest {

    private fun step(count: Int, distanceM: Float = 0f, kcal: Float = 0f, endMs: Long = T0 + 300_000) = HealthRecord.Step(
        "step-id", "steps:d:$endMs", "d", T0, endMs, EAT, RecordOrigin.MONITOR, version = 2, deleted = false,
        ingestedAt = T0, count = count, distanceM = distanceM, energyKcal = kcal,
    )

    private fun heartRate(bpm: Int) = HealthRecord.HeartRate(
        "hr-id", "hr:d:$T0:MONITOR", "d", T0, T0, EAT, RecordOrigin.MONITOR, 1, false, T0, bpm,
    )

    @Test
    fun `a step interval becomes steps, distance and calories, each keyed by the record id`() {
        val records = HealthConnectRecordMapper.toRecords(step(count = 120, distanceM = 90f, kcal = 4.5f))

        val steps = records.filterIsInstance<StepsRecord>().single()
        assertEquals(120L, steps.count)
        assertEquals("step-id", steps.metadata.clientRecordId)
        assertEquals(2L, steps.metadata.clientRecordVersion)
        assertEquals(ZoneOffset.ofHours(3), steps.startZoneOffset)
        assertEquals(Instant.ofEpochMilli(T0), steps.startTime)
        assertEquals("step-id/distance", records.filterIsInstance<DistanceRecord>().single().metadata.clientRecordId)
        assertEquals(90.0, records.filterIsInstance<DistanceRecord>().single().distance.inMeters, 0.001)
        assertEquals("step-id/energy", records.filterIsInstance<ActiveCaloriesBurnedRecord>().single().metadata.clientRecordId)
    }

    @Test
    fun `zero distance and calories are not written, and an empty interval writes nothing`() {
        assertEquals(listOf(StepsRecord::class), HealthConnectRecordMapper.toRecords(step(count = 5)).map { it::class })
        assertTrue(HealthConnectRecordMapper.toRecords(step(count = 5, distanceM = 3f, endMs = T0)).isEmpty())
    }

    @Test
    fun `a heart rate sample becomes a one-sample series, and an impossible bpm is dropped`() {
        val record = HealthConnectRecordMapper.toRecords(heartRate(72)).single() as HeartRateRecord
        assertEquals(72L, record.samples.single().beatsPerMinute)
        assertEquals("hr-id", record.metadata.clientRecordId)

        assertTrue(HealthConnectRecordMapper.toRecords(heartRate(0)).isEmpty())
    }

    @Test
    fun `sleep stages are sorted, clipped to the session and de-overlapped`() {
        val session = HealthRecord.SleepSession(
            "sleep-id", "sleep:d:2026-10-07", "d", T0, T0 + 60 * 60_000, EAT, RecordOrigin.MONITOR, 3, false, T0,
            nightDate = "2026-10-07", contentHash = "h", score = 80, efficiency = 90,
            stages = listOf(
                SleepStageSpan(SleepStage.DEEP, T0 + 20 * 60_000, T0 + 40 * 60_000),
                SleepStageSpan(SleepStage.LIGHT, T0 - 10 * 60_000, T0 + 25 * 60_000),
                SleepStageSpan(SleepStage.REM, T0 + 40 * 60_000, T0 + 90 * 60_000),
            ),
        )

        val record = HealthConnectRecordMapper.toRecords(session).single() as SleepSessionRecord

        assertEquals(3L, record.metadata.clientRecordVersion)
        assertEquals(
            listOf(
                Triple(SleepSessionRecord.STAGE_TYPE_LIGHT, T0, T0 + 25 * 60_000),
                Triple(SleepSessionRecord.STAGE_TYPE_DEEP, T0 + 25 * 60_000, T0 + 40 * 60_000),
                Triple(SleepSessionRecord.STAGE_TYPE_REM, T0 + 40 * 60_000, T0 + 60 * 60_000),
            ),
            record.stages.map { Triple(it.stage, it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
        )
    }

    @Test
    fun `a workout becomes a session with its route plus one heart rate series`() {
        val workout = HealthRecord.Workout(
            "w-id", "workout:d:s1", "d", T0, T0 + 600_000, EAT, RecordOrigin.MONITOR, 1, false, T0,
            sportId = "s1", sportType = 7, durationS = 600, distanceM = 1500f, energyKcal = 90f,
            avgHrBpm = 120, maxHrBpm = 150, steps = 1800,
            route = listOf(
                WorkoutRoutePoint(0, -1.28, 36.82, 1650f),
                WorkoutRoutePoint(60, -1.281, 36.821, null),
                WorkoutRoutePoint(900, -1.29, 36.83, null), // after the session ends
            ),
            heartRateSeries = listOf(WorkoutHrPoint(T0 + 1_000, 110), WorkoutHrPoint(T0 + 61_000, 130)),
        )

        val records = HealthConnectRecordMapper.toRecords(workout)

        val session = records.filterIsInstance<ExerciseSessionRecord>().single()
        assertEquals("w-id", session.metadata.clientRecordId)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, session.exerciseType)
        val route = (session.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute
        assertEquals(2, route.route.size)
        val series = records.filterIsInstance<HeartRateRecord>().single()
        assertEquals("w-id/hr", series.metadata.clientRecordId)
        assertEquals(listOf(110L, 130L), series.samples.map { it.beatsPerMinute })

        assertEquals(
            listOf("w-id", "w-id/hr"),
            HealthConnectRecordMapper.refsFor(workout).map { it.clientRecordId },
        )
    }
}
