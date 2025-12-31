package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.flow.Flow

/**
 * Data class to hold the result of average weekly TSS per workout type query.
 */
data class WorkoutTypeTssAverage(
    val type: WorkoutType,
    val averageWeeklyTss: Int
)

/**
 * Data Access Object for WorkoutLog entity.
 * Provides all database operations for completed workouts synced from Health Connect.
 */
@Dao
interface WorkoutLogDao {

    /**
     * Get all workout logs ordered by date (newest first).
     */
    @Query("SELECT * FROM workout_logs ORDER BY date DESC")
    fun getAll(): Flow<List<WorkoutLog>>

    /**
     * Get all workout logs as a one-shot list (for backup).
     */
    @Query("SELECT * FROM workout_logs ORDER BY date DESC")
    suspend fun getAllOnce(): List<WorkoutLog>

    /**
     * Get a workout log by its Health Connect ID.
     */
    @Query("SELECT * FROM workout_logs WHERE connectId = :connectId")
    suspend fun getByConnectId(connectId: String): WorkoutLog?

    /**
     * Get workout logs within a date range.
     */
    @Query("SELECT * FROM workout_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<WorkoutLog>>

    /**
     * Get workout logs by workout type.
     */
    @Query("SELECT * FROM workout_logs WHERE type = :type ORDER BY date DESC")
    fun getByType(type: WorkoutType): Flow<List<WorkoutLog>>

    /**
     * Get workout logs for a specific date.
     */
    @Query("SELECT * FROM workout_logs WHERE date = :date ORDER BY connectId ASC")
    fun getByDate(date: Long): Flow<List<WorkoutLog>>

    /**
     * Insert a new workout log (replaces if already exists - for Health Connect sync).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WorkoutLog)

    /**
     * Insert multiple workout logs.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<WorkoutLog>)

    /**
     * Delete a workout log.
     */
    @Delete
    suspend fun delete(log: WorkoutLog)

    /**
     * Delete a workout log by its Health Connect ID.
     */
    @Query("DELETE FROM workout_logs WHERE connectId = :connectId")
    suspend fun deleteByConnectId(connectId: String)

    /**
     * Delete all workout logs.
     */
    @Query("DELETE FROM workout_logs")
    suspend fun deleteAll()

    /**
     * Get count of workout logs.
     */
    @Query("SELECT COUNT(*) FROM workout_logs")
    suspend fun getCount(): Int

    /**
     * Check if a workout with the given Health Connect ID exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM workout_logs WHERE connectId = :connectId)")
    suspend fun exists(connectId: String): Boolean

    /**
     * Get average weekly TSS per workout type over a date range.
     * Calculates the sum of TSS for each workout type and divides by 4 to get weekly average.
     * 
     * @param startDate Start date (epoch days)
     * @param endDate End date (epoch days)
     * @return List of WorkoutTypeTssAverage containing type and average weekly TSS
     */
    @Query("""
        SELECT type, (SUM(computedTSS) / 4) as averageWeeklyTss
        FROM workout_logs
        WHERE date >= :startDate AND date <= :endDate 
          AND computedTSS IS NOT NULL
        GROUP BY type
    """)
    suspend fun getAverageWeeklyTssPerType(startDate: Long, endDate: Long): List<WorkoutTypeTssAverage>
}

