package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_summary",
    indices = [Index(value = ["device_id", "date"], unique = true)],
)
data class DailySummaryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val date: String,
    val steps: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Int,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Int,
    @ColumnInfo(name = "resting_hr_bpm") val restingHrBpm: Int?,
    @ColumnInfo(name = "avg_hr_bpm") val avgHrBpm: Int?,
    @ColumnInfo(name = "max_hr_bpm") val maxHrBpm: Int?,
    @ColumnInfo(name = "sleep_minutes") val sleepMinutes: Int?,
    @ColumnInfo(name = "live_steps_total") val liveStepsTotal: Int?,
)
