package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ChangeLogDao {

    @Query("SELECT * FROM change_log WHERE seq > :afterSeq ORDER BY seq LIMIT :limit")
    suspend fun findAfter(afterSeq: Long, limit: Int): List<ChangeLogEntity>

    @Query("SELECT MAX(seq) FROM change_log")
    suspend fun latestSeq(): Long?
}
