package com.nexwatch.core.sync.healthconnect.di

import com.nexwatch.core.sync.healthconnect.HealthConnectSyncProvider
import com.nexwatch.core.syncapi.SyncProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {
    @Binds
    @IntoSet
    abstract fun bindHealthConnectProvider(impl: HealthConnectSyncProvider): SyncProvider
}
