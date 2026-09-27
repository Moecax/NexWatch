package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSampleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRate(rows: List<HeartRateEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpo2(rows: List<Spo2Entity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBloodPressure(rows: List<BloodPressureEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemperature(rows: List<TemperatureEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStress(rows: List<StressEntity>): List<Long>

    @Query("SELECT * FROM heart_rate WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageHeartRateAfter(afterId: String, limit: Int): List<HeartRateEntity>

    @Query("SELECT * FROM spo2 WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageSpo2After(afterId: String, limit: Int): List<Spo2Entity>

    @Query("SELECT * FROM blood_pressure WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageBloodPressureAfter(afterId: String, limit: Int): List<BloodPressureEntity>

    @Query("SELECT * FROM temperature WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageTemperatureAfter(afterId: String, limit: Int): List<TemperatureEntity>

    @Query("SELECT * FROM stress WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageStressAfter(afterId: String, limit: Int): List<StressEntity>

    @Query(
        "SELECT * FROM heart_rate WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs " +
            "AND deleted = 0 ORDER BY start_time",
    )
    fun observeHeartRateRange(deviceId: String, fromMs: Long, toMs: Long): Flow<List<HeartRateEntity>>

    @Query(
        "SELECT (start_time / :bucketMs) * :bucketMs AS bucket, AVG(bpm) AS avgBpm, MIN(bpm) AS minBpm, " +
            "MAX(bpm) AS maxBpm FROM heart_rate WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs " +
            "AND deleted = 0 GROUP BY bucket ORDER BY bucket",
    )
    fun observeHeartRateBuckets(
        deviceId: String,
        fromMs: Long,
        toMs: Long,
        bucketMs: Long,
    ): Flow<List<HeartRateBucket>>

    @Query(
        "SELECT AVG(bpm) AS avgBpm, MIN(bpm) AS minBpm, MAX(bpm) AS maxBpm FROM heart_rate " +
            "WHERE device_id = :deviceId AND start_time >= :dayStartMs AND start_time < :dayEndMs AND deleted = 0",
    )
    suspend fun findDailyHrStats(deviceId: String, dayStartMs: Long, dayEndMs: Long): HrDailyStats?
}

data class HeartRateBucket(val bucket: Long, val avgBpm: Double, val minBpm: Int, val maxBpm: Int)
data class HrDailyStats(val avgBpm: Double?, val minBpm: Int?, val maxBpm: Int?)
