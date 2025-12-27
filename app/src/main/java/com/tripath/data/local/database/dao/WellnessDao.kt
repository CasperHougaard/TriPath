package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for wellness-related entities (DailyWellnessLog and WellnessTaskDefinition).
 */
@Dao
interface WellnessDao {

    // DailyWellnessLog operations

    /**
     * Get a daily wellness log by date.
     */
    @Query("SELECT * FROM daily_wellness_logs WHERE date = :date")
    suspend fun getLogByDate(date: LocalDate): DailyWellnessLog?

    /**
     * Get a daily wellness log by date as Flow.
     */
    @Query("SELECT * FROM daily_wellness_logs WHERE date = :date")
    fun getLogByDateFlow(date: LocalDate): Flow<DailyWellnessLog?>

    /**
     * Get all daily wellness logs ordered by date (newest first).
     */
    @Query("SELECT * FROM daily_wellness_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<DailyWellnessLog>>

    /**
     * Get all daily wellness logs as a one-shot list (for backup).
     */
    @Query("SELECT * FROM daily_wellness_logs ORDER BY date DESC")
    suspend fun getAllLogsOnce(): List<DailyWellnessLog>

    /**
     * Get daily wellness logs within a date range.
     */
    @Query("SELECT * FROM daily_wellness_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getLogsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyWellnessLog>>

    /**
     * Get the most recent recorded weight before the given date.
     */
    @Query("SELECT morningWeight FROM daily_wellness_logs WHERE morningWeight IS NOT NULL AND date < :currentDate ORDER BY date DESC LIMIT 1")
    suspend fun getLastRecordedWeight(currentDate: LocalDate): Double?

    /**
     * Insert or update a daily wellness log.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DailyWellnessLog)

    /**
     * Update an existing daily wellness log.
     */
    @Update
    suspend fun updateLog(log: DailyWellnessLog)

    /**
     * Delete a daily wellness log.
     */
    @Delete
    suspend fun deleteLog(log: DailyWellnessLog)

    // WellnessTaskDefinition operations

    /**
     * Get all wellness task definitions ordered by id.
     */
    @Query("SELECT * FROM wellness_task_definitions ORDER BY id ASC")
    fun getAllTasks(): Flow<List<WellnessTaskDefinition>>

    /**
     * Get all wellness task definitions as a one-shot list (for backup).
     */
    @Query("SELECT * FROM wellness_task_definitions ORDER BY id ASC")
    suspend fun getAllTasksOnce(): List<WellnessTaskDefinition>

    /**
     * Get a wellness task definition by id.
     */
    @Query("SELECT * FROM wellness_task_definitions WHERE id = :id")
    suspend fun getTaskById(id: Long): WellnessTaskDefinition?

    /**
     * Insert a new wellness task definition.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WellnessTaskDefinition): Long

    /**
     * Insert multiple wellness task definitions.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<WellnessTaskDefinition>)

    /**
     * Update an existing wellness task definition.
     */
    @Update
    suspend fun updateTask(task: WellnessTaskDefinition)

    /**
     * Delete a wellness task definition.
     */
    @Delete
    suspend fun deleteTask(task: WellnessTaskDefinition)

    /**
     * Delete a wellness task definition by id.
     */
    @Query("DELETE FROM wellness_task_definitions WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
}
