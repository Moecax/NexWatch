package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncCursorDao {

    @Query("SELECT * FROM sync_cursor WHERE provider_id = :providerId")
    suspend fun find(providerId: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursor")
    fun observeAll(): Flow<List<SyncCursorEntity>>

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursor WHERE provider_id = :providerId")
    suspend fun delete(providerId: String)

    /** §7.4: the change log is kept above this point; null means no provider holds a cursor. */
    @Query("SELECT MIN(last_seq) FROM sync_cursor")
    suspend fun minLastSeq(): Long?
}
