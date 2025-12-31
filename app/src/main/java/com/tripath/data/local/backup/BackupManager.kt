package com.tripath.data.local.backup

import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.UserProfile
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.room.withTransaction
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
    private val repository: TrainingRepository,
    private val recoveryRepository: RecoveryRepository,
    private val database: AppDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Export all data to a JSON string.
     * Includes training plans, workout logs, special periods, sleep logs, recovery data, and user profile.
     */
    suspend fun exportToJson(): String {
        return withContext(Dispatchers.IO) {
            val trainingPlans = repository.getAllTrainingPlansOnce()
            val workoutLogs = repository.getAllWorkoutLogsOnce()
            val rawWorkoutData = repository.getAllRawWorkoutDataOnce()
            val sleepLogs = repository.getAllSleepLogsOnce()
            val specialPeriods = repository.getAllSpecialPeriodsOnce()
            val wellnessLogs = recoveryRepository.getAllWellnessLogsOnce()
            val wellnessTasks = recoveryRepository.getAllTasksOnce()
            val userProfile = repository.getUserProfileOnce()

            val backupData = AppBackupData(
                version = BACKUP_VERSION,
                timestamp = System.currentTimeMillis(),
                trainingPlans = trainingPlans.map { it.toDto() },
                workoutLogs = workoutLogs.map { it.toDto() },
                rawWorkoutData = rawWorkoutData.map { it.toDto() },
                sleepLogs = sleepLogs.map { it.toDto() },
                specialPeriods = specialPeriods.map { it.toDto() },
                wellnessLogs = wellnessLogs.map { it.toDto() },
                wellnessTasks = wellnessTasks.map { it.toDto() },
                userProfile = userProfile?.toDto()
            )

            json.encodeToString(backupData)
        }
    }

    /**
     * Import data from a JSON string.
     * This will REPLACE all existing data with the imported data.
     * 
     * @param jsonString The JSON backup string to import
     * @return Result indicating success or failure with error details
     */
    suspend fun importFromJson(jsonString: String): Result<ImportSummary> {
        return withContext(Dispatchers.IO) {
            try {
                val backupData = json.decodeFromString<AppBackupData>(jsonString)

                // Validate backup version
                if (backupData.version > BACKUP_VERSION) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Backup version ${backupData.version} is newer than supported version $BACKUP_VERSION")
                    )
                }

                // Import all data in a transaction to ensure atomicity
                val summary = database.withTransaction {
                    // Clear all existing data
                    repository.clearAllData()
                    recoveryRepository.deleteAllLogs()
                    recoveryRepository.deleteAllTasks()

                    // Import training plans
                    val trainingPlans = backupData.trainingPlans.map { it.toEntity() }
                    repository.insertTrainingPlans(trainingPlans)

                    // Import workout logs
                    val workoutLogs = backupData.workoutLogs.map { it.toEntity() }
                    repository.insertWorkoutLogs(workoutLogs)

                    // Import raw workout data
                    val rawWorkoutData = backupData.rawWorkoutData.map { it.toEntity() }
                    repository.insertRawWorkoutData(rawWorkoutData)

                    // Import sleep logs
                    val sleepLogs = backupData.sleepLogs.map { it.toEntity() }
                    repository.insertSleepLogs(sleepLogs)

                    // Import special periods
                    val specialPeriods = backupData.specialPeriods.map { it.toEntity() }
                    repository.insertSpecialPeriods(specialPeriods)

                    // Import wellness logs
                    val wellnessLogs = backupData.wellnessLogs.map { it.toEntity() }
                    wellnessLogs.forEach { recoveryRepository.insertWellnessLog(it) }

                    // Import wellness tasks
                    val wellnessTasks = backupData.wellnessTasks.map { it.toEntity() }
                    recoveryRepository.insertTasks(wellnessTasks)

                    // Import user profile
                    backupData.userProfile?.let { profileDto ->
                        repository.upsertUserProfile(profileDto.toEntity())
                    }

                    ImportSummary(
                        trainingPlansImported = trainingPlans.size,
                        workoutLogsImported = workoutLogs.size,
                        rawWorkoutDataImported = rawWorkoutData.size,
                        sleepLogsImported = sleepLogs.size,
                        specialPeriodsImported = specialPeriods.size,
                        wellnessLogsImported = wellnessLogs.size,
                        wellnessTasksImported = wellnessTasks.size,
                        profileImported = backupData.userProfile != null
                    )
                }
                
                Result.success(summary)
            } catch (e: Exception) {
                Result.failure(e)
            }
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
        const val BACKUP_VERSION = 4
    }
}

/**
 * Summary of an import operation.
 */
data class ImportSummary(
    val trainingPlansImported: Int,
    val workoutLogsImported: Int,
    val rawWorkoutDataImported: Int,
    val sleepLogsImported: Int,
    val specialPeriodsImported: Int,
    val wellnessLogsImported: Int,
    val wellnessTasksImported: Int,
    val profileImported: Boolean
)

// ==================== Backup Data Transfer Objects ====================

/**
 * Root backup data structure.
 */
@Serializable
data class AppBackupData(
    val version: Int = 4,
    val timestamp: Long,
    val trainingPlans: List<TrainingPlanDto>,
    val workoutLogs: List<WorkoutLogDto>,
    val rawWorkoutData: List<RawWorkoutDataDto> = emptyList(),
    val sleepLogs: List<SleepLogDto> = emptyList(),
    val specialPeriods: List<SpecialPeriodDto> = emptyList(),
    val wellnessLogs: List<DailyWellnessLogDto> = emptyList(),
    val wellnessTasks: List<WellnessTaskDefinitionDto> = emptyList(),
    val userProfile: UserProfileDto?
)

/**
 * DTO for TrainingPlan serialization.
 */
@Serializable
data class TrainingPlanDto(
    val id: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
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
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val type: String,
    val durationMinutes: Int,
    val avgHeartRate: Int?,
    val calories: Int?,
    val computedTSS: Int?,
    val distanceMeters: Double?,
    val avgSpeedKmh: Double?,
    val avgPowerWatts: Int?,
    val steps: Int?,
    val hrZoneDistribution: Map<String, Int>? = null,
    val powerZoneDistribution: Map<String, Int>? = null
)

/**
 * DTO for RawWorkoutData serialization.
 */
@Serializable
data class RawWorkoutDataDto(
    val connectId: String,
    val rawExerciseType: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val hrSamplesJson: String?,
    val powerSamplesJson: String?,
    val rawCalories: Int?,
    val rawDistanceMeters: Double?,
    val rawSteps: Int?,
    val routeJson: String? = null,
    val importedAt: Long
)

/**
 * DTO for SleepLog serialization.
 */
@Serializable
data class SleepLogDto(
    val connectId: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val title: String?,
    val stagesJson: String?,
    val deepSleepMinutes: Int?,
    val lightSleepMinutes: Int?,
    val remSleepMinutes: Int?,
    val awakeMinutes: Int?,
    val importedAt: Long
)

/**
 * DTO for SpecialPeriod serialization.
 */
@Serializable
data class SpecialPeriodDto(
    val id: String,
    val type: String,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate,
    val notes: String?
)

/**
 * DTO for DailyWellnessLog serialization.
 */
@Serializable
data class DailyWellnessLogDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val hrvRmssd: Double? = null,
    val morningWeight: Double? = null,
    val sorenessIndex: Int? = null,
    val moodIndex: Int? = null,
    val allergySeverity: String? = null,
    val completedTaskIds: List<Long>? = null
)

/**
 * DTO for WellnessTaskDefinition serialization.
 */
@Serializable
data class WellnessTaskDefinitionDto(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val type: String,
    val triggerThreshold: Int? = null
)

/**
 * DTO for UserProfile serialization.
 * Note: `id` field is optional for backward compatibility with old backups.
 */
@Serializable
data class UserProfileDto(
    val id: Int? = null, // Optional for backward compatibility, ignored when converting to entity
    val ftpBike: Int?,
    val maxHeartRate: Int?,
    val defaultSwimTSS: Int?,
    val defaultStrengthHeavyTSS: Int?,
    val defaultStrengthLightTSS: Int?,
    @Serializable(with = LocalDateSerializer::class)
    val goalDate: LocalDate?,
    val weeklyHoursGoal: Float?,
    val lthr: Int?,
    val cssSecondsper100m: Int?,
    val thresholdRunPace: Int?
)

// ==================== Entity <-> DTO Conversion Extensions ====================

fun TrainingPlan.toDto() = TrainingPlanDto(
    id = id,
    date = date,
    type = type.name,
    subType = subType,
    durationMinutes = durationMinutes,
    plannedTSS = plannedTSS,
    strengthFocus = strengthFocus?.name,
    intensity = intensity?.name
)

fun TrainingPlanDto.toEntity() = TrainingPlan(
    id = id,
    date = date,
    type = WorkoutType.valueOf(type),
    subType = subType,
    durationMinutes = durationMinutes,
    plannedTSS = plannedTSS,
    strengthFocus = strengthFocus?.let { StrengthFocus.valueOf(it) },
    intensity = intensity?.let { Intensity.valueOf(it) }
)

private fun WorkoutLog.toDto() = WorkoutLogDto(
    connectId = connectId,
    date = date,
    type = type.name,
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    calories = calories,
    computedTSS = computedTSS,
    distanceMeters = distanceMeters,
    avgSpeedKmh = avgSpeedKmh,
    avgPowerWatts = avgPowerWatts,
    steps = steps,
    hrZoneDistribution = hrZoneDistribution,
    powerZoneDistribution = powerZoneDistribution
)

private fun WorkoutLogDto.toEntity() = WorkoutLog(
    connectId = connectId,
    date = date,
    type = WorkoutType.valueOf(type),
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    calories = calories,
    computedTSS = computedTSS,
    distanceMeters = distanceMeters,
    avgSpeedKmh = avgSpeedKmh,
    avgPowerWatts = avgPowerWatts,
    steps = steps,
    hrZoneDistribution = hrZoneDistribution,
    powerZoneDistribution = powerZoneDistribution
)

private fun RawWorkoutData.toDto() = RawWorkoutDataDto(
    connectId = connectId,
    rawExerciseType = rawExerciseType,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    hrSamplesJson = hrSamplesJson,
    powerSamplesJson = powerSamplesJson,
    rawCalories = rawCalories,
    rawDistanceMeters = rawDistanceMeters,
    rawSteps = rawSteps,
    routeJson = routeJson,
    importedAt = importedAt
)

private fun RawWorkoutDataDto.toEntity() = RawWorkoutData(
    connectId = connectId,
    rawExerciseType = rawExerciseType,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    hrSamplesJson = hrSamplesJson,
    powerSamplesJson = powerSamplesJson,
    rawCalories = rawCalories,
    rawDistanceMeters = rawDistanceMeters,
    rawSteps = rawSteps,
    routeJson = routeJson,
    importedAt = importedAt
)

private fun SleepLog.toDto() = SleepLogDto(
    connectId = connectId,
    date = date,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMinutes = durationMinutes,
    title = title,
    stagesJson = stagesJson,
    deepSleepMinutes = deepSleepMinutes,
    lightSleepMinutes = lightSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    awakeMinutes = awakeMinutes,
    importedAt = importedAt
)

private fun SleepLogDto.toEntity() = SleepLog(
    connectId = connectId,
    date = date,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMinutes = durationMinutes,
    title = title,
    stagesJson = stagesJson,
    deepSleepMinutes = deepSleepMinutes,
    lightSleepMinutes = lightSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    awakeMinutes = awakeMinutes,
    importedAt = importedAt
)

private fun SpecialPeriod.toDto() = SpecialPeriodDto(
    id = id,
    type = type.name,
    startDate = startDate,
    endDate = endDate,
    notes = notes
)

private fun SpecialPeriodDto.toEntity() = SpecialPeriod(
    id = id,
    type = SpecialPeriodType.valueOf(type),
    startDate = startDate,
    endDate = endDate,
    notes = notes
)

private fun UserProfile.toDto() = UserProfileDto(
    ftpBike = ftpBike,
    maxHeartRate = maxHeartRate,
    defaultSwimTSS = defaultSwimTSS,
    defaultStrengthHeavyTSS = defaultStrengthHeavyTSS,
    defaultStrengthLightTSS = defaultStrengthLightTSS,
    goalDate = goalDate,
    weeklyHoursGoal = weeklyHoursGoal,
    lthr = lthr,
    cssSecondsper100m = cssSecondsper100m,
    thresholdRunPace = thresholdRunPace
)

private fun UserProfileDto.toEntity() = UserProfile(
    ftpBike = ftpBike,
    maxHeartRate = maxHeartRate,
    defaultSwimTSS = defaultSwimTSS,
    defaultStrengthHeavyTSS = defaultStrengthHeavyTSS,
    defaultStrengthLightTSS = defaultStrengthLightTSS,
    goalDate = goalDate,
    weeklyHoursGoal = weeklyHoursGoal,
    lthr = lthr,
    cssSecondsper100m = cssSecondsper100m,
    thresholdRunPace = thresholdRunPace
)

private fun DailyWellnessLog.toDto() = DailyWellnessLogDto(
    date = date,
    sleepMinutes = sleepMinutes,
    hrvRmssd = hrvRmssd,
    morningWeight = morningWeight,
    sorenessIndex = sorenessIndex,
    moodIndex = moodIndex,
    allergySeverity = allergySeverity?.name,
    completedTaskIds = completedTaskIds
)

private fun DailyWellnessLogDto.toEntity() = DailyWellnessLog(
    date = date,
    sleepMinutes = sleepMinutes,
    hrvRmssd = hrvRmssd,
    morningWeight = morningWeight,
    sorenessIndex = sorenessIndex,
    moodIndex = moodIndex,
    allergySeverity = allergySeverity?.let { com.tripath.data.model.AllergySeverity.valueOf(it) },
    completedTaskIds = completedTaskIds
)

private fun WellnessTaskDefinition.toDto() = WellnessTaskDefinitionDto(
    id = id,
    title = title,
    description = description,
    type = type.name,
    triggerThreshold = triggerThreshold
)

private fun WellnessTaskDefinitionDto.toEntity() = WellnessTaskDefinition(
    id = id,
    title = title,
    description = description,
    type = com.tripath.data.model.TaskTriggerType.valueOf(type),
    triggerThreshold = triggerThreshold
)

