package com.tripath.data.local.backup

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for backup and restore operations.
 * Handles exporting and importing all app data as JSON.
 */
@Singleton
class BackupManager @Inject constructor(
    private val repository: TrainingRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Export all data to a JSON string.
     * Includes training plans, workout logs, and user profile.
     */
    suspend fun exportToJson(): String {
        val trainingPlans = repository.getAllTrainingPlansOnce()
        val workoutLogs = repository.getAllWorkoutLogsOnce()
        val userProfile = repository.getUserProfileOnce()

        val backupData = BackupData(
            version = BACKUP_VERSION,
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            trainingPlans = trainingPlans.map { it.toDto() },
            workoutLogs = workoutLogs.map { it.toDto() },
            userProfile = userProfile?.toDto()
        )

        return json.encodeToString(backupData)
    }

    /**
     * Import data from a JSON string.
     * This will REPLACE all existing data with the imported data.
     * 
     * @param jsonString The JSON backup string to import
     * @return Result indicating success or failure with error details
     */
    suspend fun importFromJson(jsonString: String): Result<ImportSummary> {
        return try {
            val backupData = json.decodeFromString<BackupData>(jsonString)

            // TODO: Handle migration logic if imported version < current version
            
            // Validate backup version
            if (backupData.version > BACKUP_VERSION) {
                return Result.failure(
                    IllegalArgumentException("Backup version ${backupData.version} is newer than supported version $BACKUP_VERSION")
                )
            }

            // Clear all existing data
            repository.clearAllData()

            // Import training plans
            val trainingPlans = backupData.trainingPlans.map { it.toEntity() }
            repository.insertTrainingPlans(trainingPlans)

            // Import workout logs
            val workoutLogs = backupData.workoutLogs.map { it.toEntity() }
            repository.insertWorkoutLogs(workoutLogs)

            // Import user profile
            backupData.userProfile?.let { profileDto ->
                repository.upsertUserProfile(profileDto.toEntity())
            }

            Result.success(
                ImportSummary(
                    trainingPlansImported = trainingPlans.size,
                    workoutLogsImported = workoutLogs.size,
                    profileImported = backupData.userProfile != null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all data from the database.
     */
    suspend fun clearAllData(): Result<Unit> {
        return try {
            repository.clearAllData()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}

/**
 * Summary of an import operation.
 */
data class ImportSummary(
    val trainingPlansImported: Int,
    val workoutLogsImported: Int,
    val profileImported: Boolean
)

// ==================== Backup Data Transfer Objects ====================

/**
 * Root backup data structure.
 */
@Serializable
data class BackupData(
    val version: Int,
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val trainingPlans: List<TrainingPlanDto>,
    val workoutLogs: List<WorkoutLogDto>,
    val userProfile: UserProfileDto?
)

/**
 * DTO for TrainingPlan serialization.
 */
@Serializable
data class TrainingPlanDto(
    val id: String,
    val dateEpochDay: Long,
    val type: String,
    val subType: String?,
    val durationMinutes: Int,
    val plannedTSS: Int,
    val strengthFocus: String?,
    val intensity: String?
)

/**
 * DTO for WorkoutLog serialization.
 */
@Serializable
data class WorkoutLogDto(
    val connectId: String,
    val dateEpochDay: Long,
    val type: String,
    val durationMinutes: Int,
    val avgHeartRate: Int?,
    val calories: Int?,
    val computedTSS: Int?
)

/**
 * DTO for UserProfile serialization.
 */
@Serializable
data class UserProfileDto(
    val id: Int,
    val ftp: Int?,
    val goalDateEpochDay: Long?,
    val weeklyHoursGoal: Float?,
    val lthr: Int?,
    val cssSecondsper100m: Int?
)

// ==================== Entity <-> DTO Conversion Extensions ====================

private fun TrainingPlan.toDto() = TrainingPlanDto(
    id = id,
    dateEpochDay = date.toEpochDay(),
    type = type.name,
    subType = subType,
    durationMinutes = durationMinutes,
    plannedTSS = plannedTSS,
    strengthFocus = strengthFocus?.name,
    intensity = intensity?.name
)

private fun TrainingPlanDto.toEntity() = TrainingPlan(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    type = WorkoutType.valueOf(type),
    subType = subType,
    durationMinutes = durationMinutes,
    plannedTSS = plannedTSS,
    strengthFocus = strengthFocus?.let { StrengthFocus.valueOf(it) },
    intensity = intensity?.let { Intensity.valueOf(it) }
)

private fun WorkoutLog.toDto() = WorkoutLogDto(
    connectId = connectId,
    dateEpochDay = date.toEpochDay(),
    type = type.name,
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    calories = calories,
    computedTSS = computedTSS
)

private fun WorkoutLogDto.toEntity() = WorkoutLog(
    connectId = connectId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    type = WorkoutType.valueOf(type),
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    calories = calories,
    computedTSS = computedTSS
)

private fun UserProfile.toDto() = UserProfileDto(
    id = id,
    ftp = ftp,
    goalDateEpochDay = goalDate?.toEpochDay(),
    weeklyHoursGoal = weeklyHoursGoal,
    lthr = lthr,
    cssSecondsper100m = cssSecondsper100m
)

private fun UserProfileDto.toEntity() = UserProfile(
    id = id,
    ftp = ftp,
    goalDate = goalDateEpochDay?.let { LocalDate.ofEpochDay(it) },
    weeklyHoursGoal = weeklyHoursGoal,
    lthr = lthr,
    cssSecondsper100m = cssSecondsper100m
)

