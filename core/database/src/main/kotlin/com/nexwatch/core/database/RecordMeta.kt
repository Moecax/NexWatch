package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** §5.3 — embedded in every health table. Not its own @Entity; it has no primary key of its own. */
data class RecordMeta(
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "zone_offset_s") val zoneOffsetSec: Int,
    @ColumnInfo(name = "origin") val origin: Origin,
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
    @ColumnInfo(name = "ingested_at") val ingestedAt: Long,
)
