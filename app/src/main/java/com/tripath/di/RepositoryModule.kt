package com.tripath.di

import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.RecoveryRepositoryImpl
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.local.repository.TrainingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing repository dependencies.
 * Uses @Binds to map interface to implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTrainingRepository(
        trainingRepositoryImpl: TrainingRepositoryImpl
    ): TrainingRepository

    @Binds
    @Singleton
    abstract fun bindRecoveryRepository(
        recoveryRepositoryImpl: RecoveryRepositoryImpl
    ): RecoveryRepository
}

