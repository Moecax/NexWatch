package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class WorkoutEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    @ColumnInfo(name = "sport_id") val sportId: String,
    @ColumnInfo(name = "sport_type") val sportType: Int,
    @ColumnInfo(name = "duration_s") val durationS: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Float,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Float,
    @ColumnInfo(name = "avg_hr_bpm") val avgHrBpm: Int?,
    @ColumnInfo(name = "max_hr_bpm") val maxHrBpm: Int?,
    val steps: Int?,
)
