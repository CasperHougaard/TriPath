package com.tripath.domain.health

/**
 * Where an energy-availability figure falls.
 *
 * The names are part of the safety design. The literature's own labels ("optimal", "low") read as
 * findings, and this is a screening signal — see [EnergyAvailability] for why the thresholds are
 * much softer than they are usually presented, particularly for men.
 */
enum class EnergyAvailabilityBand(val label: String) {
    /** At or above ~45 kcal/kg FFM — the range controlled trials associate with normal function. */
    ADEQUATE("Adequate"),

    /** Between the two reference points. Common in a deliberate deficit and not alarming by itself. */
    REDUCED("Reduced"),

    /**
     * Below ~30 kcal/kg FFM. Named `LOW_SIGNAL` rather than `LOW` because it means "screening
     * flagged this, watch the trend", not "you have RED-S".
     */
    LOW_SIGNAL("Low — screening signal"),

    /** No fat-free mass, or nothing logged. */
    UNKNOWN("Unknown")
}

/** A single assessment, with the inputs kept alongside so the UI can explain the number. */
data class EnergyAvailabilityResult(
    val kcalPerKgFfm: Double?,
    val band: EnergyAvailabilityBand,
    val daysCounted: Int = 0
) {
    companion object {
        val UNKNOWN = EnergyAvailabilityResult(null, EnergyAvailabilityBand.UNKNOWN)
    }
}

/**
 * Energy availability: the energy left for everything the body does that is not exercise, expressed
 * per kilogram of fat-free mass.
 *
 * ```
 * EA = (intake − exercise energy expenditure) / kg FFM
 * ```
 *
 * This is the standard definition, and the reason it is worth computing at all is that it catches
 * the failure mode a plain calorie balance misses: an athlete can be in energy *balance* on paper
 * while leaving their physiology far too little to run on, because training took most of it.
 *
 * ## Why the thresholds are treated as soft
 * The familiar 45 / 30 kcal/kg FFM reference points come from tightly controlled laboratory work in
 * women. The IOC's 2023 REDs consensus is explicit that a universal 30 kcal/kg FFM cut-off is
 * disputed even in females, that the male threshold is far less well characterised and appears
 * *lower* (somewhere around 9–25), and that the field has moved from a single line toward graded
 * risk assessment. Field studies have repeatedly failed to find a clean threshold at all.
 *
 * So: a single low day means almost nothing, the rolling figure is what matters, and the wording
 * everywhere is "screening signal, watch the trend". This is not a diagnosis and must never be
 * presented as one.
 */
object EnergyAvailability {

    /** Reference point for the range controlled trials associate with normal function. */
    const val ADEQUATE_KCAL_PER_KG_FFM = 45.0

    /**
     * The widely cited screening reference point. Roughly equal to resting metabolic rate itself —
     * below it, exercise has eaten everything the body needed just to keep the lights on.
     */
    const val LOW_SIGNAL_KCAL_PER_KG_FFM = 30.0

    /** Days in the rolling window. A week smooths out one big session and one big meal. */
    const val ROLLING_WINDOW_DAYS = 7

    /** Minimum logged days in the window before a rolling figure is worth showing. */
    const val MIN_DAYS_FOR_ROLLING = 4

    fun band(kcalPerKgFfm: Double?): EnergyAvailabilityBand = when {
        kcalPerKgFfm == null -> EnergyAvailabilityBand.UNKNOWN
        kcalPerKgFfm >= ADEQUATE_KCAL_PER_KG_FFM -> EnergyAvailabilityBand.ADEQUATE
        kcalPerKgFfm >= LOW_SIGNAL_KCAL_PER_KG_FFM -> EnergyAvailabilityBand.REDUCED
        else -> EnergyAvailabilityBand.LOW_SIGNAL
    }

    /**
     * Energy availability for one day. Null intake means the day was not logged, which is not the
     * same as a day of eating nothing and must not be scored.
     */
    fun forDay(intakeKcal: Double?, exerciseKcal: Double, ffmKg: Double?): EnergyAvailabilityResult {
        val intake = intakeKcal ?: return EnergyAvailabilityResult.UNKNOWN
        val ffm = ffmKg ?: return EnergyAvailabilityResult.UNKNOWN
        if (ffm <= 0) return EnergyAvailabilityResult.UNKNOWN
        val ea = (intake - exerciseKcal) / ffm
        return EnergyAvailabilityResult(ea, band(ea), daysCounted = 1)
    }

    /**
     * Rolling energy availability over the most recent [ROLLING_WINDOW_DAYS] entries of [days],
     * which must be ordered oldest first. Unlogged days are skipped rather than counted as zero.
     *
     * This, not the single-day figure, is what should drive anything the athlete acts on.
     */
    fun rolling(days: List<DayEnergy>, ffmKg: Double?): EnergyAvailabilityResult {
        val ffm = ffmKg ?: return EnergyAvailabilityResult.UNKNOWN
        if (ffm <= 0) return EnergyAvailabilityResult.UNKNOWN

        val logged = days.takeLast(ROLLING_WINDOW_DAYS).filter { it.intakeKcal != null }
        if (logged.size < MIN_DAYS_FOR_ROLLING) return EnergyAvailabilityResult.UNKNOWN

        val available = logged.sumOf { (it.intakeKcal ?: 0.0) - it.exerciseKcal }
        val ea = available / logged.size / ffm
        return EnergyAvailabilityResult(ea, band(ea), daysCounted = logged.size)
    }

    /** One day's energy in and energy spent training. */
    data class DayEnergy(val intakeKcal: Double?, val exerciseKcal: Double)
}
