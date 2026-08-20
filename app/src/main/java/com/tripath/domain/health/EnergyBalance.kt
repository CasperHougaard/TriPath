package com.tripath.domain.health

import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType

/**
 * Estimates the energy cost of individual workouts — the "training burn" half of expenditure.
 *
 * Training burn is the sum of each workout's *active* calories: [WorkoutLog.calories] (recorded by
 * Health Connect) when present, otherwise a MET-based estimate from session duration and body
 * weight. Using the active portion only (MET − 1) means the result can be added to a resting
 * baseline without counting rest twice.
 *
 * **The rest of the expenditure model lives in [MetabolicModel].** This object used to own the
 * whole thing — resting rate × a fixed 1.2 multiplier, plus this burn — but that model had no
 * thermic effect of food, no way to tell a desk day from an active one, and no correction against
 * what the scale actually did. [MetabolicModel] and [AdaptiveExpenditure] replaced it; this is the
 * piece that survived unchanged because it was already right.
 *
 * All figures are approximations for trend/awareness, not medical or lab-grade values.
 */
object EnergyBalanceCalculator {

    /**
     * Approximate MET (metabolic equivalent of task) per workout type, used only as a fallback
     * when a session has no recorded active calories. Moderate-effort values for a mixed session.
     */
    private fun metFor(type: WorkoutType): Double = when (type) {
        WorkoutType.RUN -> 9.8
        WorkoutType.BIKE -> 8.0
        WorkoutType.SWIM -> 7.0
        WorkoutType.STRENGTH -> 5.0
        WorkoutType.WALK -> 3.5
        WorkoutType.HIKE -> 6.0
        WorkoutType.OTHER -> 6.0
    }

    /**
     * Estimated *active* calories (above rest) for one workout when [WorkoutLog.calories] is
     * missing. Uses the active portion (MET − 1) so the result can be added to the resting
     * baseline without double-counting rest. Null when body weight is unknown.
     */
    fun estimateActiveCalories(type: WorkoutType, durationMinutes: Int, weightKg: Double?): Double? {
        val w = weightKg ?: return null
        if (durationMinutes <= 0) return 0.0
        val hours = durationMinutes / 60.0
        return (metFor(type) - 1.0).coerceAtLeast(0.0) * w * hours
    }

    /**
     * Active calories for a workout: the recorded value if present, otherwise a MET estimate.
     * Null only when neither a recorded value nor a weight-based estimate is available.
     */
    fun workoutActiveCalories(log: WorkoutLog, weightKg: Double?): Double? =
        log.calories?.toDouble() ?: estimateActiveCalories(log.type, log.durationMinutes, weightKg)
}
