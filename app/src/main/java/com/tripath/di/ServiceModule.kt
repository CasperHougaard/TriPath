package com.tripath.di

import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing service dependencies.
 * Provides services that require explicit injection setup.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    // No providers currently defined
}


