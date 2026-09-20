package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_ingest",
    indices = [Index("data_type", "processed_at"), Index("received_at")],
)
data class RawIngestEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "sdk_version") val sdkVersion: String,
    @ColumnInfo(name = "data_type") val dataType: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "processed_at") val processedAt: Long? = null,
    val error: String? = null,
)
