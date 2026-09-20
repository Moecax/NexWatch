package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_session",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class SleepSessionEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    @ColumnInfo(name = "night_date") val nightDate: String,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    val score: Int,
    val efficiency: Int,
)
