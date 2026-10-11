package com.nexwatch.feature.health

import com.nexwatch.core.model.DailyHeartRate
import com.nexwatch.core.model.DailySummary
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.watchapi.WatchCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
private val TODAY = LocalDate.of(2026, 10, 10)

private fun summary(date: String, steps: Int, live: Int? = null, resting: Int? = null) = DailySummary(
    date = date, steps = steps, distanceM = 0, energyKcal = 0, restingHrBpm = resting, avgHrBpm = null,
    maxHrBpm = null, sleepMinutes = null, liveStepsTotal = live,
)

private fun capabilities(stress: Boolean, temperature: Boolean) = WatchCapabilities(
    heartRate = true, spo2 = true, bloodPressure = true, temperature = temperature, stress = stress,
    sport = true, gps = false, advancedReminders = false, weather = true, contactsLimit = 10,
    doNotDisturb = true, heartRateAlert = true, timeFormat = true,
    sedentaryIntervalConfigurable = true, monitorIntervalConfigurable = true, alarmLimit = 5,
    firmwareVersion = "00000105",
)

private fun build(
    heartRate: List<DailyHeartRate> = emptyList(),
    summaries: List<DailySummary> = emptyList(),
    nights: List<SleepNight> = emptyList(),
    caps: WatchCapabilities? = null,
) = buildHealthState(
    HealthRange.WEEK, TODAY.minusDays(6), TODAY, heartRate, summaries, nights,
    emptyList(), emptyList(), emptyList(), caps, ZONE,
)

class HealthStateTest {

    @Test
    fun `every series lines up with the week's dates, null where a day has no data`() {
        val state = build(
            heartRate = listOf(DailyHeartRate("2026-10-08", 70, 55, 130)),
            summaries = listOf(summary("2026-10-08", 9_000, resting = 58), summary("2026-10-10", 0, live = 8_432)),
        )

        assertEquals(7, state.dates.size)
        assertEquals(LocalDate.of(2026, 10, 4), state.dates.first())
        assertEquals(DayHeart(55, 130, 58), state.heart[4])
        assertNull(state.heart[0])
        // Today's live total beats the bucketed steps that haven't synced yet.
        assertEquals(8_432, state.steps.last())
        assertNull(state.steps[0])
    }

    @Test
    fun `a night counts for the morning it ended`() {
        val bed = Instant.parse("2026-10-09T20:05:00Z").toEpochMilli() // 23:05 local on the 9th
        val night = SleepNight(
            nightDate = "2026-10-09",
            stages = listOf(
                SleepStageSpan(SleepStage.DEEP, bed, bed + 85 * 60_000),
                SleepStageSpan(SleepStage.LIGHT, bed + 85 * 60_000, bed + 432 * 60_000),
            ),
            totalMinutes = 432,
            score = 84,
        )

        val state = build(nights = listOf(night))

        assertEquals(DaySleep(deep = 85, rem = 0, light = 347, awake = 0), state.sleep.last())
        assertNull(state.sleep[5])
    }

    @Test
    fun `metrics the watch can't measure are named, and only when the watch said so`() {
        assertEquals(listOf("Stress", "skin temperature"), build(caps = capabilities(stress = false, temperature = false)).unsupported)
        assertEquals(emptyList<String>(), build(caps = null).unsupported)
    }

    @Test
    fun `KPIs average only the days that have data`() {
        val state = HealthUiState(
            heart = listOf(DayHeart(50, 120, 60), null, DayHeart(55, 140, 62)),
            steps = listOf(12_000, null, 8_000),
            sleep = listOf(DaySleep(80, 90, 200, 10), null),
        )

        assertEquals(61, state.averageResting())
        assertEquals(50 to 140, state.heartRange())
        assertEquals(10_000, state.averageSteps())
        assertEquals(1, state.daysAtGoal())
        assertEquals(380, state.averageSleepMinutes())
    }

    @Test
    fun `axis labels name every day of a week but only every tenth of a month`() {
        val week = (6 downTo 0).map { TODAY.minusDays(it.toLong()) }
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Today"), xLabels(week))

        val month = (29 downTo 0).map { TODAY.minusDays(it.toLong()) }
        val labels = xLabels(month)
        assertEquals("11 Sep", labels[0])
        assertEquals("21 Sep", labels[10])
        assertEquals("Today", labels[29])
        assertEquals(26, labels.count { it == null })
    }
}
