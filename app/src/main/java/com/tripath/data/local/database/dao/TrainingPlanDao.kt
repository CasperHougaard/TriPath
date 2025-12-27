package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TrainingPlan entity.
 * Provides all database operations for planned workouts.
 */
@Dao
interface TrainingPlanDao {

    /**
     * Get all training plans ordered by date.
     */
    @Query("SELECT * FROM training_plans ORDER BY date ASC")
    fun getAll(): Flow<List<TrainingPlan>>

    /**
     * Get all training plans as a one-shot list (for backup).
     */
    @Query("SELECT * FROM training_plans ORDER BY date ASC")
    suspend fun getAllOnce(): List<TrainingPlan>

    /**
     * Get a training plan by its ID.
     */
    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun getById(id: String): TrainingPlan?

    /**
     * Get training plans within a date range.
     */
    @Query("SELECT * FROM training_plans WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<TrainingPlan>>

    /**
     * Get training plans within a date range (as a list).
     */
    @Query("SELECT * FROM training_plans WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getByDateRangeOnce(startDate: Long, endDate: Long): List<TrainingPlan>

    /**
     * Get training plans by workout type.
     */
    @Query("SELECT * FROM training_plans WHERE type = :type ORDER BY date ASC")
    fun getByType(type: WorkoutType): Flow<List<TrainingPlan>>

    /**
     * Get training plans for a specific date.
     */
    @Query("SELECT * FROM training_plans WHERE date = :date ORDER BY id ASC")
    fun getByDate(date: Long): Flow<List<TrainingPlan>>

    /**
     * Insert a new training plan.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: TrainingPlan)

    /**
     * Insert multiple training plans.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<TrainingPlan>)

    /**
     * Update an existing training plan.
     */
    @Update
    suspend fun update(plan: TrainingPlan)

    /**
     * Delete a training plan.
     */
    @Delete
    suspend fun delete(plan: TrainingPlan)

    /**
     * Delete a training plan by ID.
     */
    @Query("DELETE FROM training_plans WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete all training plans.
     */
    @Query("DELETE FROM training_plans")
    suspend fun deleteAll()

    /**
     * Delete training plans within a date range.
     */
    @Query("DELETE FROM training_plans WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteByDateRange(startDate: Long, endDate: Long)

    /**
     * Get count of training plans.
     */
    @Query("SELECT COUNT(*) FROM training_plans")
    suspend fun getCount(): Int
}

