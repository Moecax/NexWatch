package com.nexwatch.core.data.di

import com.nexwatch.core.data.notification.NotificationActivityLog
import com.nexwatch.core.data.notification.NotificationForwardingPrefs
import com.nexwatch.core.watchapi.notification.NotificationForwardedRecorder
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettingsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Completes the §8.5 dependency inversion: :core:data owns the persisted forwarding
 * settings, :core:watch-fitcloud consumes them, and neither module can see the other.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    @Singleton
    abstract fun bindNotificationForwardingSettingsProvider(
        impl: NotificationForwardingPrefs,
    ): NotificationForwardingSettingsProvider

    @Binds
    @Singleton
    abstract fun bindNotificationForwardedRecorder(
        impl: NotificationActivityLog,
    ): NotificationForwardedRecorder
}
