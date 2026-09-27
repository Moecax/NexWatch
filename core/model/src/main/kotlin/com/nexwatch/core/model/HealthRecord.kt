package com.nexwatch.core.model

import kotlinx.serialization.Serializable

/** Mirrors :core:database's Origin (§5.3) without :core:model depending on :core:database. */
@Serializable
enum class RecordOrigin { MONITOR, MEASURE, LIVE }

/**
 * Full-fidelity canonical record — the §6.1 export/import wire shape, and the type §7.1
 * already names for SyncProvider's RecordChange (Phase 9 reuses this, not a new one).
 * One variant per RecordMeta-bearing table (§5.3); daily_summary is deliberately not here,
 * since it's derived and carries no RecordMeta (see DailySummaryRecord below).
 */
sealed interface HealthRecord {
    val id: String
    val dedupeKey: String
    val deviceId: String
    val startMs: Long
    val endMs: Long
    val zoneOffsetSec: Int
    val origin: RecordOrigin
    val version: Int
    val deleted: Boolean
    val ingestedAt: Long

    @Serializable
    data class Step(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val count: Int,
        val distanceM: Float,
        val energyKcal: Float,
    ) : HealthRecord

    @Serializable
    data class HeartRate(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val bpm: Int,
    ) : HealthRecord

    @Serializable
    data class Spo2(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val percent: Int,
    ) : HealthRecord

    @Serializable
    data class BloodPressure(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val systolic: Int,
        val diastolic: Int,
    ) : HealthRecord

    @Serializable
    data class Temperature(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val celsius: Float,
    ) : HealthRecord

    @Serializable
    data class Stress(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val level: Int,
    ) : HealthRecord

    @Serializable
    data class SleepSession(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val nightDate: String,
        val contentHash: String,
        val score: Int,
        val efficiency: Int,
        val stages: List<SleepStageSpan>,
    ) : HealthRecord

    @Serializable
    data class Workout(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val sportId: String,
        val sportType: Int,
        val distanceM: Float,
        val energyKcal: Float,
        val avgHrBpm: Int?,
        val maxHrBpm: Int?,
        val steps: Int?,
        val route: List<WorkoutRoutePoint>,
        val heartRateSeries: List<WorkoutHrPoint>,
    ) : HealthRecord
}

/** daily_summary has no RecordMeta (§5.3: "Derived. Rebuilt, never synced as source data"). */
@Serializable
data class DailySummaryRecord(
    val deviceId: String,
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

data class ExportHistoryEntry(
    val atMs: Long,
    val uri: String,
    val format: String,
    val range: String,
    val recordCounts: Map<String, Int>,
)
