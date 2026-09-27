package com.nexwatch.export

import com.nexwatch.core.export.AppVersionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    @Singleton
    abstract fun bindAppVersionProvider(impl: AppVersionProviderImpl): AppVersionProvider
}
