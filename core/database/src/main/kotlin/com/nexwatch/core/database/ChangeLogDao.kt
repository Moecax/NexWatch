package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ChangeLogDao {

    @Query("SELECT * FROM change_log WHERE seq > :afterSeq ORDER BY seq LIMIT :limit")
    suspend fun findAfter(afterSeq: Long, limit: Int): List<ChangeLogEntity>

    @Query("SELECT MAX(seq) FROM change_log")
    suspend fun latestSeq(): Long?

    @Query(
        "SELECT * FROM change_log WHERE seq > :afterSeq AND seq <= :upToSeq AND record_type IN (:recordTypes) " +
            "ORDER BY seq LIMIT :limit",
    )
    suspend fun findRange(afterSeq: Long, upToSeq: Long, recordTypes: List<String>, limit: Int): List<ChangeLogEntity>

    @Query("DELETE FROM change_log WHERE seq <= :seq")
    suspend fun deleteUpTo(seq: Long): Int

    @Query("DELETE FROM change_log")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM change_log")
    suspend fun count(): Int
}
