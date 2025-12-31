package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.SleepLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for SleepLog entity.
 * Provides all database operations for sleep sessions synced from Health Connect.
 */
@Dao
interface SleepLogDao {

    /**
     * Get all sleep logs ordered by date (newest first).
     */
    @Query("SELECT * FROM sleep_logs ORDER BY date DESC")
    fun getAll(): Flow<List<SleepLog>>

    /**
     * Get all sleep logs as a one-shot list (for backup).
     */
    @Query("SELECT * FROM sleep_logs ORDER BY date DESC")
    suspend fun getAllOnce(): List<SleepLog>

    /**
     * Get a sleep log by its Health Connect ID.
     */
    @Query("SELECT * FROM sleep_logs WHERE connectId = :connectId")
    suspend fun getByConnectId(connectId: String): SleepLog?

    /**
     * Get sleep logs within a date range.
     */
    @Query("SELECT * FROM sleep_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<SleepLog>>


    /**
     * Get sleep log for a specific date.
     */
    @Query("SELECT * FROM sleep_logs WHERE date = :date")
    suspend fun getByDate(date: Long): SleepLog?

    /**
     * Get all sleep logs with null sleepScore for backfill processing.
     */
    @Query("SELECT * FROM sleep_logs WHERE sleepScore IS NULL")
    suspend fun getLogsWithoutScore(): List<SleepLog>

    /**
     * Update sleep score for a specific sleep log.
     */
    @Query("UPDATE sleep_logs SET sleepScore = :score WHERE connectId = :connectId")
    suspend fun updateSleepScore(connectId: String, score: Int)

    /**
     * Check if a sleep log exists for the given Health Connect ID.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM sleep_logs WHERE connectId = :connectId)")
    suspend fun exists(connectId: String): Boolean

    /**
     * Insert a new sleep log (replaces if already exists).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SleepLog)

    /**
     * Insert multiple sleep logs.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SleepLog>)

    /**
     * Delete all sleep logs.
     */
    @Query("DELETE FROM sleep_logs")
    suspend fun deleteAll()

    /**
     * Get count of sleep logs.
     */
    @Query("SELECT COUNT(*) FROM sleep_logs")
    suspend fun getCount(): Int
}

