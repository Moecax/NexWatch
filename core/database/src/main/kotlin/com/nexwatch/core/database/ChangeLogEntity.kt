package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "change_log",
    indices = [Index("record_type"), Index("changed_at")],
)
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val op: String,
    val version: Int,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)
