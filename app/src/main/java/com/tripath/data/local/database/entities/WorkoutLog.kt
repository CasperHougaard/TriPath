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
    
    /** Type of workout: RUN, BIKE, SWIM, STRENGTH, or OTHER */
    val type: WorkoutType,
    
    /** Actual duration in minutes */
    val durationMinutes: Int,
    
    /** Average heart rate during the workout (optional) */
    val avgHeartRate: Int? = null,
    
    /** Calories burned during the workout (optional) */
    val calories: Int? = null,
    
    /** Computed Training Stress Score based on workout data (optional) */
    val computedTSS: Int? = null,

    /** Total distance in meters (optional) */
    val distanceMeters: Double? = null,

    /** Average speed in km/h (optional) */
    val avgSpeedKmh: Double? = null,

    /** Average power in Watts (optional) */
    val avgPowerWatts: Int? = null,

    /** Total steps (optional) */
    val steps: Int? = null,

    /** Time distribution in seconds across HR zones (optional) */
    val hrZoneDistribution: Map<String, Int>? = null,

    /** Time distribution in seconds across power zones (optional) */
    val powerZoneDistribution: Map<String, Int>? = null
)

