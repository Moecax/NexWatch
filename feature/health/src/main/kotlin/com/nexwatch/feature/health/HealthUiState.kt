package com.nexwatch.feature.health

import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.WorkoutSummary

data class HealthUiState(
    val heartRateBuckets: List<HeartRateSample> = emptyList(),
    val recentNights: List<SleepNight> = emptyList(),
    val recentWorkouts: List<WorkoutSummary> = emptyList(),
)
