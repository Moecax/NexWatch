package com.nexwatch.di

import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchfake.WatchDebugController
import com.nexwatch.core.watchfitcloud.FitCloudWatchClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release builds only ever talk to the real watch (§12 Phase 4) — there is no toggle and
 * `FakeWatchClient` is never constructed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {

    @Binds
    @Singleton
    abstract fun bindWatchClient(impl: FitCloudWatchClient): WatchClient

    companion object {
        /**
         * The Watch tab's debug screen lives in the main source set, so the binding has to
         * exist in every variant. In release it does nothing: forcing a state would lie to
         * a screen that is showing a real connection.
         */
        @Provides
        @Singleton
        fun provideWatchDebugController(): WatchDebugController = object : WatchDebugController {
            override fun forceState(state: WatchState) = Unit
            override fun forceBattery(percent: Int) = Unit
        }
    }
}
