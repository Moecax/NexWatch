package com.nexwatch.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "heart_rate",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class HeartRateEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val bpm: Int,
)
