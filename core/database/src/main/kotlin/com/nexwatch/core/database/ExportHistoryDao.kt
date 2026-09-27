package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExportHistoryDao {

    @Insert
    suspend fun insert(entry: ExportHistoryEntity): Long

    @Query("SELECT * FROM export_history ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ExportHistoryEntity>
}
