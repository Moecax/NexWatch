package com.nexwatch.core.data.storage

import android.content.Context
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.NEXWATCH_DATABASE_NAME
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.StepsDao
import com.nexwatch.core.database.WorkoutDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class StoredDataType { HEART_RATE, ACTIVITY, SPO2, SLEEP, BLOOD_PRESSURE, WORKOUTS, TEMPERATURE, STRESS }

data class StorageSummary(
    /** Only types with at least one record; a watch without a sensor shows no empty row. */
    val counts: Map<StoredDataType, Int>,
    val earliestMs: Long?,
    val databaseBytes: Long,
) {
    val totalRecords: Int get() = counts.values.sum()
}

/** What the Data tab's storage card shows: how much is stored, of what, and since when. */
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stepsDao: StepsDao,
    private val healthSampleDao: HealthSampleDao,
    private val sleepDao: SleepDao,
    private val workoutDao: WorkoutDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun summary(): StorageSummary = withContext(dispatchers.io) {
        val counts = linkedMapOf(
            StoredDataType.HEART_RATE to healthSampleDao.countHeartRate(),
            StoredDataType.ACTIVITY to stepsDao.count(),
            StoredDataType.SPO2 to healthSampleDao.countSpo2(),
            StoredDataType.SLEEP to sleepDao.countSessions(),
            StoredDataType.BLOOD_PRESSURE to healthSampleDao.countBloodPressure(),
            StoredDataType.WORKOUTS to workoutDao.count(),
            StoredDataType.TEMPERATURE to healthSampleDao.countTemperature(),
            StoredDataType.STRESS to healthSampleDao.countStress(),
        ).filterValues { it > 0 }
        val earliest = listOfNotNull(stepsDao.earliestStartTime(), healthSampleDao.earliestHeartRateTime()).minOrNull()
        StorageSummary(counts, earliest, databaseBytes())
    }

    /** The WAL file holds recent writes until a checkpoint, so it counts toward what's on disk. */
    private fun databaseBytes(): Long {
        val main = context.getDatabasePath(NEXWATCH_DATABASE_NAME)
        return listOf(main, java.io.File(main.path + "-wal")).filter { it.exists() }.sumOf { it.length() }
    }
}
