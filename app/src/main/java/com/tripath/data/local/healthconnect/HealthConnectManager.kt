package com.tripath.data.local.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.tripath.data.model.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.WorkoutType
import com.tripath.domain.TrainingMetricsCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detailed result from a Health Connect sync operation.
 */
data class SyncResult(
    /** Total workouts found in Health Connect for the date range */
    val foundInHealthConnect: Int,
    /** Number of workouts that were newly imported */
    val newlyImported: Int,
    /** Number of workouts that were already in the database */
    val alreadySynced: Int,
    /** Number of workouts skipped due to unsupported exercise type */
    val skippedUnsupported: Int,
    /** List of newly imported workouts */
    val newWorkouts: List<WorkoutLog>
)

/**
 * Detailed result from a Health Connect resync operation.
 * Used to re-classify existing workouts (e.g., fixing walking/hiking misclassified as running).
 */
data class ResyncResult(
    /** Total workouts found in Health Connect for the date range */
    val workoutsFound: Int,
    /** Number of workouts that were updated (type changed) */
    val updated: Int,
    /** Number of workouts that remained unchanged */
    val unchanged: Int,
    /** Number of new workouts added */
    val new: Int,
    /** Number of errors encountered */
    val errors: Int
)

/**
 * Manager class for Health Connect API integration.
 * Handles permissions and reading workout data from Health Connect.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TrainingRepository
) {
    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            if (isAvailable()) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Required permissions for reading workout data from Health Connect.
     */
    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    /**
     * Check if Health Connect permissions are granted.
     */
    suspend fun checkPermissions(): Boolean {
        return try {
            if (!isAvailable() || healthConnectClient == null) {
                return false
            }
            val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
            permissions.all { it in granted }
        } catch (e: Exception) {
            // Health Connect not available or error accessing permissions
            false
        }
    }

    /**
     * Check if Health Connect is available on this device.
     */
    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Check if all required permissions are granted.
     * Legacy method, preferred is checkPermissions().
     */
    suspend fun hasAllPermissions(): Boolean {
        return checkPermissions()
    }

    /**
     * Get the permission request contract for Health Connect.
     * This should be used with ActivityResultLauncher in the UI layer.
     */
    fun getPermissionsToRequest() = permissions

    /**
     * Read exercise sessions from Health Connect within a date range.
     * 
     * @param startDate The start date for reading workouts (inclusive)
     * @param endDate The end date for reading workouts (inclusive)
     * @param userProfile The user profile for TSS calculation.
     * @return List of WorkoutLog entries converted from Health Connect data
     */
    suspend fun readExerciseSessions(
        startDate: LocalDate,
        endDate: LocalDate,
        userProfile: UserProfile
    ): List<WorkoutLog> {
        return try {
            if (!isAvailable() || healthConnectClient == null) {
                return emptyList()
            }
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )

            val response = healthConnectClient!!.readRecords(request)
            
            response.records.mapNotNull { session ->
                convertToWorkoutLog(session, userProfile)
            }
        } catch (e: Exception) {
            // Health Connect not available or error reading records
            emptyList()
        }
    }

    /**
     * Read heart rate data for a specific exercise session.
     */
    private suspend fun readHeartRateForSession(
        startTime: Instant,
        endTime: Instant
    ): Int? {
        return try {
            if (healthConnectClient == null) return null
            
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient!!.readRecords(request)
            
            if (response.records.isEmpty()) return null

            val allSamples = response.records.flatMap { it.samples }
            if (allSamples.isEmpty()) return null

            allSamples.map { it.beatsPerMinute }.average().toInt()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read calories burned for a specific exercise session.
     */
    private suspend fun readCaloriesForSession(
        startTime: Instant,
        endTime: Instant
    ): Int? {
        return try {
            if (healthConnectClient == null) return null
            
            val request = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient!!.readRecords(request)
            
            if (response.records.isEmpty()) return null

            // Sum up all calories burned during this time range
            response.records.sumOf { record ->
                record.energy.inKilocalories.toInt()
            }
        } catch (e: Exception) {
            // If we don't have permission or data is unavailable, return null
            null
        }
    }

    /**
     * Read total distance for a specific exercise session.
     */
    private suspend fun readDistanceForSession(
        startTime: Instant,
        endTime: Instant
    ): Double? {
        return try {
            if (healthConnectClient == null) return null
            
            val request = ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient!!.readRecords(request)
            if (response.records.isEmpty()) return null
            response.records.sumOf { it.distance.inMeters }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read average speed for a specific exercise session.
     */
    private suspend fun readSpeedForSession(
        startTime: Instant,
        endTime: Instant
    ): Double? {
        return try {
            if (healthConnectClient == null) return null
            
            val request = ReadRecordsRequest(
                recordType = SpeedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient!!.readRecords(request)
            if (response.records.isEmpty()) return null
            
            val allSamples = response.records.flatMap { it.samples }
            if (allSamples.isEmpty()) return null
            
            allSamples.map { it.speed.inKilometersPerHour }.average()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read average power for a specific exercise session.
     */
    private suspend fun readPowerForSession(
        startTime: Instant,
        endTime: Instant
    ): Int? {
        return try {
            if (healthConnectClient == null) return null
            
            val request = ReadRecordsRequest(
                recordType = PowerRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient!!.readRecords(request)
            if (response.records.isEmpty()) return null
            
            val allSamples = response.records.flatMap { it.samples }
            if (allSamples.isEmpty()) return null
            
            allSamples.map { it.power.inWatts }.average().toInt()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read total steps for a specific exercise session.
     */
    private suspend fun readStepsForSession(
        startTime: Instant,
        endTime: Instant
    ): Int? {
        return try {
            if (healthConnectClient == null) return null
            
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient!!.readRecords(request)
            if (response.records.isEmpty()) return null
            response.records.sumOf { it.count }.toInt()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert a Health Connect ExerciseSessionRecord to our WorkoutLog entity.
     */
    private suspend fun convertToWorkoutLog(
        session: ExerciseSessionRecord,
        userProfile: UserProfile
    ): WorkoutLog? {
        val workoutType = mapExerciseType(session.exerciseType) ?: return null
        
        val durationMinutes = java.time.Duration.between(
            session.startTime,
            session.endTime
        ).toMinutes().toInt()

        val avgHeartRate = readHeartRateForSession(session.startTime, session.endTime)
        val calories = readCaloriesForSession(session.startTime, session.endTime)
        
        // New fields
        val distance = readDistanceForSession(session.startTime, session.endTime)
        val speed = readSpeedForSession(session.startTime, session.endTime)
        val power = readPowerForSession(session.startTime, session.endTime)
        val steps = readStepsForSession(session.startTime, session.endTime)

        // Calculate TSS using the new Calculation Engine
        val computedTSS = TrainingMetricsCalculator.calculateTSS(
            workoutType = workoutType,
            durationMin = durationMinutes,
            avgHr = avgHeartRate,
            avgPower = power,
            userProfile = userProfile
        )

        val date = session.startTime
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        return WorkoutLog(
            connectId = session.metadata.id,
            date = date,
            type = workoutType,
            durationMinutes = durationMinutes,
            avgHeartRate = avgHeartRate,
            calories = calories,
            computedTSS = computedTSS,
            distanceMeters = distance,
            avgSpeedKmh = speed,
            avgPowerWatts = power,
            steps = steps
        )
    }

    /**
     * Map Health Connect exercise type to our WorkoutType enum.
     */
    private fun mapExerciseType(exerciseType: Int): WorkoutType? {
        return when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WorkoutType.RUN
            
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WorkoutType.BIKE
            
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> WorkoutType.SWIM
            
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> WorkoutType.STRENGTH
            
            // Non-specific cardio activities -> OTHER to prevent data pollution
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_SKATING -> WorkoutType.OTHER
            
            else -> null // Ignore unsupported exercise types
        }
    }

    /**
     * Get all workout logs from the database (both synced from Health Connect and any manual entries).
     */
    fun getAllSyncedWorkouts(): Flow<List<WorkoutLog>> {
        return repository.getAllWorkoutLogs()
    }

    /**
     * Sync workouts from Health Connect to the local database.
     * Only inserts workouts that don't already exist (based on connectId).
     * 
     * @param daysToLookBack Number of days to look back from today (defaults to 30)
     * @return Result containing detailed sync information
     */
    suspend fun syncWorkouts(daysToLookBack: Int = 30): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
            }
            
            if (!checkPermissions()) {
                return@withContext Result.failure(SecurityException("Health Connect permissions not granted"))
            }
            
            // Fetch UserProfile for TSS calculations
            val userProfile = repository.getUserProfileOnce() ?: UserProfile()

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(daysToLookBack.toLong())
            
            // Read raw exercise sessions to count total found
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )
            
            val rawSessions = healthConnectClient?.readRecords(request)?.records ?: emptyList()
            val totalFoundInHealthConnect = rawSessions.size
            
            // Convert to WorkoutLog (this filters unsupported types)
            val workouts = readExerciseSessions(startDate, endDate, userProfile)
            val skippedUnsupported = totalFoundInHealthConnect - workouts.size
            
            // Check each workout and only insert if it doesn't exist
            val newWorkouts = mutableListOf<WorkoutLog>()
            var alreadySynced = 0
            
            for (workout in workouts) {
                if (!repository.workoutLogExists(workout.connectId)) {
                    repository.insertWorkoutLog(workout)
                    newWorkouts.add(workout)
                } else {
                    alreadySynced++
                }
            }
            
            Result.success(SyncResult(
                foundInHealthConnect = totalFoundInHealthConnect,
                newlyImported = newWorkouts.size,
                alreadySynced = alreadySynced,
                skippedUnsupported = skippedUnsupported,
                newWorkouts = newWorkouts
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resync workout history to re-classify existing workouts.
     * Re-fetches workouts from Health Connect and updates existing records if their type has changed.
     * This is useful for fixing misclassified workouts (e.g., walking/hiking previously saved as RUN).
     * 
     * @param daysToLookBack Number of days to look back from today (defaults to 30)
     * @return Result containing detailed resync information
     */
    suspend fun resyncHistory(daysToLookBack: Int = 30): Result<ResyncResult> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
            }
            
            if (!checkPermissions()) {
                return@withContext Result.failure(SecurityException("Health Connect permissions not granted"))
            }
            
            // Fetch UserProfile for TSS calculations
            val userProfile = repository.getUserProfileOnce() ?: UserProfile()

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(daysToLookBack.toLong())
            
            // Read raw exercise sessions to count total found
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )
            
            val rawSessions = healthConnectClient?.readRecords(request)?.records ?: emptyList()
            val totalFound = rawSessions.size
            
            // Process each session
            var updated = 0
            var unchanged = 0
            var new = 0
            var errors = 0
            
            for (session in rawSessions) {
                try {
                    val convertedWorkout = convertToWorkoutLog(session, userProfile)
                    if (convertedWorkout == null) {
                        // Unsupported exercise type - skip
                        continue
                    }
                    
                    val existingWorkout = repository.getWorkoutLogByConnectId(convertedWorkout.connectId)
                    
                    if (existingWorkout != null) {
                        // Workout exists - check if type changed
                        if (existingWorkout.type != convertedWorkout.type) {
                            // Type changed - update the record
                            repository.insertWorkoutLog(convertedWorkout) // Uses REPLACE strategy
                            updated++
                        } else {
                            // Type unchanged
                            unchanged++
                        }
                    } else {
                        // New workout - insert it
                        repository.insertWorkoutLog(convertedWorkout)
                        new++
                    }
                } catch (e: Exception) {
                    errors++
                    // Continue processing other workouts even if one fails
                }
            }
            
            Result.success(ResyncResult(
                workoutsFound = totalFound,
                updated = updated,
                unchanged = unchanged,
                new = new,
                errors = errors
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

