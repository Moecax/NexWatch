package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    val at: Long,
    val uri: String,
    val format: String,
    val range: String,
    @ColumnInfo(name = "record_counts") val recordCountsJson: String,
)
