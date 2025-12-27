package com.tripath.data.local.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.tripath.data.model.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.WorkoutType
import com.tripath.domain.HeartRateSample
import com.tripath.domain.PowerSample
import com.tripath.domain.TrainingMetricsCalculator
import com.tripath.data.model.RoutePoint
import com.tripath.domain.ZoneCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    /** Number of existing workouts that had route data backfilled */
    val routeBackfilled: Int = 0,
    /** List of newly imported workouts */
    val newWorkouts: List<WorkoutLog>
)

/**
 * Detailed result from a Health Connect reprocess operation.
 */
data class ReprocessResult(
    /** Total raw workouts found in the database */
    val foundInDatabase: Int,
    /** Number of workouts that were successfully reprocessed */
    val processed: Int,
    /** Number of errors encountered */
    val errors: Int
)

/**
 * Detailed result from a sleep sync operation.
 */
data class SleepSyncResult(
    /** Total sleep sessions found in Health Connect */
    val foundInHealthConnect: Int,
    /** Number of sleep sessions newly imported */
    val newlyImported: Int,
    /** Number already in database */
    val alreadySynced: Int
)

/**
 * Data class for sleep stages (for JSON serialization).
 */
@Serializable
data class SleepStage(
    val stage: String,
    val startMillis: Long,
    val endMillis: Long
)

/**
 * Manager class for Health Connect API integration.
 * Handles permissions and reading workout data from Health Connect.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TrainingRepository,
    private val rawWorkoutDataDao: RawWorkoutDataDao,
    private val sleepLogDao: SleepLogDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    
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
     * Required permissions for reading workout and sleep data from Health Connect.
     */
    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
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
     * Read heart rate data for a specific exercise session.
     * Returns a pair of (Average HR, List of HR samples).
     */
    private suspend fun readHeartRateForSession(
        startTime: Instant,
        endTime: Instant
    ): Pair<Int?, List<HeartRateSample>> {
        return try {
            if (healthConnectClient == null) return null to emptyList()
            
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient!!.readRecords(request)
            
            if (response.records.isEmpty()) return null to emptyList()

            val allSamples = response.records.flatMap { record ->
                record.samples.map { sample ->
                    HeartRateSample(sample.time, sample.beatsPerMinute.toInt())
                }
            }
            if (allSamples.isEmpty()) return null to emptyList()

            val avgHr = allSamples.map { it.bpm }.average().toInt()
            avgHr to allSamples
        } catch (e: Exception) {
            null to emptyList()
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
     * Returns a pair of (Average Power, List of Power samples).
     */
    private suspend fun readPowerForSession(
        startTime: Instant,
        endTime: Instant
    ): Pair<Int?, List<PowerSample>> {
        return try {
            if (healthConnectClient == null) return null to emptyList()
            
            val request = ReadRecordsRequest(
                recordType = PowerRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient!!.readRecords(request)
            if (response.records.isEmpty()) return null to emptyList()
            
            val allSamples = response.records.flatMap { record ->
                record.samples.map { sample ->
                    PowerSample(sample.time, sample.power.inWatts.toInt())
                }
            }
            if (allSamples.isEmpty()) return null to emptyList()
            
            val avgPower = allSamples.map { it.watts }.average().toInt()
            avgPower to allSamples
        } catch (e: Exception) {
            null to emptyList()
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
     * Extract GPS route points from an exercise session.
     * Returns a JSON string of RoutePoint list if available.
     */
    private suspend fun readRouteForSession(
        session: ExerciseSessionRecord
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val routeResult = session.exerciseRouteResult
            
            // Check if we have route data - exerciseRouteResult can be:
            // - ExerciseRoute (contains the actual route data)
            // - null (no route data available)
            val route = routeResult as? ExerciseRoute ?: return@withContext null
            
            val routePoints = route.route.map { location ->
                RoutePoint(
                    lat = location.latitude,
                    lon = location.longitude,
                    alt = location.altitude?.inMeters,
                    t = location.time.toEpochMilli()
                )
            }
            
            if (routePoints.isEmpty()) return@withContext null
            
            // Serialize on Default dispatcher for CPU-intensive work
            withContext(Dispatchers.Default) {
                json.encodeToString(routePoints)
            }
        } catch (e: Exception) {
            Log.d("HealthConnect", "No route data available: ${e.message}")
            null
        }
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
     * Read raw exercise sessions from Health Connect within a date range.
     * Unlike readExerciseSessions, this returns the original Health Connect records.
     */
    suspend fun readRawExerciseSessions(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ExerciseSessionRecord> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable() || healthConnectClient == null) {
                return@withContext emptyList()
            }
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )

            val response = healthConnectClient!!.readRecords(request)
            response.records.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get a specific exercise session by its ID.
     */
    suspend fun getExerciseSession(sessionId: String): ExerciseSessionRecord? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable() || healthConnectClient == null) return@withContext null
            
            val response = healthConnectClient!!.readRecord(
                ExerciseSessionRecord::class,
                sessionId
            )
            response.record
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all raw data associated with a session time range.
     */
    suspend fun getSessionRawData(startTime: Instant, endTime: Instant): Map<String, Any?> = withContext(Dispatchers.IO) {
        val data = mutableMapOf<String, Any?>()
        
        data["HeartRate"] = readHeartRateForSession(startTime, endTime).second
        data["Calories"] = readCaloriesForSession(startTime, endTime)
        data["Distance"] = readDistanceForSession(startTime, endTime)
        data["Speed"] = readSpeedForSession(startTime, endTime)
        data["Power"] = readPowerForSession(startTime, endTime).second
        data["Steps"] = readStepsForSession(startTime, endTime)
        
        data
    }

    fun getAllSyncedWorkouts(): Flow<List<WorkoutLog>> {
        return repository.getAllWorkoutLogs()
    }

    /**
     * Sync workouts from Health Connect to the local database.
     * Only inserts workouts that don't already exist in RawWorkoutData.
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
            
            var newlyImportedCount = 0
            var alreadySyncedCount = 0
            var skippedUnsupportedCount = 0
            var routeBackfilledCount = 0
            val newWorkouts = mutableListOf<WorkoutLog>()

            for (session in rawSessions) {
                val connectId = session.metadata.id
                
                // Check if already stored in raw data table
                val existingData = rawWorkoutDataDao.getByConnectId(connectId)
                if (existingData != null) {
                    // Check if we need to backfill route data
                    if (existingData.routeJson == null) {
                        val routeJson = readRouteForSession(session)
                        if (routeJson != null) {
                            rawWorkoutDataDao.updateRoute(connectId, routeJson)
                            routeBackfilledCount++
                            Log.d("HealthConnect", "Backfilled route data for $connectId")
                        }
                    }
                    alreadySyncedCount++
                    continue
                }

                val workoutType = mapExerciseType(session.exerciseType)
                if (workoutType == null) {
                    skippedUnsupportedCount++
                    continue
                }

                // 1. Fetch all raw data associated with this session
                val (avgHeartRate, hrSamples) = readHeartRateForSession(session.startTime, session.endTime)
                val (avgPower, powerSamples) = readPowerForSession(session.startTime, session.endTime)
                val calories = readCaloriesForSession(session.startTime, session.endTime)
                val distance = readDistanceForSession(session.startTime, session.endTime)
                val speed = readSpeedForSession(session.startTime, session.endTime)
                val steps = readStepsForSession(session.startTime, session.endTime)
                val routeJson = readRouteForSession(session)

                // 2. Serialize samples on Default dispatcher
                val hrSamplesJson = if (hrSamples.isNotEmpty()) {
                    withContext(Dispatchers.Default) { json.encodeToString(hrSamples) }
                } else null
                
                val powerSamplesJson = if (powerSamples.isNotEmpty()) {
                    withContext(Dispatchers.Default) { json.encodeToString(powerSamples) }
                } else null

                // 3. Check storage size and log warning if > 100KB
                val totalJsonSize = (hrSamplesJson?.length ?: 0) + (powerSamplesJson?.length ?: 0) + (routeJson?.length ?: 0)
                if (totalJsonSize > 100_000) {
                    Log.w("HealthConnect", "Large workout data: ${totalJsonSize / 1024}KB for session $connectId (HR: ${hrSamplesJson?.length ?: 0}, Power: ${powerSamplesJson?.length ?: 0}, Route: ${routeJson?.length ?: 0})")
                }

                // 4. Store in RawWorkoutData table
                val rawData = RawWorkoutData(
                    connectId = connectId,
                    rawExerciseType = session.exerciseType,
                    startTimeMillis = session.startTime.toEpochMilli(),
                    endTimeMillis = session.endTime.toEpochMilli(),
                    hrSamplesJson = hrSamplesJson,
                    powerSamplesJson = powerSamplesJson,
                    rawCalories = calories,
                    rawDistanceMeters = distance,
                    rawSteps = steps,
                    routeJson = routeJson
                )
                rawWorkoutDataDao.insert(rawData)

                // 5. Process into WorkoutLog using current UserProfile
                val workoutLog = processRawDataToWorkoutLog(
                    rawData = rawData,
                    hrSamples = hrSamples,
                    powerSamples = powerSamples,
                    userProfile = userProfile
                )
                
                repository.insertWorkoutLog(workoutLog)
                newWorkouts.add(workoutLog)
                newlyImportedCount++
            }
            
            Result.success(SyncResult(
                foundInHealthConnect = totalFoundInHealthConnect,
                newlyImported = newlyImportedCount,
                alreadySynced = alreadySyncedCount,
                skippedUnsupported = skippedUnsupportedCount,
                routeBackfilled = routeBackfilledCount,
                newWorkouts = newWorkouts
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Process raw workout data into a WorkoutLog.
     * All calculations are performed on Dispatchers.Default.
     */
    private suspend fun processRawDataToWorkoutLog(
        rawData: RawWorkoutData,
        hrSamples: List<HeartRateSample>,
        powerSamples: List<PowerSample>,
        userProfile: UserProfile
    ): WorkoutLog = withContext(Dispatchers.Default) {
        val workoutType = mapExerciseType(rawData.rawExerciseType) ?: WorkoutType.OTHER
        
        val durationMinutes = ((rawData.endTimeMillis - rawData.startTimeMillis) / 60000).toInt()
        
        val avgHeartRate = if (hrSamples.isNotEmpty()) {
            hrSamples.map { it.bpm }.average().toInt()
        } else null
        
        val avgPower = if (powerSamples.isNotEmpty()) {
            powerSamples.map { it.watts }.average().toInt()
        } else null

        // Calculate Zone Distributions
        val hrZoneDistribution = if (hrSamples.isNotEmpty() && userProfile.maxHeartRate != null) {
            ZoneCalculator.calculateHrZoneDistribution(hrSamples, userProfile.maxHeartRate)
        } else null

        val powerZoneDistribution = if (powerSamples.isNotEmpty() && userProfile.ftpBike != null) {
            ZoneCalculator.calculatePowerZoneDistribution(powerSamples, userProfile.ftpBike)
        } else null

        // Calculate TSS
        val computedTSS = TrainingMetricsCalculator.calculateTSS(
            workoutType = workoutType,
            durationMin = durationMinutes,
            avgHr = avgHeartRate,
            avgPower = avgPower,
            userProfile = userProfile
        )

        val date = Instant.ofEpochMilli(rawData.startTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val avgSpeedKmh = if (rawData.rawDistanceMeters != null && durationMinutes > 0) {
            (rawData.rawDistanceMeters / 1000.0) / (durationMinutes / 60.0)
        } else null

        WorkoutLog(
            connectId = rawData.connectId,
            date = date,
            type = workoutType,
            durationMinutes = durationMinutes,
            avgHeartRate = avgHeartRate,
            calories = rawData.rawCalories,
            computedTSS = computedTSS,
            distanceMeters = rawData.rawDistanceMeters,
            avgSpeedKmh = avgSpeedKmh,
            avgPowerWatts = avgPower,
            steps = rawData.rawSteps,
            hrZoneDistribution = hrZoneDistribution,
            powerZoneDistribution = powerZoneDistribution
        )
    }

    /**
     * Reprocess all workouts in the database using the latest UserProfile settings.
     * Uses local RawWorkoutData, so it doesn't require Health Connect permissions.
     */
    suspend fun reprocessAllWorkouts(): Result<ReprocessResult> = withContext(Dispatchers.IO) {
        try {
            val userProfile = repository.getUserProfileOnce() ?: UserProfile()
            val allRawData = rawWorkoutDataDao.getAll()
            var processedCount = 0
            var errorCount = 0

            for (rawData in allRawData) {
                try {
                    // Deserialize samples on Default dispatcher
                    val hrSamples = if (!rawData.hrSamplesJson.isNullOrEmpty()) {
                        withContext(Dispatchers.Default) {
                            json.decodeFromString<List<HeartRateSample>>(rawData.hrSamplesJson)
                        }
                    } else emptyList()

                    val powerSamples = if (!rawData.powerSamplesJson.isNullOrEmpty()) {
                        withContext(Dispatchers.Default) {
                            json.decodeFromString<List<PowerSample>>(rawData.powerSamplesJson)
                        }
                    } else emptyList()

                    // Recalculate everything
                    val updatedWorkoutLog = processRawDataToWorkoutLog(
                        rawData = rawData,
                        hrSamples = hrSamples,
                        powerSamples = powerSamples,
                        userProfile = userProfile
                    )

                    repository.insertWorkoutLog(updatedWorkoutLog)
                    processedCount++
                } catch (e: Exception) {
                    Log.e("HealthConnect", "Error reprocessing workout ${rawData.connectId}", e)
                    errorCount++
                }
            }

            Result.success(ReprocessResult(
                foundInDatabase = allRawData.size,
                processed = processedCount,
                errors = errorCount
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync sleep sessions from Health Connect to the local database.
     * Only inserts sleep sessions that don't already exist.
     * 
     * @param daysToLookBack Number of days to look back from today (defaults to 30)
     * @return Result containing detailed sync information
     */
    suspend fun syncSleep(daysToLookBack: Int = 30): Result<SleepSyncResult> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(IllegalStateException("Health Connect is not available"))
            }
            
            if (!checkPermissions()) {
                return@withContext Result.failure(SecurityException("Health Connect permissions not granted"))
            }
            
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(daysToLookBack.toLong())
            
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )
            
            val sleepSessions = healthConnectClient?.readRecords(request)?.records ?: emptyList()
            val totalFoundInHealthConnect = sleepSessions.size
            
            var newlyImportedCount = 0
            var alreadySyncedCount = 0
            
            for (session in sleepSessions) {
                val connectId = session.metadata.id
                
                // Check if already stored
                if (sleepLogDao.exists(connectId)) {
                    alreadySyncedCount++
                    continue
                }
                
                // Calculate sleep duration and stage breakdown
                val durationMinutes = ((session.endTime.toEpochMilli() - session.startTime.toEpochMilli()) / 60000).toInt()
                
                // Process sleep stages
                val stages = session.stages.map { stage ->
                    SleepStage(
                        stage = mapSleepStage(stage.stage),
                        startMillis = stage.startTime.toEpochMilli(),
                        endMillis = stage.endTime.toEpochMilli()
                    )
                }
                
                val stagesJson = if (stages.isNotEmpty()) {
                    withContext(Dispatchers.Default) { json.encodeToString(stages) }
                } else null
                
                // Calculate time in each stage
                val deepSleepMinutes = calculateStageMinutes(stages, listOf("deep"))
                val lightSleepMinutes = calculateStageMinutes(stages, listOf("light"))
                val remSleepMinutes = calculateStageMinutes(stages, listOf("rem"))
                val awakeMinutes = calculateStageMinutes(stages, listOf("awake", "awake_in_bed", "out_of_bed"))
                
                val date = Instant.ofEpochMilli(session.startTime.toEpochMilli())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                
                val sleepLog = SleepLog(
                    connectId = connectId,
                    date = date,
                    startTimeMillis = session.startTime.toEpochMilli(),
                    endTimeMillis = session.endTime.toEpochMilli(),
                    durationMinutes = durationMinutes,
                    title = session.title,
                    stagesJson = stagesJson,
                    deepSleepMinutes = deepSleepMinutes,
                    lightSleepMinutes = lightSleepMinutes,
                    remSleepMinutes = remSleepMinutes,
                    awakeMinutes = awakeMinutes
                )
                
                sleepLogDao.insert(sleepLog)
                newlyImportedCount++
            }
            
            Result.success(SleepSyncResult(
                foundInHealthConnect = totalFoundInHealthConnect,
                newlyImported = newlyImportedCount,
                alreadySynced = alreadySyncedCount
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Map Health Connect sleep stage to readable string.
     */
    private fun mapSleepStage(stage: Int): String {
        return when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> "sleeping"
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "out_of_bed"
            SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
            SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
            SleepSessionRecord.STAGE_TYPE_REM -> "rem"
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "awake_in_bed"
            else -> "unknown"
        }
    }
    
    /**
     * Calculate total minutes spent in specific sleep stages.
     */
    private fun calculateStageMinutes(stages: List<SleepStage>, stageNames: List<String>): Int? {
        val filteredStages = stages.filter { it.stage in stageNames }
        if (filteredStages.isEmpty()) return null
        
        return filteredStages.sumOf { 
            ((it.endMillis - it.startMillis) / 60000).toInt() 
        }
    }
    
    /**
     * Get all sleep logs from the database.
     */
    fun getAllSleepLogs(): Flow<List<SleepLog>> {
        return sleepLogDao.getAll()
    }
}

