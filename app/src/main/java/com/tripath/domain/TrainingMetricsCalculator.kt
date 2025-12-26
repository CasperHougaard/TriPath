package com.tripath.domain

import com.tripath.data.model.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import kotlin.math.pow
import java.time.LocalDate

/**
 * Performance metrics calculated using the Banister Impulse Response model.
 * 
 * @param ctl Chronic Training Load (Fitness) - 42-day exponentially weighted moving average
 * @param atl Acute Training Load (Fatigue) - 7-day exponentially weighted moving average
 * @param tsb Training Stress Balance (Form) - Difference between CTL and ATL
 */
data class PerformanceMetrics(
    val ctl: Double,  // Chronic Training Load (Fitness)
    val atl: Double,  // Acute Training Load (Fatigue)
    val tsb: Double   // Training Stress Balance (Form) = CTL - ATL
)

/**
 * Singleton object for centralized training metrics calculations.
 * Handles TSS (Training Stress Score) based on user profile and workout data.
 * Implements the Banister Impulse Response model for performance metrics.
 */
object TrainingMetricsCalculator {

    private const val DEFAULT_MAX_HR = 185
    private const val DEFAULT_FTP = 250
    private const val DEFAULT_SWIM_TSS_PER_HOUR = 60
    private const val DEFAULT_STRENGTH_TSS_PER_HOUR = 60

    /**
     * Calculate Training Stress Score (TSS).
     * 
     * @param workoutType The type of workout (RUN, BIKE, SWIM, STRENGTH, OTHER).
     * @param durationMin Duration of the workout in minutes.
     * @param avgHr Average heart rate during the workout (optional).
     * @param avgPower Average power during the workout (optional).
     * @param userProfile The current user profile for thresholds and metrics.
     * @return Calculated TSS as an integer.
     */
    fun calculateTSS(
        workoutType: WorkoutType,
        durationMin: Int,
        avgHr: Int?,
        avgPower: Int?,
        userProfile: UserProfile
    ): Int {
        val durationHours = durationMin / 60.0
        val durationSeconds = durationMin * 60.0

        return when (workoutType) {
            WorkoutType.BIKE -> {
                val ftp = userProfile.ftpBike ?: DEFAULT_FTP
                if (avgPower != null && ftp > 0) {
                    val intensityFactor = avgPower.toDouble() / ftp
                    (((durationSeconds * avgPower * intensityFactor) / (ftp * 3600.0)) * 100.0).toInt()
                } else if (avgHr != null) {
                    calculateHrTSS(durationMin, avgHr, userProfile.maxHeartRate ?: DEFAULT_MAX_HR)
                } else {
                    (durationHours * 40).toInt() // Low intensity fallback
                }
            }
            WorkoutType.RUN -> {
                if (avgHr != null) {
                    calculateHrTSS(durationMin, avgHr, userProfile.maxHeartRate ?: DEFAULT_MAX_HR)
                } else {
                    (durationHours * 50).toInt() // Moderate intensity fallback for running
                }
            }
            WorkoutType.SWIM -> {
                val defaultSwimTSS = userProfile.defaultSwimTSS ?: DEFAULT_SWIM_TSS_PER_HOUR
                (durationHours * defaultSwimTSS).toInt()
            }
            WorkoutType.STRENGTH -> {
                val defaultStrengthTSS = userProfile.defaultStrengthHeavyTSS ?: DEFAULT_STRENGTH_TSS_PER_HOUR
                (durationHours * defaultStrengthTSS).toInt()
            }
            WorkoutType.OTHER -> {
                if (avgHr != null) {
                    // If heart rate is present, calculate TSS based on HR (heart doesn't care if hiking or running)
                    calculateHrTSS(durationMin, avgHr, userProfile.maxHeartRate ?: DEFAULT_MAX_HR)
                } else {
                    // Low-intensity default for walking/hiking without HR data
                    (durationHours * 20).toInt() // 20 TSS/hour for walking/hiking
                }
            }
        }
    }

    /**
     * Estimate hrTSS based on average heart rate and Max HR.
     * Simplified formula: (durationHours) * (avgHr / maxHR)^2 * 100
     */
    private fun calculateHrTSS(durationMin: Int, avgHr: Int, maxHr: Int): Int {
        val durationHours = durationMin / 60.0
        if (maxHr <= 0) return (durationHours * 40).toInt()
        
        val hrRatio = avgHr.toDouble() / maxHr
        return (durationHours * hrRatio.pow(2) * 100).toInt()
    }

    // Banister Impulse Response Model Constants
    private const val CTL_TIME_CONSTANT = 42.0  // 42-day time constant for Chronic Training Load
    private const val ATL_TIME_CONSTANT = 7.0   // 7-day time constant for Acute Training Load

    /**
     * Aggregates daily TSS from workout logs.
     * Groups workouts by date and sums their computedTSS values.
     * 
     * @param logs List of workout logs to aggregate
     * @return Map of LocalDate to daily TSS (treats null computedTSS as 0)
     */
    private fun aggregateDailyTSS(logs: List<WorkoutLog>): Map<LocalDate, Int> {
        return logs
            .groupBy { it.date }
            .mapValues { (_, dayLogs) ->
                dayLogs.sumOf { it.computedTSS ?: 0 }
            }
    }

    /**
     * Calculate performance metrics (CTL, ATL, TSB) using the Banister Impulse Response model.
     * 
     * The Banister model uses Exponentially Weighted Moving Averages (EWMA):
     * - CTL (Chronic Training Load / Fitness): 42-day EWMA of daily TSS
     * - ATL (Acute Training Load / Fatigue): 7-day EWMA of daily TSS
     * - TSB (Training Stress Balance / Form): CTL - ATL
     * 
     * @param logs List of workout logs to calculate metrics from
     * @param targetDate The date for which to calculate performance metrics
     * @return PerformanceMetrics containing CTL, ATL, and TSB for the target date
     */
    fun calculatePerformanceMetrics(
        logs: List<WorkoutLog>,
        targetDate: LocalDate
    ): PerformanceMetrics {
        // Handle empty logs
        if (logs.isEmpty()) {
            return PerformanceMetrics(ctl = 0.0, atl = 0.0, tsb = 0.0)
        }

        // Filter logs to include only dates up to and including targetDate
        val relevantLogs = logs.filter { !it.date.isAfter(targetDate) }
        
        if (relevantLogs.isEmpty()) {
            return PerformanceMetrics(ctl = 0.0, atl = 0.0, tsb = 0.0)
        }

        // Aggregate daily TSS
        val dailyTSS = aggregateDailyTSS(relevantLogs)

        // Find the earliest workout date
        val startDate = relevantLogs.minOfOrNull { it.date }
            ?: return PerformanceMetrics(ctl = 0.0, atl = 0.0, tsb = 0.0)

        // Initialize CTL and ATL
        var ctl = 0.0
        var atl = 0.0

        // Iterate chronologically from start date to target date
        var currentDate = startDate
        while (!currentDate.isAfter(targetDate)) {
            // Get daily TSS for this date (0 if no workouts)
            val dayTSS = dailyTSS[currentDate]?.toDouble() ?: 0.0

            // Calculate CTL using EWMA: CTL_today = CTL_yesterday * (1 - 1/42) + TSS_today * (1/42)
            ctl = ctl * (1.0 - 1.0 / CTL_TIME_CONSTANT) + dayTSS * (1.0 / CTL_TIME_CONSTANT)

            // Calculate ATL using EWMA: ATL_today = ATL_yesterday * (1 - 1/7) + TSS_today * (1/7)
            atl = atl * (1.0 - 1.0 / ATL_TIME_CONSTANT) + dayTSS * (1.0 / ATL_TIME_CONSTANT)

            // Move to next day
            currentDate = currentDate.plusDays(1)
        }

        // Calculate TSB (Training Stress Balance / Form)
        val tsb = ctl - atl

        return PerformanceMetrics(ctl = ctl, atl = atl, tsb = tsb)
    }
}

