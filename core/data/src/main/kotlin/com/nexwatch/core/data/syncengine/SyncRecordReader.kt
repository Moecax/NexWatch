package com.nexwatch.core.data.syncengine

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.records.toModel
import com.nexwatch.core.data.records.toModelWithChildren
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.StepsDao
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.syncapi.RecordType
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** change_log.record_type holds the table name, because the triggers write it (§5.4). */
internal val RecordType.tableName: String
    get() = when (this) {
        RecordType.STEPS -> "steps"
        RecordType.HEART_RATE -> "heart_rate"
        RecordType.SPO2 -> "spo2"
        RecordType.BLOOD_PRESSURE -> "blood_pressure"
        RecordType.TEMPERATURE -> "temperature"
        RecordType.STRESS -> "stress"
        RecordType.SLEEP_SESSION -> "sleep_session"
        RecordType.WORKOUT -> "workout"
    }

internal fun recordTypeForTable(table: String): RecordType? = RecordType.entries.firstOrNull { it.tableName == table }

/** The SyncEngine's only window onto the health tables: keyset pages for snapshots, id lookups for tailing. */
class SyncRecordReader @Inject constructor(
    private val stepsDao: StepsDao,
    private val healthSampleDao: HealthSampleDao,
    private val sleepDao: SleepDao,
    private val workoutDao: WorkoutDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun count(type: RecordType): Int = withContext(dispatchers.io) {
        when (type) {
            RecordType.STEPS -> stepsDao.count()
            RecordType.HEART_RATE -> healthSampleDao.countHeartRate()
            RecordType.SPO2 -> healthSampleDao.countSpo2()
            RecordType.BLOOD_PRESSURE -> healthSampleDao.countBloodPressure()
            RecordType.TEMPERATURE -> healthSampleDao.countTemperature()
            RecordType.STRESS -> healthSampleDao.countStress()
            RecordType.SLEEP_SESSION -> sleepDao.countSessions()
            RecordType.WORKOUT -> workoutDao.count()
        }
    }

    suspend fun page(type: RecordType, afterId: String, limit: Int): List<HealthRecord> = withContext(dispatchers.io) {
        when (type) {
            RecordType.STEPS -> stepsDao.pageAfter(afterId, limit).map { it.toModel() }
            RecordType.HEART_RATE -> healthSampleDao.pageHeartRateAfter(afterId, limit).map { it.toModel() }
            RecordType.SPO2 -> healthSampleDao.pageSpo2After(afterId, limit).map { it.toModel() }
            RecordType.BLOOD_PRESSURE -> healthSampleDao.pageBloodPressureAfter(afterId, limit).map { it.toModel() }
            RecordType.TEMPERATURE -> healthSampleDao.pageTemperatureAfter(afterId, limit).map { it.toModel() }
            RecordType.STRESS -> healthSampleDao.pageStressAfter(afterId, limit).map { it.toModel() }
            RecordType.SLEEP_SESSION ->
                sleepDao.pageSessionsAfter(afterId, limit).map { it.toModel(sleepDao.stagesForSessionOnce(it.pk)) }
            RecordType.WORKOUT -> workoutDao.pageAfter(afterId, limit).map { it.toModelWithChildren(workoutDao) }
        }
    }

    suspend fun byIds(type: RecordType, ids: List<String>): List<HealthRecord> = withContext(dispatchers.io) {
        when (type) {
            RecordType.STEPS -> stepsDao.findByIds(ids).map { it.toModel() }
            RecordType.HEART_RATE -> healthSampleDao.findHeartRateByIds(ids).map { it.toModel() }
            RecordType.SPO2 -> healthSampleDao.findSpo2ByIds(ids).map { it.toModel() }
            RecordType.BLOOD_PRESSURE -> healthSampleDao.findBloodPressureByIds(ids).map { it.toModel() }
            RecordType.TEMPERATURE -> healthSampleDao.findTemperatureByIds(ids).map { it.toModel() }
            RecordType.STRESS -> healthSampleDao.findStressByIds(ids).map { it.toModel() }
            RecordType.SLEEP_SESSION ->
                sleepDao.findSessionsByIds(ids).map { it.toModel(sleepDao.stagesForSessionOnce(it.pk)) }
            RecordType.WORKOUT -> workoutDao.findByIds(ids).map { it.toModelWithChildren(workoutDao) }
        }
    }
}
