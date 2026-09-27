package com.nexwatch.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** Exposed publicly so :core:data's ExportRepository can stamp it into export manifests (§6.1). */
const val NEXWATCH_SCHEMA_VERSION = 1

@Database(
    entities = [
        HeartRateEntity::class, Spo2Entity::class, BloodPressureEntity::class,
        TemperatureEntity::class, StressEntity::class, StepsEntity::class,
        SleepSessionEntity::class, SleepStageEntity::class,
        WorkoutEntity::class, WorkoutRouteEntity::class, WorkoutHrEntity::class,
        DailySummaryEntity::class, DeviceEntity::class, DeviceEventEntity::class,
        RawIngestEntity::class, ChangeLogEntity::class,
        SyncCursorEntity::class, ExportHistoryEntity::class,
    ],
    version = NEXWATCH_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class NexWatchDatabase : RoomDatabase() {
    abstract fun healthSampleDao(): HealthSampleDao
    abstract fun stepsDao(): StepsDao
    abstract fun sleepDao(): SleepDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun deviceDao(): DeviceDao
    abstract fun rawIngestDao(): RawIngestDao
    abstract fun changeLogDao(): ChangeLogDao
    abstract fun exportHistoryDao(): ExportHistoryDao
}
