package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.model.ActivityLevel
import com.tripath.data.model.BiologicalSex
import com.tripath.data.model.UserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Which equation produced a resting metabolic rate, in descending order of preference.
 *
 * The order is not arbitrary. A 2023 *Sports Medicine* meta-analysis of RMR prediction in athletes
 * found Cunningham (1980) among the few equations that did not systematically mis-estimate — but
 * with large heterogeneity, so its group-level accuracy does not guarantee an individual one. The
 * weight-based ten Haaf equation had the least bias, no observed heterogeneity, and 80.2% of
 * estimates within ±10%. Mifflin–St Jeor significantly mis-estimated in athletes, which is why it
 * is the last resort here even though it remains the clinical default for the general population.
 */
enum class RmrSource {
    /** A measured value (indirect calorimetry) the user entered. Beats every formula. */
    MEASURED_OVERRIDE,

    /** Cunningham (1980), `500 + 22 × FFM`. Needs a fresh, plausible body-composition scan. */
    CUNNINGHAM_FFM,

    /** ten Haaf & Weijs (2014) weight-based. No body scan needed. */
    TEN_HAAF_WEIGHT,

    /** Mifflin–St Jeor. Fallback when height, age or sex make ten Haaf unusable. */
    MIFFLIN_ST_JEOR,

    /** Not enough profile data to estimate anything. */
    UNAVAILABLE
}

/** How much a derived figure should be trusted. Drives UI copy, never the maths. */
enum class EstimateConfidence { HIGH, MEDIUM, LOW, NONE }

/**
 * A resting metabolic rate together with where it came from and how much to trust it.
 *
 * [notes] carries anything the user should know before reading [kcal] as fact — most importantly
 * a disagreement between two equations, which usually means a bad bio-impedance fat reading.
 */
data class RmrEstimate(
    val kcal: Double?,
    val source: RmrSource,
    val confidence: EstimateConfidence,
    val notes: List<String> = emptyList()
) {
    companion object {
        val UNAVAILABLE = RmrEstimate(null, RmrSource.UNAVAILABLE, EstimateConfidence.NONE)
    }
}

/**
 * The metabolic spine of the fuel model: resting rate, non-exercise activity, and the algebra that
 * turns them into an intake target.
 *
 * ## Why "non-TEF expenditure" is the primary quantity
 * The thermic effect of food is ~10% of what is *eaten*, so it is not a fixed addend — it moves
 * with the target it is supposed to help set. Modelling it as an addend produces two bugs:
 *
 *  1. A target sized from *logged* intake would shrink in the morning and grow through the day.
 *  2. Subtracting a deficit from a maintenance TDEE under-delivers that deficit by `TEF_RATE ×
 *     deficit`, because eating less also burns less digesting it.
 *
 * Both disappear if the only thing modelled directly is everything *except* TEF, and TEF is solved
 * for at the moment a target is set. See [targetIntake] for the derivation.
 *
 * All figures are approximations for trend and awareness, not medical or lab-grade values.
 */
object MetabolicModel {

    /** Thermic effect of food as a fraction of energy intake, for a mixed diet. */
    const val TEF_RATE = 0.10

    /** A body scan older than this no longer describes the athlete well enough for Cunningham. */
    const val MAX_SCAN_AGE_DAYS = 45L

    /**
     * Relative gap between Cunningham and ten Haaf beyond which the two are reported as
     * disagreeing. 15% on a ~2,000 kcal RMR is ~300 kcal — far more than either equation's stated
     * error, so it means an input is wrong (almost always the scan's body-fat reading).
     */
    const val EQUATION_DISAGREEMENT_THRESHOLD = 0.15

    // ---- Resting metabolic rate ----------------------------------------------------------------

    /**
     * Cunningham (1980): `REE = 500 + 22 × FFM`.
     *
     * Not to be confused with Katch–McArdle (`370 + 21.6 × FFM`), a different equation with a
     * different intercept and slope. They are routinely conflated; they are not interchangeable.
     */
    fun cunningham(ffmKg: Double?): Double? {
        val ffm = ffmKg ?: return null
        if (ffm <= 0) return null
        return 500.0 + 22.0 * ffm
    }

    /**
     * ten Haaf & Weijs (2014) weight-based equation, derived on recreational athletes:
     *
     * ```
     * REE = 11.936 × weightKg + 587.728 × heightM − 8.129 × age + 191.027 × sex + 29.279
     * ```
     *
     * with `sex` = 1 for male, 0 for female. Verified against the paper's own worked example:
     * 80 kg, 1.80 m, 30 y, male → 1,989.23 kcal/day.
     *
     * The paper's FFM-based variant is deliberately not implemented: any input good enough to
     * reach it has already selected [cunningham], so it would only ever be dead code.
     */
    fun tenHaafWeightBased(
        sex: BiologicalSex?,
        ageYears: Int?,
        weightKg: Double?,
        heightCm: Int?
    ): Double? {
        val s = sex ?: return null
        val age = ageYears ?: return null
        val w = weightKg ?: return null
        val h = heightCm ?: return null
        if (w <= 0 || h <= 0) return null
        val sexTerm = if (s == BiologicalSex.MALE) 1.0 else 0.0
        return 11.936 * w +
            587.728 * (h / 100.0) -
            8.129 * age +
            191.027 * sexTerm +
            29.279
    }

    /**
     * Fat-free mass from a body-composition scan, or null when the scan cannot supply a
     * trustworthy one.
     *
     * Prefers a directly measured lean mass; falls back to `weight × (1 − bodyFat%)`. Rejects
     * implied body-fat percentages outside [MIN_PLAUSIBLE_BODY_FAT]..[MAX_PLAUSIBLE_BODY_FAT],
     * because a bio-impedance scale that mis-reads hydration can report a figure no human has.
     */
    fun fatFreeMassKg(scan: BodyCompositionLog?): Double? {
        val log = scan ?: return null
        log.leanMassKg?.let { if (it > 0) return it }
        val weight = log.weightKg ?: return null
        val fat = log.bodyFatPercent ?: return null
        if (weight <= 0) return null
        if (fat < MIN_PLAUSIBLE_BODY_FAT || fat > MAX_PLAUSIBLE_BODY_FAT) return null
        return weight * (1.0 - fat / 100.0)
    }

    /**
     * The athlete's resting metabolic rate, picking the best equation the available data supports.
     *
     * [scan] should be the most recent body-composition reading; it is only used for
     * [RmrSource.CUNNINGHAM_FFM] and only when no older than [MAX_SCAN_AGE_DAYS].
     *
     * When both Cunningham and ten Haaf are computable they are cross-checked. A disagreement does
     * *not* change which equation is used — it lowers confidence and adds a note, because the right
     * response to two estimates that can't both be right is to say so rather than to pick silently.
     */
    fun restingMetabolicRate(
        profile: UserProfile?,
        scan: BodyCompositionLog?,
        fallbackWeightKg: Double? = null,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): RmrEstimate {
        profile?.rmrOverrideKcal?.let { measured ->
            if (measured > 0) {
                return RmrEstimate(
                    kcal = measured.toDouble(),
                    source = RmrSource.MEASURED_OVERRIDE,
                    confidence = EstimateConfidence.HIGH
                )
            }
        }

        val sex = profile?.biologicalSex
        val age = profile?.ageOn(today)
        val heightCm = profile?.heightCm
        val weight = scan?.weightKg ?: fallbackWeightKg

        val scanIsFresh = scan != null && scanAgeDays(scan, today, zone) <= MAX_SCAN_AGE_DAYS
        val cunninghamKcal = if (scanIsFresh) cunningham(fatFreeMassKg(scan)) else null
        val tenHaafKcal = tenHaafWeightBased(sex, age, weight, heightCm)
        val mifflinKcal = HealthReference.basalMetabolicRate(sex, age, weight, heightCm)

        val disagrees = cunninghamKcal != null && tenHaafKcal != null && tenHaafKcal > 0 &&
            abs(cunninghamKcal - tenHaafKcal) / tenHaafKcal > EQUATION_DISAGREEMENT_THRESHOLD
        val notes = if (disagrees) listOf(DISAGREEMENT_NOTE) else emptyList()

        return when {
            cunninghamKcal != null -> RmrEstimate(
                kcal = cunninghamKcal,
                source = RmrSource.CUNNINGHAM_FFM,
                // Never HIGH: a bio-impedance fat reading is a real measurement but not a DXA, and
                // Cunningham showed large between-individual heterogeneity even where it was
                // accurate on average.
                confidence = if (disagrees) EstimateConfidence.LOW else EstimateConfidence.MEDIUM,
                notes = notes
            )
            tenHaafKcal != null -> RmrEstimate(
                kcal = tenHaafKcal,
                source = RmrSource.TEN_HAAF_WEIGHT,
                confidence = EstimateConfidence.MEDIUM,
                notes = notes
            )
            mifflinKcal != null -> RmrEstimate(
                kcal = mifflinKcal,
                source = RmrSource.MIFFLIN_ST_JEOR,
                confidence = EstimateConfidence.LOW,
                notes = notes
            )
            else -> RmrEstimate.UNAVAILABLE
        }
    }

    private fun scanAgeDays(scan: BodyCompositionLog, today: LocalDate, zone: ZoneId): Long {
        val scanDate = Instant.ofEpochMilli(scan.timestamp).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(scanDate, today)
    }

    // ---- Non-exercise activity -----------------------------------------------------------------

    /**
     * Multiplier applied to RMR for everyday non-exercise movement, derived from step count.
     *
     * Anchored so that ~2,500 steps (a desk day) gives 1.15 and ~15,000 gives 1.40, linear in
     * between and clamped at both ends. The 1.20 that falls out at ~5,000 steps is deliberately
     * the same figure the app used as a fixed constant before step data existed, so an athlete
     * with typical activity sees no jump when this replaces it.
     *
     * [nonExerciseSteps] must already have workout-attributed steps removed — those calories are
     * counted again as exercise energy, and double-counting a marathon's steps is not a small error.
     */
    fun neatFactor(nonExerciseSteps: Int?, fallback: ActivityLevel = ActivityLevel.DEFAULT): Double {
        val steps = nonExerciseSteps ?: return fallback.factor
        if (steps < 0) return fallback.factor
        return (MIN_NEAT_FACTOR + steps * NEAT_PER_STEP).coerceIn(MIN_NEAT_FACTOR, MAX_NEAT_FACTOR)
    }

    // ---- The energy algebra --------------------------------------------------------------------

    /**
     * Everything the body spends that does *not* scale with what was eaten: resting rate lifted by
     * daily movement, plus the day's exercise. Null when the resting rate is unknown.
     */
    fun nonTefExpenditure(rmrKcal: Double?, neatFactor: Double, exerciseKcal: Double): Double? {
        val rmr = rmrKcal ?: return null
        return rmr * neatFactor + exerciseKcal
    }

    /**
     * The intake that achieves [desiredEnergyBalanceKcal] — 0 to maintain, negative to lose,
     * positive to gain.
     *
     * Derivation. With `B` = [nonTefExpenditure], `I` = intake and `t` = [TEF_RATE]:
     *
     * ```
     *   balance = I − TDEE = I − (B + t·I) = (1 − t)·I − B
     *   ⇒ I = (B + balance) / (1 − t)
     * ```
     *
     * The goal therefore enters **before** the division. Subtracting it from a maintenance TDEE
     * afterwards is wrong by `t × balance`: with B = 2,700 and a wanted −500, the correct intake is
     * 2,444 kcal, while `3,000 − 500 = 2,500` yields an actual deficit of only −450.
     *
     * Maintenance is the single case where both forms agree, which is exactly why the error is easy
     * to ship — see `EnergyAlgebraTest`.
     */
    fun targetIntake(nonTefExpenditureKcal: Double?, desiredEnergyBalanceKcal: Double): Double? {
        val b = nonTefExpenditureKcal ?: return null
        return (b + desiredEnergyBalanceKcal) / (1.0 - TEF_RATE)
    }

    /** Total expenditure that will result from eating [targetIntakeKcal]. */
    fun predictedTdee(nonTefExpenditureKcal: Double?, targetIntakeKcal: Double?): Double? {
        val b = nonTefExpenditureKcal ?: return null
        val i = targetIntakeKcal ?: return null
        return b + TEF_RATE * i
    }

    /**
     * Total expenditure for a day that has already happened, using what was actually logged.
     *
     * Historical only. Sizing a *target* from logged intake would make the morning's target lower
     * than the evening's purely because breakfast hadn't been entered yet.
     */
    fun realizedTdeeForDay(nonTefExpenditureKcal: Double?, loggedIntakeKcal: Double?): Double? {
        val b = nonTefExpenditureKcal ?: return null
        return b + TEF_RATE * (loggedIntakeKcal ?: 0.0)
    }

    // ---- Constants -----------------------------------------------------------------------------

    /** Body-fat percentages outside this range indicate a bad scan rather than an unusual body. */
    private const val MIN_PLAUSIBLE_BODY_FAT = 3.0
    private const val MAX_PLAUSIBLE_BODY_FAT = 60.0

    private const val MIN_NEAT_FACTOR = 1.10
    private const val MAX_NEAT_FACTOR = 1.45

    /** 0.05 of multiplier per 2,500 steps — the slope through the anchors in [neatFactor]. */
    private const val NEAT_PER_STEP = 0.05 / 2_500.0

    internal const val DISAGREEMENT_NOTE =
        "RMR estimates disagree — the body-fat reading from your scale may be off"
}
