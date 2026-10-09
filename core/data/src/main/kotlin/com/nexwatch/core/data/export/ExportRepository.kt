package com.nexwatch.core.data.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.records.toMeta
import com.nexwatch.core.data.records.toModel
import com.nexwatch.core.data.records.toModelWithChildren
import com.nexwatch.core.database.BloodPressureEntity
import com.nexwatch.core.database.DailySummaryDao
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.ExportHistoryDao
import com.nexwatch.core.database.ExportHistoryEntity
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.NEXWATCH_SCHEMA_VERSION
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageDb
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.Spo2Entity
import com.nexwatch.core.database.StepsDao
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.StressEntity
import com.nexwatch.core.database.TemperatureEntity
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutHrEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.model.DailySummaryRecord
import com.nexwatch.core.model.Device
import com.nexwatch.core.model.ExportHistoryEntry
import com.nexwatch.core.model.HealthRecord
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * The only class outside :core:database that reads/writes Room for export/import (§6).
 * Every RecordMeta-bearing table is exposed only through keyset-paginated reads — there is
 * deliberately no "get all rows" method here, so a year of data can never be loaded at once.
 */
class ExportRepository @Inject constructor(
    private val stepsDao: StepsDao,
    private val healthSampleDao: HealthSampleDao,
    private val sleepDao: SleepDao,
    private val workoutDao: WorkoutDao,
    private val dailySummaryDao: DailySummaryDao,
    private val deviceDao: DeviceDao,
    private val exportHistoryDao: ExportHistoryDao,
    private val dispatchers: CoroutineDispatchers,
) {
    val schemaVersion: Int get() = NEXWATCH_SCHEMA_VERSION

    // ---- paged reads ----

    suspend fun pageSteps(afterId: String, limit: Int): List<HealthRecord.Step> =
        withContext(dispatchers.io) { stepsDao.pageAfter(afterId, limit).map { it.toModel() } }

    suspend fun pageHeartRate(afterId: String, limit: Int): List<HealthRecord.HeartRate> =
        withContext(dispatchers.io) { healthSampleDao.pageHeartRateAfter(afterId, limit).map { it.toModel() } }

    suspend fun pageSpo2(afterId: String, limit: Int): List<HealthRecord.Spo2> =
        withContext(dispatchers.io) { healthSampleDao.pageSpo2After(afterId, limit).map { it.toModel() } }

    suspend fun pageBloodPressure(afterId: String, limit: Int): List<HealthRecord.BloodPressure> =
        withContext(dispatchers.io) { healthSampleDao.pageBloodPressureAfter(afterId, limit).map { it.toModel() } }

    suspend fun pageTemperature(afterId: String, limit: Int): List<HealthRecord.Temperature> =
        withContext(dispatchers.io) { healthSampleDao.pageTemperatureAfter(afterId, limit).map { it.toModel() } }

    suspend fun pageStress(afterId: String, limit: Int): List<HealthRecord.Stress> =
        withContext(dispatchers.io) { healthSampleDao.pageStressAfter(afterId, limit).map { it.toModel() } }

    suspend fun pageSleepSessions(afterId: String, limit: Int): List<HealthRecord.SleepSession> =
        withContext(dispatchers.io) {
            sleepDao.pageSessionsAfter(afterId, limit).map { it.toModel(sleepDao.stagesForSessionOnce(it.pk)) }
        }

    suspend fun pageWorkouts(afterId: String, limit: Int): List<HealthRecord.Workout> =
        withContext(dispatchers.io) { workoutDao.pageAfter(afterId, limit).map { it.toModelWithChildren(workoutDao) } }

    suspend fun workoutById(id: String): HealthRecord.Workout? =
        withContext(dispatchers.io) { workoutDao.findByPk(id)?.toModelWithChildren(workoutDao) }

    suspend fun pageDailySummaries(afterId: Long, limit: Int): List<DailySummaryRecord> =
        withContext(dispatchers.io) {
            dailySummaryDao.pageAfter(afterId, limit).map {
                DailySummaryRecord(it.deviceId, it.date, it.steps, it.distanceM, it.energyKcal, it.restingHrBpm,
                    it.avgHrBpm, it.maxHrBpm, it.sleepMinutes, it.liveStepsTotal)
            }
        }

    /** Exposed so callers can advance a Long cursor without depending on :core:database's entity type. */
    suspend fun dailySummaryCursorAfter(afterId: Long, limit: Int): Long? =
        withContext(dispatchers.io) { dailySummaryDao.pageAfter(afterId, limit).lastOrNull()?.pk }

    suspend fun allDevices(): List<Device> = withContext(dispatchers.io) {
        deviceDao.findAll().map { Device(it.address, it.model, it.firmwareVersion, it.sdkVersion, it.boundAtMs) }
    }

    // ---- import writes ----

    suspend fun insertSteps(records: List<HealthRecord.Step>): Unit = withContext(dispatchers.io) {
        stepsDao.insertAll(records.map { StepsEntity(it.id, it.toMeta(), it.count, it.distanceM, it.energyKcal) })
    }

    /** Exports written before schema v2 carry step buckets as instants; see REPAIR_STEP_INTERVALS_SQL. */
    suspend fun repairStepIntervals(): Unit = withContext(dispatchers.io) { stepsDao.repairInstantBuckets() }

    suspend fun insertHeartRate(records: List<HealthRecord.HeartRate>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertHeartRate(records.map { HeartRateEntity(it.id, it.toMeta(), it.bpm) })
    }

    suspend fun insertSpo2(records: List<HealthRecord.Spo2>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertSpo2(records.map { Spo2Entity(it.id, it.toMeta(), it.percent) })
    }

    suspend fun insertBloodPressure(records: List<HealthRecord.BloodPressure>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertBloodPressure(records.map { BloodPressureEntity(it.id, it.toMeta(), it.systolic, it.diastolic) })
    }

    suspend fun insertTemperature(records: List<HealthRecord.Temperature>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertTemperature(records.map { TemperatureEntity(it.id, it.toMeta(), it.celsius) })
    }

    suspend fun insertStress(records: List<HealthRecord.Stress>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertStress(records.map { StressEntity(it.id, it.toMeta(), it.level) })
    }

    suspend fun insertSleepSessions(records: List<HealthRecord.SleepSession>): Unit = withContext(dispatchers.io) {
        records.forEach { record ->
            val session = SleepSessionEntity(record.id, record.toMeta(), record.nightDate, record.contentHash,
                record.score, record.efficiency)
            val stages = record.stages.map { SleepStageEntity(sessionId = record.id, stage = SleepStageDb.valueOf(it.stage.name),
                startTime = it.startMs, endTime = it.endMs) }
            sleepDao.replaceNight(session, stages)
        }
    }

    suspend fun insertWorkouts(records: List<HealthRecord.Workout>): Unit = withContext(dispatchers.io) {
        workoutDao.insertWorkouts(records.map {
            WorkoutEntity(it.id, it.toMeta(), it.sportId, it.sportType, it.durationS,
                it.distanceM, it.energyKcal, it.avgHrBpm, it.maxHrBpm, it.steps)
        })
        records.forEach { record ->
            if (record.route.isNotEmpty()) {
                workoutDao.insertRoute(record.route.map {
                    WorkoutRouteEntity(workoutId = record.id, atMs = record.startMs + it.offsetSeconds * 1000L,
                        lat = it.lat, lon = it.lon, altitudeM = it.altitudeM)
                })
            }
            if (record.heartRateSeries.isNotEmpty()) {
                workoutDao.insertHeartRateSeries(record.heartRateSeries.map {
                    WorkoutHrEntity(workoutId = record.id, atMs = it.atMs, bpm = it.bpm)
                })
            }
        }
    }

    suspend fun insertDailySummaries(records: List<DailySummaryRecord>): Unit = withContext(dispatchers.io) {
        records.forEach {
            dailySummaryDao.upsert(DailySummaryEntity(deviceId = it.deviceId, date = it.date, steps = it.steps,
                distanceM = it.distanceM, energyKcal = it.energyKcal, restingHrBpm = it.restingHrBpm,
                avgHrBpm = it.avgHrBpm, maxHrBpm = it.maxHrBpm, sleepMinutes = it.sleepMinutes,
                liveStepsTotal = it.liveStepsTotal))
        }
    }

    suspend fun upsertDevices(devices: List<Device>): Unit = withContext(dispatchers.io) {
        devices.forEach {
            deviceDao.upsert(DeviceEntity(it.address, it.model, it.firmwareVersion, it.sdkVersion,
                capabilitiesJson = null, boundAtMs = it.boundAtMs))
        }
    }

    // ---- history ----

    suspend fun recordExport(entry: ExportHistoryEntry): Unit = withContext(dispatchers.io) {
        exportHistoryDao.insert(ExportHistoryEntity(at = entry.atMs, uri = entry.uri, format = entry.format,
            range = entry.range, recordCountsJson = Json.encodeToString(entry.recordCounts)))
        Unit
    }

    suspend fun recentExports(limit: Int = 20): List<ExportHistoryEntry> = withContext(dispatchers.io) {
        exportHistoryDao.recent(limit).map {
            ExportHistoryEntry(it.at, it.uri, it.format, it.range, Json.decodeFromString(it.recordCountsJson))
        }
    }

}
