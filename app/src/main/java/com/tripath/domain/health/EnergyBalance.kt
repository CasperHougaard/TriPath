package com.tripath.domain.health

import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType

/**
 * Estimates daily energy expenditure so intake can be compared against a *load-adjusted*
 * requirement — the basis for the "am I eating enough for my training?" view.
 *
 * Model: Total Daily Energy Expenditure (TDEE) = resting baseline + training burn.
 *  - Resting baseline = BMR (Mifflin–St Jeor via [HealthReference.basalMetabolicRate]) ×
 *    [BASELINE_ACTIVITY_FACTOR] (1.2, the standard sedentary multiplier for non-exercise daily
 *    living). We deliberately do NOT reuse [HealthReference.maintenanceCalories]'s 1.55 factor
 *    here, because that already bakes in an assumed training load — adding measured workout burn
 *    on top of it would double-count the exercise.
 *  - Training burn = the sum of each workout's *active* calories. Uses [WorkoutLog.calories]
 *    (active kcal recorded by Health Connect) when present, otherwise a MET-based estimate from
 *    the session duration and the athlete's body weight.
 *
 * All figures are approximations for trend/awareness, not medical or lab-grade values.
 */
object EnergyBalanceCalculator {

    /** Sedentary multiplier applied to BMR for the non-exercise daily baseline. */
    const val BASELINE_ACTIVITY_FACTOR = 1.2

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

    /** Resting baseline expenditure (kcal/day) = BMR × sedentary factor. Null if BMR is unknown. */
    fun restingBaseline(bmr: Double?): Double? = bmr?.let { it * BASELINE_ACTIVITY_FACTOR }

    /**
     * Total daily energy expenditure = resting baseline + summed training burn for the day.
     * Returns null when the resting baseline can't be computed (incomplete demographics/weight).
     */
    fun dailyExpenditure(bmr: Double?, trainingBurnKcal: Double): Double? =
        restingBaseline(bmr)?.let { it + trainingBurnKcal }
}
