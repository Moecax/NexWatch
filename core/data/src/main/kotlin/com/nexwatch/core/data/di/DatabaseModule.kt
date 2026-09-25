package com.nexwatch.core.data.di

import android.content.Context
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.buildNexWatchDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexWatchDatabase = buildNexWatchDatabase(context)

    @Provides fun provideHealthSampleDao(db: NexWatchDatabase) = db.healthSampleDao()
    @Provides fun provideStepsDao(db: NexWatchDatabase) = db.stepsDao()
    @Provides fun provideSleepDao(db: NexWatchDatabase) = db.sleepDao()
    @Provides fun provideWorkoutDao(db: NexWatchDatabase) = db.workoutDao()
    @Provides fun provideDailySummaryDao(db: NexWatchDatabase) = db.dailySummaryDao()
    @Provides fun provideDeviceDao(db: NexWatchDatabase) = db.deviceDao()
    @Provides fun provideRawIngestDao(db: NexWatchDatabase) = db.rawIngestDao()
    @Provides fun provideChangeLogDao(db: NexWatchDatabase) = db.changeLogDao()
}
