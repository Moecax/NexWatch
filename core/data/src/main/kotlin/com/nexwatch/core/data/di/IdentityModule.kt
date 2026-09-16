package com.nexwatch.core.data.di

import com.nexwatch.core.data.identity.StoredWatchUserIdProvider
import com.nexwatch.core.watchapi.WatchUserIdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Completes the §4.4 dependency inversion: :core:data owns the persisted userId,
 * :core:watch-fitcloud consumes it, and neither module can see the other.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityModule {
    @Binds
    @Singleton
    abstract fun bindWatchUserIdProvider(impl: StoredWatchUserIdProvider): WatchUserIdProvider
}
