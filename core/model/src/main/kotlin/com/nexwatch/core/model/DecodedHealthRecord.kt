package com.nexwatch.core.model

/**
 * Output of §5.2's decode step — one variant per raw_ingest data_type. Pure Kotlin so it
 * can cross the :core:watch-api / :core:data boundary without either side seeing an SDK type.
 */
sealed interface DecodedHealthRecord {
    data class Step(
        val startMs: Long,
        val endMs: Long,
        val count: Int,
        val distanceM: Float,
        val kcal: Float,
    ) : DecodedHealthRecord

    data class HeartRate(val atMs: Long, val bpm: Int) : DecodedHealthRecord
    data class Spo2(val atMs: Long, val percent: Int) : DecodedHealthRecord
    data class BloodPressure(val atMs: Long, val systolic: Int, val diastolic: Int) : DecodedHealthRecord
    data class Temperature(val atMs: Long, val celsius: Float) : DecodedHealthRecord
    data class Stress(val atMs: Long, val level: Int) : DecodedHealthRecord

    /**
     * No nightDate here on purpose — deriving a calendar date from startMs needs a zone
     * offset, which the decoder (:core:watch-fitcloud) doesn't carry; HealthDataNormalizer
     * (:core:data) computes nightDate once it has RecordMeta's zoneOffsetSec to work with.
     */
    data class Sleep(
        val startMs: Long,
        val endMs: Long,
        val stages: List<SleepStageSpan>,
        val score: Int,
        val efficiency: Int,
    ) : DecodedHealthRecord

    data class Workout(
        val sportId: String,
        val sportType: Int,
        val startMs: Long,
        val endMs: Long,
        val distanceM: Float,
        val kcal: Float,
        val avgHrBpm: Int?,
        val maxHrBpm: Int?,
        val steps: Int?,
        val heartRateSeries: List<WorkoutHrPoint>,
    ) : DecodedHealthRecord

    /**
     * GPS is a separate sync data_type ("gps") from the workout itself ("sport") — the SDK
     * emits them as independent FcSyncData batches, joined only by sportId, so this is its
     * own record variant rather than a field on [Workout].
     */
    data class WorkoutRoute(val sportId: String, val points: List<WorkoutRoutePoint>) : DecodedHealthRecord

    data class TodayTotal(
        val atMs: Long,
        val steps: Int,
        val distanceM: Int,
        val kcal: Float,
        val heartRateBpm: Int?,
    ) : DecodedHealthRecord
}

/**
 * offsetSeconds, not atMs: FcGpsData carries no session-start timestamp of its own (only
 * sportId + items), so absolute time can only be computed once the matching WorkoutEntity's
 * startTime is known — that happens in HealthDataNormalizer (Task 15), not at decode time.
 */
data class WorkoutRoutePoint(val offsetSeconds: Int, val lat: Double, val lon: Double, val altitudeM: Float?)
data class WorkoutHrPoint(val atMs: Long, val bpm: Int)
