package com.nexwatch.di

import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchfake.FakeWatchClient
import com.nexwatch.core.watchfake.WatchDebugController
import com.nexwatch.core.watchfitcloud.FitCloudWatchClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debug builds can run against either implementation (§12 Phase 4), chosen by
 * [WatchImplPreference] at graph-creation time and flipped from the Watch debug screen.
 *
 * Both are injected as `Provider`s so only the selected one is ever constructed — the real
 * client resolves `FitCloudSdk` the moment it is built, and the fake has no business
 * existing in a session that's driving the actual watch.
 *
 * [WatchDebugController] stays on the fake whichever way the toggle is set: forcing a state
 * is only meaningful on an implementation that isn't being told what to do by a radio.
 */
@Module
@InstallIn(SingletonComponent::class)
object WatchModule {

    @Provides
    @Singleton
    fun provideWatchClient(
        preference: WatchImplPreference,
        fake: Provider<FakeWatchClient>,
        real: Provider<FitCloudWatchClient>,
    ): WatchClient = if (preference.useRealWatch) real.get() else fake.get()

    @Provides
    @Singleton
    fun provideWatchDebugController(fake: FakeWatchClient): WatchDebugController = fake
}
