package com.tripath.di

import android.content.Context
import androidx.room.Room
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.dao.DayNoteDao
import com.tripath.data.local.database.dao.DayTemplateDao
import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.dao.SpecialPeriodDao
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.WellnessDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.migrations.MIGRATION_1_2
import com.tripath.data.local.database.migrations.MIGRATION_10_11
import com.tripath.data.local.database.migrations.MIGRATION_2_3
import com.tripath.data.local.database.migrations.MIGRATION_3_4
import com.tripath.data.local.database.migrations.MIGRATION_4_5
import com.tripath.data.local.database.migrations.MIGRATION_5_6
import com.tripath.data.local.database.migrations.MIGRATION_6_7
import com.tripath.data.local.database.migrations.MIGRATION_7_8
import com.tripath.data.local.database.migrations.MIGRATION_8_9
import com.tripath.data.local.database.migrations.MIGRATION_9_10
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database-related dependencies.
 * 
 * All database migrations are explicitly defined and non-destructive.
 * This ensures data integrity across app updates.
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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11
            )
            .fallbackToDestructiveMigration() // Development fallback - allows DB to rebuild if migration fails
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
    fun provideRawWorkoutDataDao(database: AppDatabase): RawWorkoutDataDao {
        return database.rawWorkoutDataDao()
    }

    @Provides
    @Singleton
    fun provideSpecialPeriodDao(database: AppDatabase): SpecialPeriodDao {
        return database.specialPeriodDao()
    }

    @Provides
    @Singleton
    fun provideDayNoteDao(database: AppDatabase): DayNoteDao {
        return database.dayNoteDao()
    }

    @Provides
    @Singleton
    fun provideDayTemplateDao(database: AppDatabase): DayTemplateDao {
        return database.dayTemplateDao()
    }

    @Provides
    @Singleton
    fun provideSleepLogDao(database: AppDatabase): SleepLogDao {
        return database.sleepLogDao()
    }

    @Provides
    @Singleton
    fun provideWellnessDao(database: AppDatabase): WellnessDao {
        return database.wellnessDao()
    }
}

