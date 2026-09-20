package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_stage",
    indices = [Index("session_id"), Index(value = ["session_id", "start_time"], unique = true)],
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val stage: SleepStageDb,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
)

enum class SleepStageDb { AWAKE, LIGHT, DEEP, REM }
