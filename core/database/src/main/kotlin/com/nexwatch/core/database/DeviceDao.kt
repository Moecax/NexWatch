package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM device WHERE address = :address")
    suspend fun findByAddress(address: String): DeviceEntity?

    @Query("SELECT * FROM device ORDER BY bound_at DESC LIMIT 1")
    fun observeMostRecentlyBound(): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: DeviceEventEntity)

    @Query("SELECT * FROM device_event WHERE device_address = :address ORDER BY at DESC")
    fun observeEvents(address: String): Flow<List<DeviceEventEntity>>
}
