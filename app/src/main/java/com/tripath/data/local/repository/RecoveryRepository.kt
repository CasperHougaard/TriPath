package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for all recovery and wellness-related data operations.
 * Provides a clean API for the domain/UI layer to interact with wellness data.
 */
interface RecoveryRepository {

    // ==================== Daily Wellness Log Operations ====================

    /**
     * Get a daily wellness log by date.
     */
    suspend fun getWellnessLog(date: LocalDate): DailyWellnessLog?

    /**
     * Get a daily wellness log by date as a reactive Flow.
     */
    fun getWellnessLogFlow(date: LocalDate): Flow<DailyWellnessLog?>

    /**
     * Get all daily wellness logs ordered by date (newest first).
     */
    fun getAllWellnessLogs(): Flow<List<DailyWellnessLog>>

    /**
     * Get all daily wellness logs as a one-shot list (for backup).
     */
    suspend fun getAllWellnessLogsOnce(): List<DailyWellnessLog>

    /**
     * Get daily wellness logs within a date range.
     */
    fun getWellnessLogsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyWellnessLog>>

    /**
     * Get the most recent recorded weight before the given date.
     */
    suspend fun getLastRecordedWeight(currentDate: LocalDate): Double?

    /**
     * Insert or update a daily wellness log.
     */
    suspend fun insertWellnessLog(log: DailyWellnessLog)

    /**
     * Update an existing daily wellness log.
     */
    suspend fun updateWellnessLog(log: DailyWellnessLog)

    /**
     * Delete a daily wellness log.
     */
    suspend fun deleteWellnessLog(log: DailyWellnessLog)

    // ==================== Wellness Task Definition Operations ====================

    /**
     * Get all wellness task definitions ordered by id.
     */
    fun getAllTasks(): Flow<List<WellnessTaskDefinition>>

    /**
     * Get all wellness task definitions as a one-shot list (for backup).
     */
    suspend fun getAllTasksOnce(): List<WellnessTaskDefinition>

    /**
     * Get a wellness task definition by id.
     */
    suspend fun getTaskById(id: Long): WellnessTaskDefinition?

    /**
     * Insert a new wellness task definition.
     */
    suspend fun insertTask(task: WellnessTaskDefinition): Long

    /**
     * Insert multiple wellness task definitions.
     */
    suspend fun insertTasks(tasks: List<WellnessTaskDefinition>)

    /**
     * Update an existing wellness task definition.
     */
    suspend fun updateTask(task: WellnessTaskDefinition)

    /**
     * Delete a wellness task definition.
     */
    suspend fun deleteTask(task: WellnessTaskDefinition)

    /**
     * Delete a wellness task definition by id.
     */
    suspend fun deleteTaskById(id: Long)

    // ==================== Initialization ====================

    /**
     * Initialize default wellness tasks if the task table is empty.
     * Inserts standard tasks: Creatine, Vitamins, Stretching.
     */
    suspend fun initializeDefaults()
}
