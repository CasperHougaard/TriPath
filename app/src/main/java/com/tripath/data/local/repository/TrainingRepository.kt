package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for all training-related data operations.
 * Provides a clean API for the domain/UI layer to interact with data.
 */
interface TrainingRepository {

    // ==================== Training Plan Operations ====================

    /**
     * Get all training plans as a reactive Flow.
     */
    fun getAllTrainingPlans(): Flow<List<TrainingPlan>>

    /**
     * Get all training plans as a one-shot list.
     */
    suspend fun getAllTrainingPlansOnce(): List<TrainingPlan>

    /**
     * Get a training plan by its ID.
     */
    suspend fun getTrainingPlanById(id: String): TrainingPlan?

    /**
     * Get training plans within a date range.
     */
    fun getTrainingPlansByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TrainingPlan>>

    /**
     * Get training plans by workout type.
     */
    fun getTrainingPlansByType(type: WorkoutType): Flow<List<TrainingPlan>>

    /**
     * Insert a new training plan.
     */
    suspend fun insertTrainingPlan(plan: TrainingPlan)

    /**
     * Insert multiple training plans.
     */
    suspend fun insertTrainingPlans(plans: List<TrainingPlan>)

    /**
     * Update an existing training plan.
     */
    suspend fun updateTrainingPlan(plan: TrainingPlan)

    /**
     * Delete a training plan.
     */
    suspend fun deleteTrainingPlan(plan: TrainingPlan)

    /**
     * Delete all training plans.
     */
    suspend fun deleteAllTrainingPlans()

    // ==================== Workout Log Operations ====================

    /**
     * Get all workout logs as a reactive Flow.
     */
    fun getAllWorkoutLogs(): Flow<List<WorkoutLog>>

    /**
     * Get all workout logs as a one-shot list.
     */
    suspend fun getAllWorkoutLogsOnce(): List<WorkoutLog>

    /**
     * Get a workout log by its Health Connect ID.
     */
    suspend fun getWorkoutLogByConnectId(connectId: String): WorkoutLog?

    /**
     * Get workout logs within a date range.
     */
    fun getWorkoutLogsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkoutLog>>

    /**
     * Get workout logs by workout type.
     */
    fun getWorkoutLogsByType(type: WorkoutType): Flow<List<WorkoutLog>>

    /**
     * Insert a workout log (from Health Connect sync).
     */
    suspend fun insertWorkoutLog(log: WorkoutLog)

    /**
     * Insert multiple workout logs.
     */
    suspend fun insertWorkoutLogs(logs: List<WorkoutLog>)

    /**
     * Delete a workout log.
     */
    suspend fun deleteWorkoutLog(log: WorkoutLog)

    /**
     * Delete all workout logs.
     */
    suspend fun deleteAllWorkoutLogs()

    /**
     * Check if a workout log exists.
     */
    suspend fun workoutLogExists(connectId: String): Boolean

    // ==================== User Profile Operations ====================

    /**
     * Get the user profile as a reactive Flow.
     */
    fun getUserProfile(): Flow<UserProfile?>

    /**
     * Get the user profile as a one-shot value.
     */
    suspend fun getUserProfileOnce(): UserProfile?

    /**
     * Insert or update the user profile.
     */
    suspend fun upsertUserProfile(profile: UserProfile)

    /**
     * Delete the user profile.
     */
    suspend fun deleteUserProfile()

    // ==================== Bulk Operations ====================

    /**
     * Delete all data from all tables.
     */
    suspend fun clearAllData()
}

