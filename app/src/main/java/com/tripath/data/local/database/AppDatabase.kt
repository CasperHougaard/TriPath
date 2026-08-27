package com.tripath.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tripath.data.local.database.converters.Converters
import com.tripath.data.local.database.dao.BodyCompositionDao
import com.tripath.data.local.database.dao.DailyActivityDao
import com.tripath.data.local.database.dao.DayNoteDao
import com.tripath.data.local.database.dao.DayTemplateDao
import com.tripath.data.local.database.dao.LiftPathDao
import com.tripath.data.local.database.dao.NutritionEntryDao
import com.tripath.data.local.database.dao.NutritionLogDao
import com.tripath.data.local.database.dao.NutritionPresetDao
import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.dao.ScannedFoodDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.dao.SpecialPeriodDao
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.WellnessDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSessionLog
import com.tripath.data.local.database.entities.LiftSetLog
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.NutritionPreset
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.ScannedFoodCache
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
 *
 * When adding an entity here, also add it to
 * [com.tripath.data.local.backup.AppBackupData] and to
 * [com.tripath.data.local.repository.TrainingRepository.clearAllData], or the new table will be
 * left out of the user's backup and survive a data reset.
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
        WellnessTaskDefinition::class,
        BodyCompositionLog::class,
        NutritionLog::class,
        NutritionEntry::class,
        NutritionPreset::class,
        DailyActivityLog::class,
        LiftSessionLog::class,
        LiftSetLog::class,
        LiftExerciseCatalogEntry::class,
        ScannedFoodCache::class
    ],
    version = 24,
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
    abstract fun bodyCompositionDao(): BodyCompositionDao
    abstract fun nutritionLogDao(): NutritionLogDao
    abstract fun nutritionEntryDao(): NutritionEntryDao
    abstract fun nutritionPresetDao(): NutritionPresetDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun liftPathDao(): LiftPathDao
    abstract fun scannedFoodDao(): ScannedFoodDao

    companion object {
        const val DATABASE_NAME = "tripath_database"
    }
}

