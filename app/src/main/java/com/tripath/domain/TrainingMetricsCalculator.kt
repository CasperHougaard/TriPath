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
            WorkoutType.WALK -> {
                if (avgHr != null) {
                    calculateHrTSS(durationMin, avgHr, userProfile.maxHeartRate ?: DEFAULT_MAX_HR)
                } else {
                    (durationHours * 30).toInt() // Moderate intensity default for walking
                }
            }
            WorkoutType.HIKE -> {
                if (avgHr != null) {
                    calculateHrTSS(durationMin, avgHr, userProfile.maxHeartRate ?: DEFAULT_MAX_HR)
                } else {
                    (durationHours * 40).toInt() // Higher intensity default for hiking
                }
            }
        }
    }

    fun calculateManualTss(type: WorkoutType, durationMinutes: Int, zone: Int): Int {
        val durationHours = durationMinutes / 60.0
        val tssPerHour = when (type) {
            WorkoutType.RUN      -> when (zone) { 1 -> 35.0; 2 -> 55.0; 3 -> 75.0; 4 -> 95.0; 5 -> 115.0; else -> 55.0 }
            WorkoutType.BIKE     -> when (zone) { 1 -> 30.0; 2 -> 50.0; 3 -> 70.0; 4 -> 90.0; 5 -> 110.0; else -> 50.0 }
            WorkoutType.SWIM     -> when (zone) { 1 -> 30.0; 2 -> 45.0; 3 -> 60.0; 4 -> 75.0; 5 -> 90.0;  else -> 45.0 }
            WorkoutType.STRENGTH -> when (zone) { 1 -> 25.0; 2 -> 40.0; 3 -> 55.0; 4 -> 65.0; 5 -> 75.0;  else -> 40.0 }
            else                 -> when (zone) { 1 -> 20.0; 2 -> 35.0; 3 -> 50.0; 4 -> 65.0; 5 -> 80.0;  else -> 35.0 }
        }
        return (durationHours * tssPerHour).toInt()
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

    /**
     * Computes a CTL/ATL/TSB time series across [seriesStart]..[seriesEnd] (inclusive).
     *
     * Daily TSS is taken from completed [logs] for dates up to and including [actualUntil].
     * For dates after [actualUntil] it is taken from [plannedTssByDate] to produce a forward
     * projection of Fitness (CTL) and Fatigue (ATL) — except that a completed log on a future
     * date (e.g. a workout manually marked completed ahead of time) takes precedence over the
     * plan for that same day, so it is never double-counted.
     *
     * The EWMA is seeded from the earliest available log so that values for dates up to
     * [actualUntil] are identical to [calculatePerformanceMetrics] for the same [logs].
     *
     * @param logs Completed workout logs (actuals; may include future-dated manual entries).
     * @param plannedTssByDate Planned daily TSS keyed by date (used for future dates with no completed log).
     * @param seriesStart First date to emit a data point for.
     * @param seriesEnd Last date to emit a data point for.
     * @param actualUntil The boundary between actuals and projection (typically today).
     * @return Ordered list of (date, metrics) from [seriesStart] to [seriesEnd].
     */
    fun calculatePerformanceSeries(
        logs: List<WorkoutLog>,
        plannedTssByDate: Map<LocalDate, Int>,
        seriesStart: LocalDate,
        seriesEnd: LocalDate,
        actualUntil: LocalDate
    ): List<Pair<LocalDate, PerformanceMetrics>> {
        if (seriesEnd.isBefore(seriesStart)) return emptyList()

        // Actual daily TSS from all completed workouts, including any future-dated manual entries.
        val actualDailyTSS = aggregateDailyTSS(logs)

        // Seed the EWMA from the earliest log so on-chart actuals match calculatePerformanceMetrics.
        val earliestLog = logs.minOfOrNull { it.date }
        val iterationStart = if (earliestLog != null && earliestLog.isBefore(seriesStart)) {
            earliestLog
        } else {
            seriesStart
        }

        var ctl = 0.0
        var atl = 0.0
        val series = mutableListOf<Pair<LocalDate, PerformanceMetrics>>()

        var currentDate = iterationStart
        while (!currentDate.isAfter(seriesEnd)) {
            val dayTSS = if (!currentDate.isAfter(actualUntil)) {
                // Actuals: completed workouts only.
                actualDailyTSS[currentDate]?.toDouble() ?: 0.0
            } else {
                // Projection: a completed log on this future day wins over the plan.
                (actualDailyTSS[currentDate] ?: plannedTssByDate[currentDate])?.toDouble() ?: 0.0
            }

            // EWMA: CTL uses the 42-day constant, ATL the 7-day constant.
            ctl = ctl * (1.0 - 1.0 / CTL_TIME_CONSTANT) + dayTSS * (1.0 / CTL_TIME_CONSTANT)
            atl = atl * (1.0 - 1.0 / ATL_TIME_CONSTANT) + dayTSS * (1.0 / ATL_TIME_CONSTANT)

            if (!currentDate.isBefore(seriesStart)) {
                series.add(currentDate to PerformanceMetrics(ctl = ctl, atl = atl, tsb = ctl - atl))
            }
            currentDate = currentDate.plusDays(1)
        }
        return series
    }

    /**
     * Calculates a safe maximum weekly TSS based on current fitness (CTL).
     * This represents a safe progressive overload (approx 15% bump over current 
     * daily load capacity extrapolated to a week).
     * 
     * @param currentCtl The current Chronic Training Load (Fitness)
     * @return Safe weekly TSS limit as an integer
     */
    fun calculateSafeWeeklyTSS(currentCtl: Double): Int {
        // Safe daily load capacity is approx Current CTL * 1.15
        val safeDailyLoad = if (currentCtl < 10) 40.0 else currentCtl * 1.15
        return (safeDailyLoad * 7).toInt()
    }
}

