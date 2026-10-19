package com.nexwatch.feature.health

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Every day on a week; on a month only every tenth, or 30 labels collide. */
internal fun xLabels(dates: List<LocalDate>): List<String?> = dates.mapIndexed { i, date ->
    when {
        i == dates.lastIndex -> "Today"
        dates.size <= 7 -> weekday.format(date)
        i % 10 == 0 -> dayMonth.format(date)
        else -> null
    }
}

internal fun HealthUiState.averageResting(): Int? = heart.mapNotNull { it?.restingBpm }.averageOrNull()

internal fun HealthUiState.heartRange(): Pair<Int, Int>? {
    val days = heart.filterNotNull()
    if (days.isEmpty()) return null
    return days.minOf { it.minBpm } to days.maxOf { it.maxBpm }
}

internal fun HealthUiState.averageSleepMinutes(): Int? = sleep.mapNotNull { it?.total }.averageOrNull()

internal fun HealthUiState.averageDeepMinutes(): Int? = sleep.mapNotNull { it?.deep }.averageOrNull()

internal fun HealthUiState.averageSteps(): Int? = steps.filterNotNull().averageOrNull()

internal fun HealthUiState.daysAtGoal(): Int = steps.count { (it ?: 0) >= stepGoal }

/** "10k", "7.5k": short enough to sit on the goal line without covering a bar. */
internal fun shortSteps(steps: Int): String =
    if (steps % 1_000 == 0) "${steps / 1_000}k" else "%.1fk".format(Locale.UK, steps / 1_000f)

internal fun HealthUiState.averageSpo2(): Int? = spo2.mapNotNull { it?.avgPercent }.averageOrNull()

internal fun HealthUiState.lowestSpo2(): Int? = spo2.mapNotNull { it?.minPercent }.minOrNull()

internal fun HealthUiState.workoutMinutes(): Int = workouts.sumOf { ((it.endMs - it.startMs) / 60_000).toInt() }

internal fun hoursAndMinutes(totalMinutes: Int): String = "${totalMinutes / 60} h ${"%02d".format(totalMinutes % 60)} m"

private fun List<Int>.averageOrNull(): Int? = if (isEmpty()) null else average().roundToInt()
