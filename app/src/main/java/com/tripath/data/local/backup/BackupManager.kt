package com.tripath.data.local.backup

import android.content.Context
import androidx.room.withTransaction
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.dao.BodyCompositionDao
import com.tripath.data.local.database.dao.DailyActivityDao
import com.tripath.data.local.database.dao.DayNoteDao
import com.tripath.data.local.database.dao.DayTemplateDao
import com.tripath.data.local.database.dao.NutritionEntryDao
import com.tripath.data.local.database.dao.NutritionLogDao
import com.tripath.data.local.database.dao.WellnessDao
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.widget.refreshNutritionWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports and restores the user's complete dataset as JSON.
 *
 * This is the single source of truth for "all the user's data": both the manual
 * export/import in Settings and the cloud snapshot uploaded by Android Auto Backup
 * ([CloudSnapshotStore]) go through here. See [AppBackupData] for the coverage contract.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TrainingRepository,
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val dayNoteDao: DayNoteDao,
    private val dayTemplateDao: DayTemplateDao,
    private val wellnessDao: WellnessDao,
    private val bodyCompositionDao: BodyCompositionDao,
    private val nutritionLogDao: NutritionLogDao,
    private val nutritionEntryDao: NutritionEntryDao,
    private val dailyActivityDao: DailyActivityDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Compact JSON for the cloud snapshot — pretty-printing a file nobody reads by hand
     * just burns quota.
     */
    private val compactJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Collect every table and preference into a backup structure.
     *
     * Reads happen inside a single transaction so the snapshot is a consistent point-in-time
     * view rather than a set of tables read at slightly different moments.
     */
    suspend fun exportToBackupData(
        appVersionCode: Long? = null,
        appVersionName: String? = null
    ): AppBackupData = withContext(Dispatchers.IO) {
        // Preferences live in DataStore, outside the Room transaction.
        val preferences = preferencesManager.exportAll().map { it.toDto() }

        database.withTransaction {
            AppBackupData(
                version = BACKUP_VERSION,
                timestamp = System.currentTimeMillis(),
                appVersionCode = appVersionCode,
                appVersionName = appVersionName,
                trainingPlans = repository.getAllTrainingPlansOnce().map { it.toDto() },
                workoutLogs = repository.getAllWorkoutLogsOnceIncludingIgnored().map { it.toDto() },
                rawWorkoutData = repository.getAllRawWorkoutDataOnce().map { it.toDto() },
                sleepLogs = repository.getAllSleepLogsOnce().map { it.toDto() },
                specialPeriods = repository.getAllSpecialPeriodsOnce().map { it.toDto() },
                dayNotes = dayNoteDao.getAllOnce().map { it.toDto() },
                dayTemplates = dayTemplateDao.getAllOnce().map { it.toDto() },
                wellnessLogs = wellnessDao.getAllLogsOnce().map { it.toDto() },
                wellnessTasks = wellnessDao.getAllTasksOnce().map { it.toDto() },
                bodyCompositionLogs = bodyCompositionDao.getAllOnce().map { it.toDto() },
                nutritionLogs = nutritionLogDao.getAllOnce().map { it.toDto() },
                nutritionEntries = nutritionEntryDao.getAllOnce().map { it.toDto() },
                dailyActivityLogs = dailyActivityDao.getAllOnce().map { it.toDto() },
                preferences = preferences,
                // Version 5+ carries the profile inside `preferences`.
                userProfile = null
            )
        }
    }

    /**
     * Export all data as a human-readable JSON string, for the "export to file" flow.
     */
    suspend fun exportToJson(
        appVersionCode: Long? = null,
        appVersionName: String? = null
    ): String = withContext(Dispatchers.IO) {
        json.encodeToString(exportToBackupData(appVersionCode, appVersionName))
    }

    /**
     * Export all data as compact JSON, for the gzipped cloud snapshot.
     */
    suspend fun exportToCompactJson(
        appVersionCode: Long? = null,
        appVersionName: String? = null
    ): String = withContext(Dispatchers.IO) {
        compactJson.encodeToString(exportToBackupData(appVersionCode, appVersionName))
    }

    /**
     * Restore data from a JSON backup string.
     *
     * @param jsonString the backup contents.
     * @param mode [ImportMode.MERGE] keeps records that aren't in the backup;
     *   [ImportMode.REPLACE_ALL] deletes everything first.
     */
    suspend fun importFromJson(
        jsonString: String,
        mode: ImportMode = ImportMode.MERGE
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            val backupData = json.decodeFromString<AppBackupData>(jsonString)
            importBackupData(backupData, mode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restore an already-parsed backup.
     *
     * The whole restore runs in one transaction: a failure part-way through rolls back rather
     * than leaving the user with half their data — which matters most in [ImportMode.REPLACE_ALL],
     * where the delete has already happened.
     */
    suspend fun importBackupData(
        backupData: AppBackupData,
        mode: ImportMode = ImportMode.MERGE
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            if (backupData.version > BACKUP_VERSION) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "This backup was made by a newer version of TriPath " +
                            "(format ${backupData.version}, this app supports $BACKUP_VERSION). " +
                            "Update the app and try again."
                    )
                )
            }

            val replace = mode == ImportMode.REPLACE_ALL

            val summary = database.withTransaction {
                if (replace) {
                    repository.clearAllData()
                }

                val trainingPlans = backupData.trainingPlans.map { it.toEntity() }
                repository.insertTrainingPlans(trainingPlans)

                val workoutLogs = backupData.workoutLogs.map { it.toEntity() }
                repository.insertWorkoutLogs(workoutLogs)

                val rawWorkoutData = backupData.rawWorkoutData.map { it.toEntity() }
                repository.insertRawWorkoutData(rawWorkoutData)

                val sleepLogs = backupData.sleepLogs.map { it.toEntity() }
                repository.insertSleepLogs(sleepLogs)

                val specialPeriods = backupData.specialPeriods.mapNotNull { it.toEntity() }
                repository.insertSpecialPeriods(specialPeriods)

                val dayNotes = backupData.dayNotes.map { it.toEntity() }
                dayNoteDao.insertAll(dayNotes)

                val dayTemplates = backupData.dayTemplates.map { it.toEntity() }
                dayTemplateDao.insertAll(dayTemplates)

                val wellnessLogs = backupData.wellnessLogs.map { it.toEntity() }
                wellnessLogs.forEach { wellnessDao.insertLog(it) }

                val wellnessTasks = backupData.wellnessTasks.map { it.toEntity() }
                wellnessDao.insertTasks(wellnessTasks)

                val bodyCompositionLogs = backupData.bodyCompositionLogs.map { it.toEntity() }
                bodyCompositionDao.insertAll(bodyCompositionLogs)

                val nutritionLogs = backupData.nutritionLogs.map { it.toEntity() }
                nutritionLogs.forEach { nutritionLogDao.upsert(it) }

                val nutritionEntries = backupData.nutritionEntries.map { it.toEntity() }
                nutritionEntryDao.insertAll(nutritionEntries)

                val dailyActivityLogs = backupData.dailyActivityLogs.map { it.toEntity() }
                dailyActivityDao.upsertAll(dailyActivityLogs)

                ImportSummary(
                    trainingPlansImported = trainingPlans.size,
                    workoutLogsImported = workoutLogs.size,
                    rawWorkoutDataImported = rawWorkoutData.size,
                    sleepLogsImported = sleepLogs.size,
                    specialPeriodsImported = specialPeriods.size,
                    specialPeriodsSkipped = backupData.specialPeriods.size - specialPeriods.size,
                    dayNotesImported = dayNotes.size,
                    dayTemplatesImported = dayTemplates.size,
                    wellnessLogsImported = wellnessLogs.size,
                    wellnessTasksImported = wellnessTasks.size,
                    bodyCompositionLogsImported = bodyCompositionLogs.size,
                    nutritionLogsImported = nutritionLogs.size,
                    nutritionEntriesImported = nutritionEntries.size,
                    dailyActivityLogsImported = dailyActivityLogs.size,
                    mode = mode
                )
            }

            // Preferences are written after the database transaction commits: DataStore isn't
            // covered by the Room transaction, so writing them inside it would leave settings
            // applied even if the record restore rolled back.
            val preferencesImported = if (backupData.preferences.isNotEmpty()) {
                preferencesManager.importAll(
                    entries = backupData.preferences.map { it.toEntry() },
                    replace = replace
                )
            } else {
                // Backup format <= 4 stored the profile in its own object.
                backupData.userProfile?.let { profileDto ->
                    repository.upsertUserProfile(profileDto.toEntity())
                    1
                } ?: 0
            }

            // The home-screen widget reads nutrition straight from the database, so without this
            // it would keep showing the pre-restore day until the next in-app edit.
            refreshNutritionWidget(context)

            Result.success(summary.copy(preferencesImported = preferencesImported))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all data from the database and the stored profile.
     */
    suspend fun clearAllData(): Result<Unit> {
        return try {
            repository.clearAllData()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse a backup without writing anything, so callers can describe it to the user
     * (record counts, date) before asking whether to restore.
     */
    fun parse(jsonString: String): Result<AppBackupData> =
        try {
            Result.success(json.decodeFromString<AppBackupData>(jsonString))
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        /**
         * Backup format version.
         *
         * - 4: training plans, workout logs, raw samples, sleep, special periods, profile object.
         * - 5: adds day notes, day templates, wellness logs and tasks, body composition,
         *   nutrition, sleep scores, and all preferences (superseding the profile object).
         *
         * `nutritionEntries` (the itemised nutrition ledger) and `dailyActivityLogs` (whole-day
         * steps, calories and HRV) were added without a bump: both fields are defaulted and both
         * readers use `ignoreUnknownKeys`, so older installs still restore these files — whereas
         * version 6 would make them reject the backup outright.
         */
        const val BACKUP_VERSION = 5
    }
}

/**
 * How an import treats data already on the device.
 */
enum class ImportMode {
    /**
     * Upsert by primary key. Records present in the backup overwrite their local counterpart;
     * anything recorded since the backup was taken is left alone. Safe default: importing an
     * old file can never delete newer data.
     */
    MERGE,

    /**
     * Wipe everything, then restore. Produces an exact copy of the backup, at the cost of
     * discarding anything logged since it was made.
     */
    REPLACE_ALL
}

/**
 * Per-table result of an import, so the UI can report what actually landed.
 */
data class ImportSummary(
    val trainingPlansImported: Int = 0,
    val workoutLogsImported: Int = 0,
    val rawWorkoutDataImported: Int = 0,
    val sleepLogsImported: Int = 0,
    val specialPeriodsImported: Int = 0,
    val specialPeriodsSkipped: Int = 0,
    val dayNotesImported: Int = 0,
    val dayTemplatesImported: Int = 0,
    val wellnessLogsImported: Int = 0,
    val wellnessTasksImported: Int = 0,
    val bodyCompositionLogsImported: Int = 0,
    val nutritionLogsImported: Int = 0,
    val nutritionEntriesImported: Int = 0,
    val dailyActivityLogsImported: Int = 0,
    val liftSessionLogsImported: Int = 0,
    val liftSetLogsImported: Int = 0,
    val liftExerciseCatalogImported: Int = 0,
    val preferencesImported: Int = 0,
    val mode: ImportMode = ImportMode.MERGE
) {
    /** Total records written, excluding preferences. */
    val totalRecords: Int
        get() = trainingPlansImported + workoutLogsImported + rawWorkoutDataImported +
            sleepLogsImported + specialPeriodsImported + dayNotesImported +
            dayTemplatesImported + wellnessLogsImported + wellnessTasksImported +
            bodyCompositionLogsImported + nutritionLogsImported + nutritionEntriesImported +
            dailyActivityLogsImported + liftSessionLogsImported + liftSetLogsImported +
            liftExerciseCatalogImported
}
