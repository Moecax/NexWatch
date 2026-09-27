package com.nexwatch.core.export.di

import com.nexwatch.core.export.BackupDestination
import com.nexwatch.core.export.DocumentFileBackupDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportBindsModule {
    @Binds
    @Singleton
    abstract fun bindBackupDestination(impl: DocumentFileBackupDestination): BackupDestination
}
