package com.tripath.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tripath.data.local.database.converters.Converters
import com.tripath.data.local.database.dao.DayNoteDao
import com.tripath.data.local.database.dao.DayTemplateDao
import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.dao.SpecialPeriodDao
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.WellnessDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog

/**
 * Main Room database for the TriPath application.
 * Contains all training plans, workout logs, and special periods.
 * Note: User Profile is stored in DataStore Preferences, not in Room.
 * 
 * Migrations are handled explicitly via Migration classes in the migrations package.
 */
@Database(
    entities = [
        TrainingPlan::class,
        WorkoutLog::class,
        SpecialPeriod::class,
        DayNote::class,
        DayTemplate::class,
        RawWorkoutData::class,
        SleepLog::class,
        DailyWellnessLog::class,
        WellnessTaskDefinition::class
    ],
    version = 12,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun specialPeriodDao(): SpecialPeriodDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun dayTemplateDao(): DayTemplateDao
    abstract fun rawWorkoutDataDao(): RawWorkoutDataDao
    abstract fun sleepLogDao(): SleepLogDao
    abstract fun wellnessDao(): WellnessDao

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

