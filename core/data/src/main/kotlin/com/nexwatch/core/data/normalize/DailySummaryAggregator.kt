package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.NexWatchDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** §5.2 step 4 — recomputes daily_summary for exactly the dates the normalizer touched. */
class DailySummaryAggregator @Inject constructor(
    private val db: NexWatchDatabase,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun recompute(deviceId: String, dates: Set<String>): Unit = withContext(dispatchers.default) {
        val zone = ZoneId.systemDefault()
        for (date in dates) {
            val dayStart = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = LocalDate.parse(date).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val steps = db.stepsDao().observeDailyTotal(deviceId, dayStart, dayEnd).first()
            val hr = db.healthSampleDao().findDailyHrStats(deviceId, dayStart, dayEnd)
            val sleepMinutes = db.sleepDao().observeNights(deviceId, date, date).first()
                .sumOf { (it.meta.endTime - it.meta.startTime) / 60_000 }.toInt()
                .takeIf { it > 0 }
            val existing = db.dailySummaryDao().findByDate(deviceId, date)

            db.dailySummaryDao().upsert(
                DailySummaryEntity(
                    pk = existing?.pk ?: 0,
                    deviceId = deviceId,
                    date = date,
                    steps = steps?.steps ?: 0,
                    distanceM = steps?.distanceM?.toInt() ?: 0,
                    energyKcal = steps?.energyKcal?.toInt() ?: 0,
                    restingHrBpm = hr?.minBpm,
                    avgHrBpm = hr?.avgBpm?.toInt(),
                    maxHrBpm = hr?.maxBpm,
                    sleepMinutes = sleepMinutes,
                    liveStepsTotal = existing?.liveStepsTotal, // preserved — TodayTotal owns this field
                ),
            )
        }
    }
}
