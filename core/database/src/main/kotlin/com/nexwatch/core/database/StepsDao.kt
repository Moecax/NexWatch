package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<StepsEntity>): List<Long>

    /** §6.2 keyset pagination — never OFFSET. pk is a deterministic UUID string (§5.1), so "" sorts before every row. */
    @Query("SELECT * FROM steps WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageAfter(afterId: String, limit: Int): List<StepsEntity>

    @Query("SELECT * FROM steps WHERE pk IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<StepsEntity>

    @Query("SELECT COUNT(*) FROM steps")
    suspend fun count(): Int

    @Query("SELECT MAX(end_time) FROM steps WHERE device_id = :deviceId AND deleted = 0")
    suspend fun latestEndTime(deviceId: String): Long?

    @Query("SELECT MAX(end_time) FROM steps WHERE device_id = :deviceId AND end_time < :beforeMs AND deleted = 0")
    suspend fun previousEndTime(deviceId: String, beforeMs: Long): Long?

    /** Idempotent: only rows still stored as instants change. See [REPAIR_STEP_INTERVALS_SQL]. */
    @Query(REPAIR_STEP_INTERVALS_SQL)
    suspend fun repairInstantBuckets()

    @Query(
        "SELECT SUM(count) AS steps, SUM(distance_m) AS distanceM, SUM(energy_kcal) AS energyKcal " +
            "FROM steps WHERE device_id = :deviceId AND start_time >= :dayStartMs AND start_time < :dayEndMs " +
            "AND deleted = 0",
    )
    fun observeDailyTotal(deviceId: String, dayStartMs: Long, dayEndMs: Long): Flow<StepsDailyTotal?>

    @Query(
        "SELECT (start_time / :bucketMs) * :bucketMs AS bucket, SUM(count) AS steps " +
            "FROM steps WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs AND deleted = 0 " +
            "GROUP BY bucket ORDER BY bucket",
    )
    fun observeStepBuckets(deviceId: String, fromMs: Long, toMs: Long, bucketMs: Long): Flow<List<StepBucket>>
}

data class StepsDailyTotal(val steps: Int, val distanceM: Float, val energyKcal: Float)
data class StepBucket(val bucket: Long, val steps: Int)
