package com.nexwatch.core.data.di

import com.nexwatch.core.data.syncengine.SyncScheduler
import com.nexwatch.core.data.syncengine.WorkManagerSyncScheduler
import com.nexwatch.core.syncapi.SyncProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    /** Declared so the set exists, empty, in a build that registers no provider. */
    @Multibinds
    abstract fun syncProviders(): Set<SyncProvider>

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
