package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blood_pressure",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class BloodPressureEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val systolic: Int,
    val diastolic: Int,
)
