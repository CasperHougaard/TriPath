package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import java.time.LocalDate
import java.util.UUID

/**
 * Represents a planned future workout.
 * Training plans are created by the user to schedule their training.
 */
@Entity(tableName = "training_plans")
data class TrainingPlan(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    /** The scheduled date for the workout */
    val date: LocalDate,
    
    /** Type of workout: RUN, BIKE, SWIM, STRENGTH, or OTHER */
    val type: WorkoutType,
    
    /** Optional sub-type for more specific workout description (e.g., "Tempo Run", "Hill Repeats") */
    val subType: String? = null,
    
    /** Planned duration in minutes */
    val durationMinutes: Int,
    
    /** Planned Training Stress Score */
    val plannedTSS: Int,
    
    /** 
     * Strength training focus area. 
     * Only applicable when type is STRENGTH, null otherwise.
     */
    val strengthFocus: StrengthFocus? = null,
    
    /** 
     * Strength training intensity. 
     * Only applicable when type is STRENGTH, null otherwise.
     */
    val intensity: Intensity? = null
)

