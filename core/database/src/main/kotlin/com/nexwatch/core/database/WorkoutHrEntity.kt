package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_hr",
    indices = [Index(value = ["workout_id", "start_time"], unique = true)],
)
data class WorkoutHrEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "start_time") val atMs: Long,
    val bpm: Int,
)
