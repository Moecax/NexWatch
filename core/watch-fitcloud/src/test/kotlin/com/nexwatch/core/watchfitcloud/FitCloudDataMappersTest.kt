package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.model.SleepStage
import com.topstep.fitcloud.sdk.v2.model.data.FcBloodPressureData
import com.topstep.fitcloud.sdk.v2.model.data.FcGpsData
import com.topstep.fitcloud.sdk.v2.model.data.FcGpsItem
import com.topstep.fitcloud.sdk.v2.model.data.FcHeartRateData
import com.topstep.fitcloud.sdk.v2.model.data.FcOxygenData
import com.topstep.fitcloud.sdk.v2.model.data.FcPressureData
import com.topstep.fitcloud.sdk.v2.model.data.FcSleepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem
import com.topstep.fitcloud.sdk.v2.model.data.FcSportData
import com.topstep.fitcloud.sdk.v2.model.data.FcSportHeartRateItem
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcTemperatureData
import com.topstep.fitcloud.sdk.v2.model.data.FcTodayTotalData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitCloudDataMappersTest {

    @Test
    fun `step mapping matches the real recon fixture`() {
        // docs/recon/fixtures/step_bucket.txt: t=1789472400000 step=14 distance=0.00915 calories=0.393
        val real = FcStepData(1_789_472_400_000L, 14, 0.00915f, 0.393f, 0)

        val decoded = real.toDecodedStep()

        assertEquals(1_789_472_400_000L, decoded.startMs)
        assertEquals(14, decoded.count)
        assertEquals(9.15f, decoded.distanceM, 0.01f)
        assertEquals(0.393f, decoded.kcal, 0.001f)
    }

    @Test
    fun `today-total mapping converts calorie from milli-kcal and drops a zero heart rate`() {
        val real = FcTodayTotalData(1_789_472_400_000L, 9, 9, 393, 0, 0, 0, 0, 0, 0, 0)

        val decoded = real.toDecodedTodayTotal()

        assertEquals(9, decoded.steps)
        assertEquals(9, decoded.distanceM)
        assertEquals(0.393f, decoded.kcal, 0.001f)
        assertNull(decoded.heartRateBpm)
    }

    @Test
    fun `heart rate mapping`() {
        val real = FcHeartRateData(1_789_472_400_000L, 72)
        val decoded = real.toDecodedHeartRate()
        assertEquals(1_789_472_400_000L, decoded.atMs)
        assertEquals(72, decoded.bpm)
    }

    @Test
    fun `spo2 mapping`() {
        val real = FcOxygenData(1_789_472_400_000L, 97)
        val decoded = real.toDecodedSpo2()
        assertEquals(97, decoded.percent)
    }

    @Test
    fun `blood pressure mapping`() {
        val real = FcBloodPressureData(1_789_472_400_000L, 118, 76)
        val decoded = real.toDecodedBloodPressure()
        assertEquals(118, decoded.systolic)
        assertEquals(76, decoded.diastolic)
    }

    @Test
    fun `temperature mapping prefers body over wrist`() {
        val real = FcTemperatureData(1_789_472_400_000L, 36.6f, 32.1f)
        val decoded = real.toDecodedTemperature()
        assertEquals(36.6f, decoded.celsius, 0.01f)
    }

    @Test
    fun `temperature mapping falls back to wrist when body is zero`() {
        val real = FcTemperatureData(1_789_472_400_000L, 0f, 32.1f)
        val decoded = real.toDecodedTemperature()
        assertEquals(32.1f, decoded.celsius, 0.01f)
    }

    @Test
    fun `stress mapping`() {
        val real = FcPressureData(1_789_472_400_000L, 45)
        val decoded = real.toDecodedStress()
        assertEquals(45, decoded.level)
    }

    @Test
    fun `sleep mapping converts SDK stage constants and spans`() {
        val items = listOf(
            FcSleepItem(
                FcSleepItem.STATUS_LIGHT,
                1_789_400_000_000L,
                1_789_403_600_000L,
            ),
            FcSleepItem(
                FcSleepItem.STATUS_DEEP,
                1_789_403_600_000L,
                1_789_407_200_000L,
            ),
        )
        val real = FcSleepData(1_789_400_000_000L, items, false, 80, 90)

        val decoded = real.toDecodedSleep()

        assertEquals(1_789_400_000_000L, decoded.startMs)
        assertEquals(1_789_407_200_000L, decoded.endMs)
        assertEquals(2, decoded.stages.size)
        assertEquals(SleepStage.LIGHT, decoded.stages[0].stage)
        assertEquals(SleepStage.DEEP, decoded.stages[1].stage)
        assertEquals(80, decoded.score)
        assertEquals(90, decoded.efficiency)
    }

    @Test
    fun `workout mapping computes avg and max heart rate from the embedded series`() {
        val hrItems = listOf(
            FcSportHeartRateItem(0, 120),
            FcSportHeartRateItem(60, 140),
            FcSportHeartRateItem(120, 160),
        )
        val real = FcSportData(
            1_789_400_000_000L, 1, 1_800, 5.0f, 6_000, 300f, 6_000,
            emptyList(), "sport-42", intArrayOf(), hrItems, null, null, null, null,
        )

        val decoded = real.toDecodedWorkout()

        assertEquals("sport-42", decoded.sportId)
        assertEquals(1, decoded.sportType)
        assertEquals(1_789_400_000_000L, decoded.startMs)
        assertEquals(1_789_401_800_000L, decoded.endMs) // start + duration(1800s)*1000
        // FcSportData.distance (5.0f) is km, matching FcStepData's convention; distanceMeters
        // (6_000, despite the name) does not hold the canonical metres value here.
        assertEquals(5_000f, decoded.distanceM, 0.1f)
        assertEquals(140, decoded.avgHrBpm)
        assertEquals(160, decoded.maxHrBpm)
        assertEquals(3, decoded.heartRateSeries.size)
        assertEquals(1_789_400_060_000L, decoded.heartRateSeries[1].atMs) // start + item.duration(60s)*1000
    }

    @Test
    fun `workout route mapping converts item durations to absolute timestamps`() {
        // FcGpsItem's real constructor order (confirmed via javap) is (duration, lng, lat,
        // altitude, satellites, isRestart) — lng before lat.
        val items = listOf(
            FcGpsItem(0, 12.34, 56.78, 10f, 8, false),
            FcGpsItem(30, 12.35, 56.79, 12f, 9, false),
        )
        val real = FcGpsData(1_789_400_000_000L, "sport-42", items)

        val decoded = real.toDecodedWorkoutRoute()

        assertEquals("sport-42", decoded.sportId)
        assertEquals(2, decoded.points.size)
        assertEquals(30, decoded.points[1].offsetSeconds)
        assertEquals(56.79, decoded.points[1].lat, 0.001)
        assertEquals(12.35, decoded.points[1].lon, 0.001)
    }
}
