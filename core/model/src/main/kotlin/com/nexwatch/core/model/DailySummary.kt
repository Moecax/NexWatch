package com.nexwatch.core.model

/** Read model for :feature:today / :feature:health — never a Room entity directly. */
data class DailySummary(
    val date: String,
    val steps: Int,
    val distanceM: Int,
    val energyKcal: Int,
    val restingHrBpm: Int?,
    val avgHrBpm: Int?,
    val maxHrBpm: Int?,
    val sleepMinutes: Int?,
    val liveStepsTotal: Int?,
)

data class HeartRateSample(val atMs: Long, val bpm: Int)

data class SleepNight(
    val nightDate: String,
    val stages: List<SleepStageSpan>,
    val totalMinutes: Int,
    val score: Int,
)

data class WorkoutSummary(
    val id: String,
    val sportType: Int,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Float,
    val kcal: Float,
    val avgHrBpm: Int?,
    val maxHrBpm: Int?,
)
