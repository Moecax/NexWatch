package com.nexwatch.core.watchfitcloud.di

import com.nexwatch.core.watchapi.HealthDataDecoder
import com.nexwatch.core.watchfitcloud.FitCloudHealthDataDecoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DecoderModule {
    @Binds
    @Singleton
    abstract fun bindHealthDataDecoder(impl: FitCloudHealthDataDecoder): HealthDataDecoder
}
