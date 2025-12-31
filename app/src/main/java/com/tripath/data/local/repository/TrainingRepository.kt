package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.UserProfile
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

    /**
     * Delete training plans within a date range.
     */
    suspend fun deleteTrainingPlansByDateRange(startDate: LocalDate, endDate: LocalDate)

    /**
     * Copy all training plans from source week to target week.
     */
    suspend fun copyWeek(sourceStartDate: LocalDate, targetStartDate: LocalDate)

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
     * Get average weekly TSS per workout type over the last 4 weeks (28 days).
     * Used for discipline-specific planning logic to prevent injury.
     * 
     * @return Map of WorkoutType to average weekly TSS (Int)
     */
    suspend fun getRecentDisciplineLoads(): Map<WorkoutType, Int>

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

    // ==================== Raw Workout Data Operations ====================

    /**
     * Get all raw workout data as a one-shot list.
     */
    suspend fun getAllRawWorkoutDataOnce(): List<RawWorkoutData>

    /**
     * Get raw workout data by its Health Connect ID.
     */
    suspend fun getRawWorkoutData(connectId: String): RawWorkoutData?

    /**
     * Insert multiple raw workout data records.
     */
    suspend fun insertRawWorkoutData(data: List<RawWorkoutData>)

    // ==================== Sleep Log Operations ====================

    /**
     * Get all sleep logs as a reactive Flow.
     */
    fun getAllSleepLogs(): Flow<List<SleepLog>>

    /**
     * Get all sleep logs as a one-shot list (for backup).
     */
    suspend fun getAllSleepLogsOnce(): List<SleepLog>

    /**
     * Get sleep logs within a date range.
     */
    fun getSleepLogsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SleepLog>>

    /**
     * Get sleep log for a specific date (one-shot).
     */
    suspend fun getSleepLogByDate(date: LocalDate): SleepLog?

    /**
     * Insert multiple sleep logs.
     */
    suspend fun insertSleepLogs(logs: List<SleepLog>)

    /**
     * Delete all sleep logs.
     */
    suspend fun deleteAllSleepLogs()

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

    // ==================== Special Period Operations ====================

    fun getAllSpecialPeriods(): Flow<List<SpecialPeriod>>

    /**
     * Get all special periods as a one-shot list (for backup).
     */
    suspend fun getAllSpecialPeriodsOnce(): List<SpecialPeriod>
    
    fun getActiveSpecialPeriods(date: LocalDate): Flow<List<SpecialPeriod>>

    fun getSpecialPeriodsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SpecialPeriod>>
    
    suspend fun insertSpecialPeriod(specialPeriod: SpecialPeriod)

    /**
     * Insert multiple special periods.
     */
    suspend fun insertSpecialPeriods(periods: List<SpecialPeriod>)
    
    suspend fun deleteSpecialPeriod(id: String)

    // ==================== Day Note Operations ====================

    fun getDayNote(date: LocalDate): Flow<DayNote?>

    suspend fun getDayNoteOnce(date: LocalDate): DayNote?

    suspend fun insertDayNote(dayNote: DayNote)

    suspend fun updateDayNote(dayNote: DayNote)

    suspend fun deleteDayNote(dayNote: DayNote)

    // ==================== Day Template Operations ====================

    fun getAllDayTemplates(): Flow<List<DayTemplate>>

    suspend fun getDayTemplateById(id: String): DayTemplate?

    suspend fun insertDayTemplate(template: DayTemplate)

    suspend fun deleteDayTemplate(template: DayTemplate)
}

