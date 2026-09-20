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
}
