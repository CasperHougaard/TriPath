package com.tripath.di

import android.content.Context
import androidx.room.Room
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.UserProfileDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTrainingPlanDao(database: AppDatabase): TrainingPlanDao {
        return database.trainingPlanDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutLogDao(database: AppDatabase): WorkoutLogDao {
        return database.workoutLogDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao {
        return database.userProfileDao()
    }
}

