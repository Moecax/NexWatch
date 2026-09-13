package com.nexwatch.core.common.di

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.common.DefaultCoroutineDispatchers
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun bindCoroutineDispatchers(impl: DefaultCoroutineDispatchers): CoroutineDispatchers
}
