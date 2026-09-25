package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_event",
    indices = [Index("device_address"), Index("at")],
)
data class DeviceEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    val type: String,
    val details: String?,
    val at: Long,
)
