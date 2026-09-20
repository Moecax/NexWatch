package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface RawIngestDao {

    @Insert
    suspend fun insert(row: RawIngestEntity): Long

    @Query("SELECT * FROM raw_ingest WHERE data_type = :dataType AND processed_at IS NULL ORDER BY received_at")
    suspend fun findUnprocessed(dataType: String): List<RawIngestEntity>

    @Query("SELECT DISTINCT data_type FROM raw_ingest WHERE processed_at IS NULL")
    suspend fun findUnprocessedDataTypes(): List<String>

    @Update
    suspend fun update(row: RawIngestEntity)

    @Query("DELETE FROM raw_ingest WHERE processed_at IS NOT NULL AND received_at < :beforeMs")
    suspend fun pruneProcessedBefore(beforeMs: Long): Int
}
