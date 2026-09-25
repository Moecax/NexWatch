package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "last_seq") val lastSeq: Long,
    @ColumnInfo(name = "snapshot_state") val snapshotState: String?,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
)
