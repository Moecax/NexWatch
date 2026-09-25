package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val model: String?,
    @ColumnInfo(name = "firmware") val firmwareVersion: String?,
    @ColumnInfo(name = "sdk_version") val sdkVersion: String?,
    @ColumnInfo(name = "capabilities_json") val capabilitiesJson: String?,
    @ColumnInfo(name = "bound_at") val boundAtMs: Long,
)
