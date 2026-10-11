package com.nexwatch.feature.today

import com.nexwatch.core.designsystem.chart.HypnogramSpan
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

internal fun clock(ms: Long, zone: ZoneId): String = clockFormat.format(Instant.ofEpochMilli(ms).atZone(zone))

/** "just now", "4 min ago", "at 08:12", or "on Fri 9 Oct" once it's no longer today. */
internal fun relativeTime(nowMs: Long, atMs: Long, zone: ZoneId): String {
    val minutes = (nowMs - atMs) / 60_000
    val sameDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() == Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        sameDay -> "at ${clock(atMs, zone)}"
        else -> "on ${dayFormat.format(Instant.ofEpochMilli(atMs).atZone(zone))}"
    }
}

/** What a sync is reading, from the journal's data-type names (see FitCloudMappers). */
internal fun syncPhaseLabel(dataType: String?): String = when {
    dataType == null -> "Starting sync"
    dataType.startsWith("step") -> "Reading steps"
    dataType.startsWith("heart_rate") -> "Reading heart rate"
    dataType.startsWith("sleep") -> "Reading sleep"
    dataType.startsWith("oxygen") -> "Reading SpO2"
    dataType.startsWith("blood_pressure") -> "Reading blood pressure"
    dataType == "sport" || dataType == "gps" -> "Reading workouts"
    else -> "Reading watch data"
}

internal fun kilometres(metres: Int): String = "%.1f km".format(Locale.UK, metres / 1000f)

internal fun hoursMinutes(totalMinutes: Int): Pair<String, String> = (totalMinutes / 60).toString() to "%02d".format(totalMinutes % 60)

/** Lanes top to bottom: Awake, REM, Light, Deep, so deeper sleep sits lower. */
internal fun SleepStage.lane(): Int = when (this) {
    SleepStage.AWAKE -> 0
    SleepStage.REM -> 1
    SleepStage.LIGHT -> 2
    SleepStage.DEEP -> 3
}

internal fun hypnogramSpans(night: SleepNight): List<HypnogramSpan> {
    val start = night.stages.minOfOrNull { it.startMs } ?: return emptyList()
    val end = night.stages.maxOf { it.endMs }
    val length = (end - start).coerceAtLeast(1).toFloat()
    return night.stages.map { HypnogramSpan(it.stage.lane(), (it.startMs - start) / length, (it.endMs - start) / length) }
}
