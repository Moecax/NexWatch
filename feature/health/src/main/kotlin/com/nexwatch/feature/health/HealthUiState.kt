package com.nexwatch.feature.health

import com.nexwatch.core.data.goals.DEFAULT_STEP_GOAL
import com.nexwatch.core.model.DailyBloodPressure
import com.nexwatch.core.model.DailySpo2
import com.nexwatch.core.model.WorkoutSummary
import java.time.LocalDate

enum class HealthRange(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
}

data class DayHeart(val minBpm: Int, val maxBpm: Int, val restingBpm: Int?)

data class DaySleep(val deep: Int, val rem: Int, val light: Int, val awake: Int) {
    val total: Int get() = deep + rem + light + awake
}

/**
 * Every per-day list lines up with [dates], oldest first, with null for a day without data,
 * so all tiles share one time axis.
 */
data class HealthUiState(
    val range: HealthRange = HealthRange.WEEK,
    val dates: List<LocalDate> = emptyList(),
    val heart: List<DayHeart?> = emptyList(),
    val steps: List<Int?> = emptyList(),
    val sleep: List<DaySleep?> = emptyList(),
    val spo2: List<DailySpo2?> = emptyList(),
    val bloodPressure: List<DailyBloodPressure?> = emptyList(),
    /** Newest first. */
    val workouts: List<WorkoutSummary> = emptyList(),
    val showSpo2: Boolean = true,
    val showBloodPressure: Boolean = true,
    /** Metrics the connected watch says it has no sensor for, named instead of shown empty. */
    val unsupported: List<String> = emptyList(),
    val stepGoal: Int = DEFAULT_STEP_GOAL,
)
