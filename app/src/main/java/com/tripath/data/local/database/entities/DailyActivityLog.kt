package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One calendar day of whole-day activity from Health Connect, as opposed to per-session data.
 *
 * This exists for the non-exercise half of energy expenditure. Until now the app multiplied resting
 * rate by a fixed 1.2 for every day alike, which cannot tell a desk day from one spent on your feet
 * — a difference worth several hundred kilocalories.
 *
 * ## Steps are split
 * [workoutSteps] are the steps already inside a logged session. Non-exercise steps
 * ([nonExerciseSteps]) are what drives the NEAT multiplier, because the energy of the workout steps
 * is already counted as exercise. Storing both rather than the difference keeps the subtraction
 * auditable when the numbers look wrong.
 *
 * ## Total calories is a cross-check, not an input
 * [totalCaloriesKcal] from a watch already includes basal *and* active energy. Adding it to the
 * app's own resting + NEAT + exercise model would double-count the whole day, so it is stored to be
 * *compared* against that model and never summed into it.
 */
@Entity(tableName = "daily_activity_logs")
data class DailyActivityLog(
    @PrimaryKey
    val date: LocalDate,

    /** Total steps for the day across all sources. */
    val steps: Int? = null,

    /** Steps that fell inside a logged workout session. */
    val workoutSteps: Int? = null,

    /** Active calories for the day as reported by Health Connect. */
    val activeCaloriesKcal: Double? = null,

    /** Total (basal + active) calories as reported by the watch. Diagnostic only — see the KDoc. */
    val totalCaloriesKcal: Double? = null,

    /** Overnight heart-rate variability (RMSSD, ms), when the watch records it. */
    val hrvRmssd: Double? = null,

    val importedAt: Long = System.currentTimeMillis()
) {
    /**
     * Steps outside logged training — the input to
     * [com.tripath.domain.health.MetabolicModel.neatFactor]. Null when the day has no step data at
     * all, so the caller falls back to the profile's activity level rather than reading a missing
     * day as a motionless one.
     */
    val nonExerciseSteps: Int?
        get() = steps?.let { (it - (workoutSteps ?: 0)).coerceAtLeast(0) }
}
