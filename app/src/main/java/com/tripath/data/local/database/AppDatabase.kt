package com.tripath.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tripath.data.local.database.converters.Converters
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.UserProfileDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog

/**
 * Main Room database for the TriPath application.
 * Contains all training plans, workout logs, and user profile data.
 */
@Database(
    entities = [
        TrainingPlan::class,
        WorkoutLog::class,
        UserProfile::class
    ],
    version = 1,
    exportSchema = true,
    autoMigrations = []
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        const val DATABASE_NAME = "tripath_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         * Use Hilt for dependency injection instead of this method when possible.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

