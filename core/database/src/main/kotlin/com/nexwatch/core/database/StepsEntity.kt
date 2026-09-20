package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class StepsEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val count: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Float,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Float,
)
