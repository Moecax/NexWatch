package com.nexwatch.core.data.health

import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.database.DailyAggregate
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.model.BloodPressureReading
import com.nexwatch.core.model.DailyBloodPressure
import com.nexwatch.core.model.DailyHeartRate
import com.nexwatch.core.model.DailySpo2
import com.nexwatch.core.model.HeartRateRange
import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.Spo2Reading
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

class HealthRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val healthSampleDao: HealthSampleDao,
    private val sleepDao: SleepDao,
    private val workoutDao: WorkoutDao,
) {
    fun observeHeartRateBuckets(fromMs: Long, toMs: Long, bucketMs: Long): Flow<List<HeartRateSample>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList())
            else healthSampleDao.observeHeartRateBuckets(device.address, fromMs, toMs, bucketMs)
                .map { buckets -> buckets.map { HeartRateSample(atMs = it.bucket, bpm = it.avgBpm.toInt()) } }
        }

    fun observeSleepNights(fromDate: String, toDate: String): Flow<List<SleepNight>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList())
            else sleepDao.observeNights(device.address, fromDate, toDate).flatMapLatest { sessions ->
                if (sessions.isEmpty()) flowOf(emptyList())
                else combine(sessions.map { session -> sleepDao.observeStages(session.pk).map { session to it } }) { pairs ->
                    pairs.map { (session, stages) ->
                        SleepNight(
                            nightDate = session.nightDate,
                            stages = stages.map { SleepStageSpan(SleepStage.valueOf(it.stage.name), it.startTime, it.endTime) },
                            totalMinutes = stages.sumOf { (it.endTime - it.startTime) / 60_000 }.toInt(),
                            score = session.score,
                        )
                    }
                }
            }
        }

    fun observeWorkouts(fromMs: Long, toMs: Long): Flow<List<WorkoutSummary>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList())
            else workoutDao.observeWorkouts(device.address, fromMs, toMs).map { list ->
                list.map {
                    WorkoutSummary(
                        id = it.pk, sportType = it.sportType, startMs = it.meta.startTime, endMs = it.meta.endTime,
                        distanceM = it.distanceM, kcal = it.energyKcal, avgHrBpm = it.avgHrBpm, maxHrBpm = it.maxHrBpm,
                    )
                }
            }
        }

    /** Like [observeHeartRateBuckets], but keeps each bucket's min and max for a range band. */
    fun observeHeartRateRanges(fromMs: Long, toMs: Long, bucketMs: Long): Flow<List<HeartRateRange>> =
        forBoundDevice(emptyList()) { device ->
            healthSampleDao.observeHeartRateBuckets(device.address, fromMs, toMs, bucketMs).map { buckets ->
                buckets.map { HeartRateRange(it.bucket, it.avgBpm.roundToInt(), it.minBpm, it.maxBpm) }
            }
        }

    fun observeLatestHeartRate(): Flow<HeartRateSample?> = forBoundDevice(null) { device ->
        healthSampleDao.observeLatestHeartRate(device.address).map { it?.let { e -> HeartRateSample(e.meta.startTime, e.bpm) } }
    }

    fun observeLatestSpo2(): Flow<Spo2Reading?> = forBoundDevice(null) { device ->
        healthSampleDao.observeLatestSpo2(device.address).map { it?.let { e -> Spo2Reading(e.meta.startTime, e.percent) } }
    }

    fun observeLatestBloodPressure(): Flow<BloodPressureReading?> = forBoundDevice(null) { device ->
        healthSampleDao.observeLatestBloodPressure(device.address)
            .map { it?.let { e -> BloodPressureReading(e.meta.startTime, e.systolic, e.diastolic) } }
    }

    fun observeSpo2Low(fromMs: Long, toMs: Long): Flow<Int?> = forBoundDevice(null) { device ->
        healthSampleDao.observeSpo2Low(device.address, fromMs, toMs)
    }

    fun observeDailyHeartRate(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<DailyHeartRate>> =
        forBoundDevice(emptyList()) { device ->
            val (fromMs, toMs, offsetMs) = window(from, to, zone)
            healthSampleDao.observeHeartRateDaily(device.address, fromMs, toMs, offsetMs).map { days ->
                days.map { DailyHeartRate(it.date(), it.avgValue.roundToInt(), it.minValue, it.maxValue) }
            }
        }

    fun observeDailySpo2(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<DailySpo2>> =
        forBoundDevice(emptyList()) { device ->
            val (fromMs, toMs, offsetMs) = window(from, to, zone)
            healthSampleDao.observeSpo2Daily(device.address, fromMs, toMs, offsetMs).map { days ->
                days.map { DailySpo2(it.date(), it.avgValue.roundToInt(), it.minValue) }
            }
        }

    fun observeDailyBloodPressure(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<DailyBloodPressure>> =
        forBoundDevice(emptyList()) { device ->
            val (fromMs, toMs, offsetMs) = window(from, to, zone)
            healthSampleDao.observeBloodPressureDaily(device.address, fromMs, toMs, offsetMs).map { days ->
                days.map { DailyBloodPressure(LocalDate.ofEpochDay(it.day).toString(), it.systolic.roundToInt(), it.diastolic.roundToInt()) }
            }
        }

    private fun <T> forBoundDevice(empty: T, block: (DeviceEntity) -> Flow<T>): Flow<T> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device -> if (device == null) flowOf(empty) else block(device) }

    /**
     * The SQL shifts timestamps by one offset to find local days, so a DST change inside the
     * window moves an hour of samples across midnight. Accepted: the charts are daily.
     */
    private fun window(from: LocalDate, to: LocalDate, zone: ZoneId): Triple<Long, Long, Long> {
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val offsetMs = zone.rules.getOffset(Instant.now()).totalSeconds * 1000L
        return Triple(fromMs, toMs, offsetMs)
    }

    private fun DailyAggregate.date(): String = LocalDate.ofEpochDay(day).toString()
}
