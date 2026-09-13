package com.nexwatch.di

import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchfake.FakeWatchClient
import com.nexwatch.core.watchfake.WatchDebugController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Both interfaces bind to the same @Singleton FakeWatchClient instance, so the debug
 * screen's forceState() calls are visible through the WatchClient the rest of the app
 * observes. Phase 4 replaces this module's targets with FitCloudWatchClient for release
 * builds and keeps this fake binding for debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {
    @Binds
    @Singleton
    abstract fun bindWatchClient(impl: FakeWatchClient): WatchClient

    @Binds
    @Singleton
    abstract fun bindWatchDebugController(impl: FakeWatchClient): WatchDebugController
}
