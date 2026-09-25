package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorkouts(rows: List<WorkoutEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoute(rows: List<WorkoutRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRateSeries(rows: List<WorkoutHrEntity>)

    @Query(
        "SELECT * FROM workout WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs " +
            "AND deleted = 0 ORDER BY start_time DESC",
    )
    fun observeWorkouts(deviceId: String, fromMs: Long, toMs: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE device_id = :deviceId AND sport_id = :sportId AND deleted = 0")
    suspend fun findBySportId(deviceId: String, sportId: String): WorkoutEntity?

    @Query("SELECT * FROM workout_route WHERE workout_id = :workoutId ORDER BY start_time")
    fun observeRoute(workoutId: String): Flow<List<WorkoutRouteEntity>>

    @Query("SELECT * FROM workout_hr WHERE workout_id = :workoutId ORDER BY start_time")
    fun observeHeartRateSeries(workoutId: String): Flow<List<WorkoutHrEntity>>
}
