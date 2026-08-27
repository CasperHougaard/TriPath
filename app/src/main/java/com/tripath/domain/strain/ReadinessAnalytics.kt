package com.tripath.domain.strain

import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.domain.health.EnergyAvailabilityBand
import com.tripath.domain.health.FuelAnalysis
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Turns the app's stored history into the inputs [ReadinessModel] wants.
 *
 * Kept separate from the model itself so the scoring stays a pure function of well-named inputs:
 * everything awkward — which sleep record counts as "last night", how a heart-rate-variability
 * baseline is built, where a week's sleep debt comes from — lives here, and the model stays
 * readable and trivially testable.
 */
object ReadinessAnalytics {

    /** Days of rMSSD averaged into the "recent" figure. A week smooths out night-to-night noise. */
    const val HRV_RECENT_DAYS = 7L

    /**
     * Days the personal baseline is built from. Long enough to be a stable reference for the
     * recent window to move against, short enough to track genuine seasonal change.
     */
    const val HRV_BASELINE_DAYS = 60L

    /** Minimum baseline readings before HRV is scored at all, rather than read off two nights. */
    const val HRV_MIN_BASELINE_READINGS = 14

    const val SLEEP_DEBT_DAYS = 7L

    fun buildInputs(
        strain: StrainState,
        tsb: Double?,
        sleep: List<SleepLog>,
        wellness: List<DailyWellnessLog>,
        dailyActivity: List<DailyActivityLog>,
        fuel: FuelAnalysis,
        sleepNeedMinutes: Int,
        today: LocalDate,
        weeklyLoadRampPct: Double? = null
    ): ReadinessInputs {
        val nights = sleep.filter { !it.isIgnored }
            .groupBy { it.date }
            .mapValues { (_, logs) -> logs.maxByOrNull { it.durationMinutes } ?: logs.first() }

        val hrvByDate = hrvByDate(wellness, dailyActivity)
        val latestWellness = wellness.filter { !it.date.isAfter(today) }.maxByOrNull { it.date }

        return ReadinessInputs(
            strain = strain,
            tsb = tsb,
            // "Last night" is the most recent night on or before today, not strictly yesterday —
            // a sync that has not run yet should fall back rather than report no sleep at all.
            sleepMinutesLastNight = nights.filterKeys { !it.isAfter(today) }
                .maxByOrNull { it.key }?.value?.durationMinutes,
            sleepDebtMinutes = sleepDebtMinutes(nights, sleepNeedMinutes, today),
            sleepNeedMinutes = sleepNeedMinutes,
            hrvRecent = rollingHrv(hrvByDate, today, HRV_RECENT_DAYS, minReadings = 2),
            hrvBaseline = rollingHrv(hrvByDate, today, HRV_BASELINE_DAYS, HRV_MIN_BASELINE_READINGS),
            energyBalanceKcal = recentEnergyBalance(fuel, today),
            energyAvailability = fuel.rollingEnergyAvailability.band,
            // Not scored — carried so the assessment can say what to eat, not only that fuelling is
            // off. Read by date rather than as `fuel.today` because this same builder is used for
            // past days when the readiness history is recomputed.
            fuelTarget = fuel.days.firstOrNull { it.date == today }?.target,
            soreness = latestWellness?.sorenessIndex,
            mood = latestWellness?.moodIndex,
            weeklyLoadRampPct = weeklyLoadRampPct
        )
    }

    /**
     * Heart-rate variability by date, preferring a manual entry over the watch's.
     *
     * A figure the athlete typed in is a deliberate act and should win over an automatic reading
     * for the same day — the same precedence the wellness log uses everywhere else.
     */
    internal fun hrvByDate(
        wellness: List<DailyWellnessLog>,
        dailyActivity: List<DailyActivityLog>
    ): Map<LocalDate, Double> {
        val fromWatch = dailyActivity.mapNotNull { log -> log.hrvRmssd?.let { log.date to it } }.toMap()
        val manual = wellness.mapNotNull { log -> log.hrvRmssd?.let { log.date to it } }.toMap()
        return fromWatch + manual
    }

    /**
     * Mean rMSSD over the trailing [days], or null when there are too few readings to mean anything.
     *
     * Returning null rather than a thin average matters: [ReadinessModel] drops a missing input and
     * renormalises, which is far better than scoring a fifteen percent weight off two nights.
     */
    internal fun rollingHrv(
        hrvByDate: Map<LocalDate, Double>,
        today: LocalDate,
        days: Long,
        minReadings: Int
    ): Double? {
        val from = today.minusDays(days)
        val readings = hrvByDate
            .filterKeys { !it.isBefore(from) && !it.isAfter(today) }
            .values
        return if (readings.size < minReadings) null else readings.average()
    }

    /**
     * Minutes short of the athlete's need across the last week, floored at zero.
     *
     * Only nights that were actually recorded count. Treating an unlogged night as zero sleep would
     * manufacture an eight-hour debt out of a watch that ran flat.
     */
    internal fun sleepDebtMinutes(
        nights: Map<LocalDate, SleepLog>,
        needMinutes: Int,
        today: LocalDate
    ): Int? {
        val from = today.minusDays(SLEEP_DEBT_DAYS - 1)
        val recorded = nights.filterKeys { !it.isBefore(from) && !it.isAfter(today) }.values
        if (recorded.isEmpty()) return null
        val debt = recorded.sumOf { (needMinutes - it.durationMinutes).coerceAtLeast(0) }
        return debt
    }

    /** Minimum logged days in the week before the mean balance describes anything. */
    const val MIN_DAYS_FOR_BALANCE = 3

    /**
     * Mean daily energy balance over the last week, or null when too little was logged for the
     * average to describe anything.
     *
     * Unlogged days are skipped rather than counted as zero intake — averaging in a forgotten
     * dinner would manufacture a deficit and dock readiness for it.
     */
    internal fun recentEnergyBalance(fuel: FuelAnalysis, today: LocalDate): Double? {
        val from = today.minusDays(6)
        val balances = fuel.days
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .mapNotNull { it.balanceKcal }
        return if (balances.size < MIN_DAYS_FOR_BALANCE) null else balances.average()
    }

    /** Weekly load ramp, purely descriptive. See [ReadinessAssessment.weeklyLoadRampPct]. */
    fun weeklyRamp(dailyTss: Map<LocalDate, Int>, today: LocalDate): Double? {
        fun sum(from: LocalDate, to: LocalDate) = dailyTss
            .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
            .values.sum()

        val thisWeek = sum(today.minusDays(6), today)
        val lastWeek = sum(today.minusDays(13), today.minusDays(7))
        return ReadinessModel.weeklyRampPct(thisWeek, lastWeek)
    }

    /** Formats a readiness score for the compact places that only have room for a number. */
    fun shortSummary(assessment: ReadinessAssessment): String =
        "${assessment.score} · ${assessment.band.label}"
}
