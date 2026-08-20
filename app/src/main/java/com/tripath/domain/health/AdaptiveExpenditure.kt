package com.tripath.domain.health

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * One day's inputs to the adaptive model. [intakeKcal] is null on days that were not logged, which
 * is different from a logged zero and must stay distinguishable.
 */
data class EnergyDay(
    val date: LocalDate,
    val intakeKcal: Double?,
    val weightKg: Double?,
    /** Modelled non-TEF expenditure for the day: `rmr × neatFactor + exercise`. */
    val formulaNonTefKcal: Double?
)

/**
 * The result of blending the modelled expenditure with what the scale and the food log imply.
 *
 * [kcal] is the figure everything downstream should use. The components are exposed so the UI can
 * say where the number came from and how settled it is, rather than presenting a blend as fact.
 */
data class NonTefEstimate(
    val kcal: Double?,
    val formulaKcal: Double?,
    val observedKcal: Double?,
    /** Weight given to the observation, 0..[AdaptiveExpenditure.MAX_OBSERVED_WEIGHT]. */
    val observedWeight: Double,
    val confidence: EstimateConfidence,
    val clampedBy: ClampReason? = null,
    val daysOfData: Int = 0
) {
    companion object {
        val UNAVAILABLE = NonTefEstimate(null, null, null, 0.0, EstimateConfidence.NONE)
    }
}

/** Why an adaptive estimate was held back from where the observation alone would have put it. */
enum class ClampReason {
    /** The daily movement budget ran out — the estimate is still travelling toward the observation. */
    DAILY_RATE,

    /** The cumulative offset from the formula hit its ceiling. */
    TOTAL_OFFSET
}

/**
 * Corrects the modelled expenditure against what actually happened to body mass.
 *
 * ## Why this exists
 * A prediction equation plus an activity multiplier is a starting point with a 10–15% error band,
 * and self-reported intake has its own. The energy-balance identity — intake minus the change in
 * stored energy equals expenditure — turns the athlete's own logging into a measurement that
 * corrects both at once. It is well validated at group level (accurate to ~2%), but its *precision*
 * depends heavily on how long the window is and how good the body-composition data is.
 *
 * ## Why it is deliberately slow
 * Over a short window the scale is mostly water, glycogen and sodium. A hard leg session followed
 * by a rice-heavy refeed can move body mass by more than a kilo in a day, which naively reads as a
 * ~7,700 kcal error. Three defences, in order of importance:
 *
 *  1. **A robust slope.** The trend comes from a Theil–Sen fit over a smoothed series, so a handful
 *     of outlier days cannot set the direction — where a first-vs-last difference is *entirely*
 *     determined by two days, and specifically by whichever two happen to be at the ends.
 *  2. **A weight ladder.** The observation barely counts at 28 days and only approaches
 *     [MAX_OBSERVED_WEIGHT] at 42+ days of genuinely dense logging.
 *  3. **A daily movement budget.** See [MAX_DAILY_ADAPTATION_KCAL].
 *
 * ## Non-TEF, not TDEE
 * The observation carries the thermic effect of whatever was eaten, so it is stripped before
 * blending. Everything here is in the same non-TEF currency as
 * [MetabolicModel.nonTefExpenditure], which is what lets [MetabolicModel.targetIntake] consume it
 * directly. See that class for why TEF cannot be a fixed addend.
 */
object AdaptiveExpenditure {

    /**
     * Energy per kilogram of body mass. The classic mixed-tissue figure; a real body loses a blend
     * of fat (~9,400 kcal/kg) and lean tissue (~1,800 kcal/kg), so this is an approximation whose
     * error is one more reason the observation is never trusted alone.
     */
    const val KCAL_PER_KG = 7700.0

    /** Ceiling on how much the observation may override the formula, even with perfect data. */
    const val MAX_OBSERVED_WEIGHT = 0.70

    /**
     * How far the blended estimate may travel per **calendar day**.
     *
     * Deliberately not a per-call clamp. Opening the app, a Health Connect sync, a nutrition edit
     * and a body-scan sync inside one minute are four recomputations of the same day, and a
     * per-call budget would let them compound into a 300 kcal jump. The budget is
     * `MAX_DAILY_ADAPTATION_KCAL × days elapsed`, applied while replaying one accepted estimate per
     * day — so the answer depends only on the data, never on how many times it was asked for.
     */
    const val MAX_DAILY_ADAPTATION_KCAL = 75.0

    /** Cumulative ceiling on how far the blend may sit from the formula, as a fraction of it. */
    const val MAX_TOTAL_OFFSET_FRACTION = 0.20

    /** Smoothing constant for the weight series. ~7-day effective span. */
    private const val WEIGHT_EWMA_ALPHA = 0.25

    private const val MIN_WINDOW_DAYS = 28

    /**
     * Trailing days each day's observation is fitted over. Long enough for the top rung of the
     * ladder, short enough that an old training block stops influencing today's estimate.
     */
    private const val ANALYSIS_WINDOW_DAYS = 56L

    /**
     * How far back the replay runs. Bounded for cost — a Theil–Sen fit is quadratic in window size,
     * so replaying from the first ever log would be quartic in total history.
     *
     * Truncating is safe because the replay is a contraction: from any starting value the clamp
     * closes the gap by [MAX_DAILY_ADAPTATION_KCAL] per day, and the gap can never exceed
     * [MAX_TOTAL_OFFSET_FRACTION] of the formula (~500 kcal on a 2,500 kcal baseline), so any seed
     * is forgotten within ~7 days. 120 is an order of magnitude more than that.
     */
    private const val REPLAY_DAYS = 120L

    /**
     * The blend for [asOf], produced by replaying one accepted value per calendar day so the daily
     * budget is applied consistently and the answer is identical however many times it is asked for.
     */
    fun estimate(days: List<EnergyDay>, asOf: LocalDate): NonTefEstimate {
        if (days.isEmpty()) return NonTefEstimate.UNAVAILABLE
        val ordered = days.filter { !it.date.isAfter(asOf) }.sortedBy { it.date }
        if (ordered.isEmpty()) return NonTefEstimate.UNAVAILABLE

        val formulaToday = ordered.lastOrNull { it.formulaNonTefKcal != null }?.formulaNonTefKcal
            ?: return NonTefEstimate.UNAVAILABLE

        var accepted: Double? = null
        var acceptedOn: LocalDate? = null
        var lastTarget = NonTefEstimate.UNAVAILABLE

        val replayStart = maxOf(ordered.first().date, asOf.minusDays(REPLAY_DAYS))
        var cursor = replayStart
        while (!cursor.isAfter(asOf)) {
            val windowStart = cursor.minusDays(ANALYSIS_WINDOW_DAYS)
            val window = ordered.filter { !it.date.isAfter(cursor) && !it.date.isBefore(windowStart) }
            val target = if (window.isEmpty()) NonTefEstimate.UNAVAILABLE else unclampedEstimate(window, cursor)
            val targetKcal = target.kcal

            if (targetKcal != null) {
                val previous = accepted
                accepted = if (previous == null) {
                    targetKcal
                } else {
                    val elapsed = ChronoUnit.DAYS.between(acceptedOn ?: cursor, cursor)
                        .coerceAtLeast(1L)
                    val budget = MAX_DAILY_ADAPTATION_KCAL * elapsed
                    val delta = (targetKcal - previous).coerceIn(-budget, budget)
                    previous + delta
                }
                acceptedOn = cursor
                lastTarget = target
            }
            cursor = cursor.plusDays(1)
        }

        val settled = accepted ?: return NonTefEstimate.UNAVAILABLE
        val maxOffset = abs(formulaToday) * MAX_TOTAL_OFFSET_FRACTION
        val bounded = settled.coerceIn(formulaToday - maxOffset, formulaToday + maxOffset)

        val clampedBy = when {
            abs(bounded - settled) > TOLERANCE -> ClampReason.TOTAL_OFFSET
            lastTarget.kcal != null && abs(settled - lastTarget.kcal) > TOLERANCE -> ClampReason.DAILY_RATE
            else -> null
        }

        return lastTarget.copy(
            kcal = bounded,
            formulaKcal = formulaToday,
            clampedBy = clampedBy
        )
    }

    /**
     * Where the blend *wants* to be for a window ending on its last day, before any rate limiting.
     * Exposed for testing the ladder in isolation.
     */
    internal fun unclampedEstimate(window: List<EnergyDay>, asOf: LocalDate): NonTefEstimate {
        val formula = window.lastOrNull { it.formulaNonTefKcal != null }?.formulaNonTefKcal
            ?: return NonTefEstimate.UNAVAILABLE

        val spanDays = ChronoUnit.DAYS.between(window.first().date, asOf).toInt() + 1
        val intakeDays = window.count { it.intakeKcal != null }
        val weightDays = window.count { it.weightKg != null }
        val coverage = if (spanDays > 0) intakeDays.toDouble() / spanDays else 0.0

        val weight = observedWeight(spanDays, coverage, weightDays)
        val formulaOnly = NonTefEstimate(
            kcal = formula,
            formulaKcal = formula,
            observedKcal = null,
            observedWeight = 0.0,
            confidence = EstimateConfidence.LOW,
            daysOfData = spanDays
        )
        if (weight <= 0.0) return formulaOnly

        val meanIntake = window.mapNotNull { it.intakeKcal }.average()
        val slope = weightSlopeKgPerDay(window) ?: return formulaOnly

        val observedTdee = meanIntake - slope * KCAL_PER_KG
        // The observation is a full TDEE and therefore already contains the thermic effect of that
        // mean intake. Strip it so both sides of the blend are in the same currency.
        val observedNonTef = observedTdee - MetabolicModel.TEF_RATE * meanIntake
        if (observedNonTef <= 0) return formulaOnly

        return NonTefEstimate(
            kcal = weight * observedNonTef + (1 - weight) * formula,
            formulaKcal = formula,
            observedKcal = observedNonTef,
            observedWeight = weight,
            confidence = confidenceFor(weight),
            daysOfData = spanDays
        )
    }

    /**
     * The weight ladder. Adaptation *starts* at 28 days but barely counts there; it only approaches
     * the ceiling once there is enough data that water and glycogen have averaged out across
     * several training weeks.
     */
    internal fun observedWeight(spanDays: Int, intakeCoverage: Double, weightReadings: Int): Double =
        when {
            spanDays >= 42 && intakeCoverage >= 0.85 && weightReadings >= 12 -> MAX_OBSERVED_WEIGHT
            spanDays >= 35 && intakeCoverage >= 0.80 && weightReadings >= 10 -> 0.35
            spanDays >= MIN_WINDOW_DAYS && intakeCoverage >= 0.70 && weightReadings >= 8 -> 0.15
            else -> 0.0
        }

    private fun confidenceFor(weight: Double): EstimateConfidence = when {
        weight >= MAX_OBSERVED_WEIGHT -> EstimateConfidence.HIGH
        weight >= 0.35 -> EstimateConfidence.MEDIUM
        else -> EstimateConfidence.LOW
    }

    /**
     * Body-mass trend in kg/day from a Theil–Sen fit over the EWMA-smoothed series: the median of
     * all pairwise slopes. Chosen over least squares because it tolerates up to ~29% outliers
     * before breaking down, and a refeed day or a salty meal is exactly that kind of outlier.
     */
    internal fun weightSlopeKgPerDay(window: List<EnergyDay>): Double? {
        val smoothed = smoothWeights(window)
        if (smoothed.size < 2) return null

        val slopes = mutableListOf<Double>()
        for (i in smoothed.indices) {
            for (j in i + 1 until smoothed.size) {
                val dx = (smoothed[j].first - smoothed[i].first).toDouble()
                if (dx <= 0.0) continue
                slopes += (smoothed[j].second - smoothed[i].second) / dx
            }
        }
        if (slopes.isEmpty()) return null
        return median(slopes)
    }

    /** (epochDay, smoothedWeight) for every day that carried a reading, oldest first. */
    private fun smoothWeights(window: List<EnergyDay>): List<Pair<Long, Double>> {
        val out = mutableListOf<Pair<Long, Double>>()
        var ewma: Double? = null
        window.forEach { day ->
            val w = day.weightKg ?: return@forEach
            ewma = ewma?.let { it + WEIGHT_EWMA_ALPHA * (w - it) } ?: w
            out += day.date.toEpochDay() to ewma!!
        }
        return out
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private const val TOLERANCE = 0.001

    /** Convenience for callers that only need the number. */
    fun blendedNonTefKcal(days: List<EnergyDay>, asOf: LocalDate): Double? =
        estimate(days, asOf).kcal

    /** Days of history still needed before adaptation can begin, or 0 once there are enough. */
    fun daysUntilAdaptationStarts(spanDays: Int): Int =
        (MIN_WINDOW_DAYS - spanDays).coerceAtLeast(0)
}
