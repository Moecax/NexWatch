package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "temperature",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class TemperatureEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val celsius: Float,
)
