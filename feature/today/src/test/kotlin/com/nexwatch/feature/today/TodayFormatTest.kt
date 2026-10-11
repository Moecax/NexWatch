package com.nexwatch.feature.today

import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

class TodayFormatTest {

    @Test
    fun `greeting follows the time of day`() {
        assertEquals("Good morning", greetingFor(7))
        assertEquals("Good afternoon", greetingFor(16))
        assertEquals("Good evening", greetingFor(21))
        assertEquals("Good evening", greetingFor(2))
    }

    @Test
    fun `relative time says minutes, then a clock time, then a day`() {
        val now = at("2026-10-10T13:24:00Z") // 16:24 local
        assertEquals("just now", relativeTime(now, now - 20_000, ZONE))
        assertEquals("4 min ago", relativeTime(now, now - 4 * 60_000, ZONE))
        assertEquals("at 08:12", relativeTime(now, at("2026-10-10T05:12:00Z"), ZONE))
        assertEquals("on Fri 9 Oct", relativeTime(now, at("2026-10-09T18:00:00Z"), ZONE))
    }

    @Test
    fun `sync phases are named from the journal's data types`() {
        assertEquals("Reading heart rate", syncPhaseLabel("heart_rate_resting"))
        assertEquals("Reading SpO2", syncPhaseLabel("oxygen"))
        assertEquals("Reading workouts", syncPhaseLabel("gps"))
        assertEquals("Reading watch data", syncPhaseLabel("hrv"))
        assertEquals("Starting sync", syncPhaseLabel(null))
    }

    @Test
    fun `hypnogram spans are fractions of the night, deeper sleep on lower lanes`() {
        val night = SleepNight(
            nightDate = "2026-10-09",
            stages = listOf(SleepStageSpan(SleepStage.LIGHT, 0, 100), SleepStageSpan(SleepStage.DEEP, 100, 400)),
            totalMinutes = 0,
            score = 80,
        )

        val spans = hypnogramSpans(night)

        assertEquals(listOf(2, 3), spans.map { it.lane })
        assertEquals(0.25f, spans[0].end, 0.0001f)
        assertEquals(1f, spans[1].end, 0.0001f)
    }

    @Test
    fun `workout line names the day, time, length and distance`() {
        val now = at("2026-10-10T13:24:00Z")
        val walk = WorkoutSummary("w", 1, at("2026-10-10T09:20:00Z"), at("2026-10-10T09:52:00Z"), 2_900f, 160f, 104, 138)
        assertEquals("Today at 12:20, 32 min, 2.9 km", workoutLine(walk, now, ZONE))
    }
}
