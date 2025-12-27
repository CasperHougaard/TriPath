package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores raw Health Connect data for permanent storage and reprocessing.
 * This allows the app to recalculate statistics (TSS, zones) even after
 * Health Connect has cleared its local history (usually after 30 days).
 */
@Entity(tableName = "raw_workout_data")
data class RawWorkoutData(
    /** Unique identifier from Health Connect session metadata.id */
    @PrimaryKey
    val connectId: String,
    
    /** Original raw exercise type from Health Connect (ExerciseSessionRecord.exerciseType) */
    val rawExerciseType: Int,
    
    /** Start time of the session in epoch milliseconds */
    val startTimeMillis: Long,
    
    /** End time of the session in epoch milliseconds */
    val endTimeMillis: Long,
    
    /** 
     * JSON serialized list of HeartRateSample. 
     * Format: [{"t": 123456789, "bpm": 145}, ...]
     */
    val hrSamplesJson: String? = null,
    
    /** 
     * JSON serialized list of PowerSample.
     * Format: [{"t": 123456789, "watts": 220}, ...]
     */
    val powerSamplesJson: String? = null,
    
    /** Total active calories burned as reported by Health Connect */
    val rawCalories: Int? = null,
    
    /** Total distance in meters as reported by Health Connect */
    val rawDistanceMeters: Double? = null,
    
    /** Total steps as reported by Health Connect (for walking/running) */
    val rawSteps: Int? = null,
    
    /** 
     * JSON serialized list of GPS route points (ExerciseRoute).
     * Format: [{"lat": 55.123, "lon": 12.456, "alt": 10.5, "t": 123456789}, ...]
     */
    val routeJson: String? = null,
    
    /** Timestamp when this record was imported into TriPath database */
    val importedAt: Long = System.currentTimeMillis()
)

