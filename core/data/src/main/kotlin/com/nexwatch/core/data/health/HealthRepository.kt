package com.nexwatch.core.data.health

import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
}
