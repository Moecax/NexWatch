package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Query("SELECT * FROM sleep_session WHERE device_id = :deviceId AND night_date = :nightDate AND deleted = 0")
    suspend fun findByNight(deviceId: String, nightDate: String): SleepSessionEntity?

    @Upsert
    suspend fun upsertSession(session: SleepSessionEntity)

    @Query("DELETE FROM sleep_stage WHERE session_id = :sessionId")
    suspend fun deleteStagesForSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStages(stages: List<SleepStageEntity>)

    @Transaction
    suspend fun replaceNight(session: SleepSessionEntity, stages: List<SleepStageEntity>) {
        upsertSession(session)
        deleteStagesForSession(session.pk)
        insertStages(stages)
    }

    @Query(
        "SELECT * FROM sleep_session WHERE device_id = :deviceId AND night_date BETWEEN :fromDate AND :toDate " +
            "AND deleted = 0 ORDER BY night_date",
    )
    fun observeNights(deviceId: String, fromDate: String, toDate: String): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_stage WHERE session_id = :sessionId ORDER BY start_time")
    fun observeStages(sessionId: String): Flow<List<SleepStageEntity>>
}
