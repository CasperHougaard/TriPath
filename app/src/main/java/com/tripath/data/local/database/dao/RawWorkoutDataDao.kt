package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.RawWorkoutData

/**
 * Data Access Object for RawWorkoutData entity.
 * Handles storage of raw samples imported from Health Connect.
 */
@Dao
interface RawWorkoutDataDao {

    /**
     * Get all raw workout data ordered by start time (newest first).
     */
    @Query("SELECT * FROM raw_workout_data ORDER BY startTimeMillis DESC")
    suspend fun getAll(): List<RawWorkoutData>

    /**
     * Get a raw workout data by its Health Connect ID.
     */
    @Query("SELECT * FROM raw_workout_data WHERE connectId = :connectId")
    suspend fun getByConnectId(connectId: String): RawWorkoutData?

    /**
     * Check if raw data exists for a specific Health Connect ID.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM raw_workout_data WHERE connectId = :connectId)")
    suspend fun exists(connectId: String): Boolean

    /**
     * Get all connectIds that are missing route data.
     */
    @Query("SELECT connectId FROM raw_workout_data WHERE routeJson IS NULL")
    suspend fun getConnectIdsMissingRoute(): List<String>

    /**
     * Update route data for an existing workout.
     */
    @Query("UPDATE raw_workout_data SET routeJson = :routeJson WHERE connectId = :connectId")
    suspend fun updateRoute(connectId: String, routeJson: String)

    /**
     * Insert raw workout data. 
     * Uses IGNORE because once raw data is imported, it shouldn't change.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rawData: RawWorkoutData)

    /**
     * Delete all raw workout data.
     */
    @Query("DELETE FROM raw_workout_data")
    suspend fun deleteAll()
}

