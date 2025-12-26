package com.tripath.data.local.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class for Health Connect API integration.
 * Handles permissions and reading workout data from Health Connect.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /**
     * Required permissions for reading workout data from Health Connect.
     */
    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    /**
     * Check if Health Connect is available on this device.
     */
    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Check if all required permissions are granted.
     */
    suspend fun hasAllPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return permissions.all { it in granted }
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
     * @return List of WorkoutLog entries converted from Health Connect data
     */
    suspend fun readExerciseSessions(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WorkoutLog> {
        val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
        )

        val response = healthConnectClient.readRecords(request)
        
        return response.records.mapNotNull { session ->
            convertToWorkoutLog(session)
        }
    }

    /**
     * Read heart rate data for a specific exercise session.
     */
    private suspend fun readHeartRateForSession(
        startTime: Instant,
        endTime: Instant
    ): Int? {
        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val response = healthConnectClient.readRecords(request)
        
        if (response.records.isEmpty()) return null

        val allSamples = response.records.flatMap { it.samples }
        if (allSamples.isEmpty()) return null

        return allSamples.map { it.beatsPerMinute }.average().toInt()
    }

    /**
     * Convert a Health Connect ExerciseSessionRecord to our WorkoutLog entity.
     */
    private suspend fun convertToWorkoutLog(session: ExerciseSessionRecord): WorkoutLog? {
        val workoutType = mapExerciseType(session.exerciseType) ?: return null
        
        val durationMinutes = java.time.Duration.between(
            session.startTime,
            session.endTime
        ).toMinutes().toInt()

        val avgHeartRate = readHeartRateForSession(session.startTime, session.endTime)
        
        val date = session.startTime
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        return WorkoutLog(
            connectId = session.metadata.id,
            date = date,
            type = workoutType,
            durationMinutes = durationMinutes,
            avgHeartRate = avgHeartRate,
            calories = null, // TODO: Read from ActiveCaloriesBurnedRecord
            computedTSS = null // TODO: Calculate TSS based on workout data
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
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> WorkoutType.STRENGTH
            
            else -> null // Ignore unsupported exercise types
        }
    }

    /**
     * Sync workouts from Health Connect to the local database.
     * This is a convenience method that reads and returns new workouts.
     * 
     * @param startDate The start date for syncing (defaults to 30 days ago)
     * @param endDate The end date for syncing (defaults to today)
     * @return List of new WorkoutLog entries from Health Connect
     */
    suspend fun syncWorkouts(
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate = LocalDate.now()
    ): Result<List<WorkoutLog>> {
        return try {
            if (!isAvailable()) {
                return Result.failure(IllegalStateException("Health Connect is not available"))
            }
            
            if (!hasAllPermissions()) {
                return Result.failure(SecurityException("Health Connect permissions not granted"))
            }
            
            val workouts = readExerciseSessions(startDate, endDate)
            Result.success(workouts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

