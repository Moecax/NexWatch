package com.nexwatch.core.data.records

import com.nexwatch.core.database.BloodPressureEntity
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.Spo2Entity
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.StressEntity
import com.nexwatch.core.database.TemperatureEntity
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.model.RecordOrigin
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutHrPoint
import com.nexwatch.core.model.WorkoutRoutePoint

// Shared by export (§6) and sync (§7) so both hand out byte-for-byte the same HealthRecord for a row.

internal fun StepsEntity.toModel() = HealthRecord.Step(pk, meta.dedupeKey, meta.deviceId, meta.startTime, meta.endTime,
    meta.zoneOffsetSec, meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, count, distanceM, energyKcal)

internal fun HeartRateEntity.toModel() = HealthRecord.HeartRate(pk, meta.dedupeKey, meta.deviceId, meta.startTime,
    meta.endTime, meta.zoneOffsetSec, meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, bpm)

internal fun Spo2Entity.toModel() = HealthRecord.Spo2(pk, meta.dedupeKey, meta.deviceId, meta.startTime, meta.endTime,
    meta.zoneOffsetSec, meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, percent)

internal fun BloodPressureEntity.toModel() = HealthRecord.BloodPressure(pk, meta.dedupeKey, meta.deviceId,
    meta.startTime, meta.endTime, meta.zoneOffsetSec, meta.origin.toModel(), meta.version, meta.deleted,
    meta.ingestedAt, systolic, diastolic)

internal fun TemperatureEntity.toModel() = HealthRecord.Temperature(pk, meta.dedupeKey, meta.deviceId, meta.startTime,
    meta.endTime, meta.zoneOffsetSec, meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, celsius)

internal fun StressEntity.toModel() = HealthRecord.Stress(pk, meta.dedupeKey, meta.deviceId, meta.startTime,
    meta.endTime, meta.zoneOffsetSec, meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, level)

internal fun SleepSessionEntity.toModel(stages: List<SleepStageEntity>) = HealthRecord.SleepSession(pk,
    meta.dedupeKey, meta.deviceId, meta.startTime, meta.endTime, meta.zoneOffsetSec, meta.origin.toModel(),
    meta.version, meta.deleted, meta.ingestedAt, nightDate, contentHash, score, efficiency,
    stages.map { SleepStageSpan(SleepStage.valueOf(it.stage.name), it.startTime, it.endTime) })

internal suspend fun WorkoutEntity.toModelWithChildren(workoutDao: WorkoutDao): HealthRecord.Workout {
    val route = workoutDao.routeForWorkoutOnce(pk).map {
        WorkoutRoutePoint(((it.atMs - meta.startTime) / 1000).toInt(), it.lat, it.lon, it.altitudeM)
    }
    val hr = workoutDao.heartRateForWorkoutOnce(pk).map { WorkoutHrPoint(it.atMs, it.bpm) }
    return HealthRecord.Workout(pk, meta.dedupeKey, meta.deviceId, meta.startTime, meta.endTime, meta.zoneOffsetSec,
        meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, sportId, sportType, durationS, distanceM,
        energyKcal, avgHrBpm, maxHrBpm, steps, route, hr)
}

internal fun HealthRecord.toMeta() = RecordMeta(dedupeKey, deviceId, startMs, endMs, zoneOffsetSec,
    Origin.valueOf(origin.name), version, deleted, ingestedAt)

internal fun Origin.toModel() = RecordOrigin.valueOf(name)
