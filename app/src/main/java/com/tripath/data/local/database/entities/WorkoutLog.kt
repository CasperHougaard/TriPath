package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tripath.data.model.WorkoutType
import java.time.LocalDate

/**
 * Represents a completed workout synced from Health Connect.
 * WorkoutLogs are imported from external sources (e.g., Garmin via Health Connect).
 */
@Entity(tableName = "workout_logs")
data class WorkoutLog(
    /** Unique identifier from Health Connect */
    @PrimaryKey
    val connectId: String,
    
    /** The date the workout was completed */
    val date: LocalDate,
    
    /** Type of workout: RUN, BIKE, SWIM, or STRENGTH */
    val type: WorkoutType,
    
    /** Actual duration in minutes */
    val durationMinutes: Int,
    
    /** Average heart rate during the workout (optional) */
    val avgHeartRate: Int? = null,
    
    /** Calories burned during the workout (optional) */
    val calories: Int? = null,
    
    /** Computed Training Stress Score based on workout data (optional) */
    val computedTSS: Int? = null
)

