package com.tripath.domain

import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TaskTriggerType
import com.tripath.data.model.WorkoutType

/**
 * Data class representing nutrition targets for a given day.
 */
data class NutritionTargets(
    val proteinGrams: Double,
    val fatGrams: Double,
    val carbGrams: Double
)

/**
 * Singleton object for recovery-related calculations and logic.
 * Handles nutrition calculation, task generation, and coach advice.
 */
object RecoveryEngine {

    /**
     * Calculate daily nutrition targets based on body weight and training stress.
     * 
     * @param weightKg Body weight in kilograms
     * @param dailyTss Total Training Stress Score for the day
     * @return NutritionTargets with protein, fat, and carb targets in grams
     * 
     * Nutrition rules:
     * - Protein: 2g/kg (consistent regardless of training load)
     * - Fat: 1g/kg (consistent regardless of training load)
     * - Carbs: Variable based on TSS:
     *   - <50 TSS: 3g/kg
     *   - 50-100 TSS: 5g/kg
     *   - >100 TSS: 7g/kg
     */
    fun calculateNutrition(weightKg: Double, dailyTss: Int): NutritionTargets {
        val proteinGrams = weightKg * 2.0
        val fatGrams = weightKg * 1.0
        
        val carbGrams = when {
            dailyTss < 50 -> weightKg * 3.0
            dailyTss <= 100 -> weightKg * 5.0
            else -> weightKg * 7.0
        }
        
        return NutritionTargets(
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            carbGrams = carbGrams
        )
    }

    /**
     * Get relevant wellness tasks based on workout logs and task definitions.
     * 
     * @param logs List of workout logs to analyze
     * @param allTasks All available task definitions
     * @return Filtered list of relevant tasks based on triggers
     * 
     * Logic:
     * - Always include tasks with type DAILY
     * - Include TRIGGER_STRENGTH if logs contain any STRENGTH type workouts
     * - Include TRIGGER_LONG_DURATION if total duration > threshold
     * - Include TRIGGER_HIGH_TSS if total TSS > threshold
     */
    fun getRelevantTasks(
        logs: List<WorkoutLog>,
        allTasks: List<WellnessTaskDefinition>
    ): List<WellnessTaskDefinition> {
        val relevantTasks = mutableListOf<WellnessTaskDefinition>()
        
        // Always include DAILY tasks
        relevantTasks.addAll(allTasks.filter { it.type == TaskTriggerType.DAILY })
        
        // Check for STRENGTH trigger
        val hasStrengthWorkout = logs.any { it.type == WorkoutType.STRENGTH }
        if (hasStrengthWorkout) {
            relevantTasks.addAll(
                allTasks.filter { it.type == TaskTriggerType.TRIGGER_STRENGTH }
            )
        }
        
        // Calculate total duration and TSS for trigger thresholds
        val totalDurationMinutes = logs.sumOf { it.durationMinutes }
        val totalTss = logs.sumOf { it.computedTSS ?: 0 }
        
        // Check for LONG_DURATION trigger
        val longDurationTasks = allTasks.filter { 
            it.type == TaskTriggerType.TRIGGER_LONG_DURATION 
        }
        relevantTasks.addAll(
            longDurationTasks.filter { task ->
                val threshold = task.triggerThreshold ?: Int.MAX_VALUE
                totalDurationMinutes > threshold
            }
        )
        
        // Check for HIGH_TSS trigger
        val highTssTasks = allTasks.filter { 
            it.type == TaskTriggerType.TRIGGER_HIGH_TSS 
        }
        relevantTasks.addAll(
            highTssTasks.filter { task ->
                val threshold = task.triggerThreshold ?: Int.MAX_VALUE
                totalTss > threshold
            }
        )
        
        return relevantTasks.distinct()
    }

    /**
     * Get coach advice based on wellness log data.
     * 
     * @param log Daily wellness log entry
     * @return Coach advice string, or empty string if no advice needed
     * 
     * Logic:
     * - If allergy severity >= MODERATE, warn about biological cost and fatigue
     */
    fun getCoachAdvice(log: DailyWellnessLog): String {
        val advice = mutableListOf<String>()
        
        log.allergySeverity?.let { severity ->
            if (severity >= AllergySeverity.MODERATE) {
                advice.add(
                    "⚠️ Elevated allergy levels detected. Allergies increase biological cost " +
                    "and can contribute to fatigue. Consider reducing training intensity or volume " +
                    "until symptoms improve."
                )
            }
        }
        
        return advice.joinToString(separator = "\n\n")
    }
}
