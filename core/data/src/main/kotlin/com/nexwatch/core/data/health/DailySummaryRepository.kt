package com.nexwatch.core.data.health

import com.nexwatch.core.database.DailySummaryDao
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.model.DailySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class DailySummaryRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val dailySummaryDao: DailySummaryDao,
) {
    fun observeToday(): Flow<DailySummary?> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(null) else dailySummaryDao.observeByDate(device.address, today).map { it?.toModel() }
        }
    }

    fun observeRange(fromDate: String, toDate: String): Flow<List<DailySummary>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList()) else dailySummaryDao.observeRange(device.address, fromDate, toDate).map { list -> list.map { it.toModel() } }
        }
}

private fun DailySummaryEntity.toModel() = DailySummary(
    date = date, steps = steps, distanceM = distanceM, energyKcal = energyKcal,
    restingHrBpm = restingHrBpm, avgHrBpm = avgHrBpm, maxHrBpm = maxHrBpm,
    sleepMinutes = sleepMinutes, liveStepsTotal = liveStepsTotal,
)
