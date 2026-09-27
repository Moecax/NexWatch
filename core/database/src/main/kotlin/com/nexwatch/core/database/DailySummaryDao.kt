package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Upsert
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date = :date")
    suspend fun findByDate(deviceId: String, date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date = :date")
    fun observeByDate(deviceId: String, date: String): Flow<DailySummaryEntity?>

    @Query(
        "SELECT * FROM daily_summary WHERE device_id = :deviceId AND date BETWEEN :fromDate AND :toDate " +
            "ORDER BY date",
    )
    fun observeRange(deviceId: String, fromDate: String, toDate: String): Flow<List<DailySummaryEntity>>

    @Query(
        "INSERT OR IGNORE INTO daily_summary(device_id, date, steps, distance_m, energy_kcal, " +
            "resting_hr_bpm, avg_hr_bpm, max_hr_bpm, sleep_minutes, live_steps_total) " +
            "VALUES (:deviceId, :date, 0, 0, 0, NULL, NULL, NULL, NULL, NULL)",
    )
    suspend fun ensureRowExists(deviceId: String, date: String)

    @Query("UPDATE daily_summary SET live_steps_total = :value WHERE device_id = :deviceId AND date = :date")
    suspend fun updateLiveStepsTotal(deviceId: String, date: String, value: Int)

    @Query("SELECT * FROM daily_summary WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageAfter(afterId: Long, limit: Int): List<DailySummaryEntity>
}
