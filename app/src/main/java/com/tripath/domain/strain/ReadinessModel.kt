package com.tripath.domain.strain

import com.tripath.data.model.WorkoutType
import com.tripath.domain.health.DailyNutritionTarget
import com.tripath.domain.health.EnergyAvailabilityBand
import kotlin.math.abs
import kotlin.math.roundToInt

/** Traffic-light banding for a readiness score. */
enum class ReadinessBand(val label: String) {
    FRESH("Fresh"),
    READY("Ready"),
    COMPROMISED("Compromised"),
    DEPLETED("Depleted");

    companion object {
        fun forScore(score: Int): ReadinessBand = when {
            score >= 80 -> FRESH
            score >= 60 -> READY
            score >= 40 -> COMPROMISED
            else -> DEPLETED
        }
    }
}

/** What to actually do today. */
enum class ReadinessAction(val label: String) {
    GO("Train as planned"),
    MODERATE("Train, but hold something back"),
    EASY("Easy only"),
    REST("Rest")
}

/**
 * One reason the score is what it is.
 *
 * The whole point of the model is that it can explain itself; a bare number is the thing this is
 * meant to replace. [impact] is signed — positive helps, negative hurts — so drivers sort by how
 * much they actually moved the result rather than by a fixed order.
 */
data class ReadinessDriver(
    val label: String,
    val detail: String,
    val impact: Double
) {
    val isPositive: Boolean get() = impact >= 0
}

/** Whether a specific discipline is a good idea today, and why not if it isn't. */
data class DisciplineVerdict(
    val discipline: WorkoutType,
    val action: ReadinessAction,
    val reason: String
)

/** The complete readiness picture. */
data class ReadinessAssessment(
    val score: Int,
    val band: ReadinessBand,
    val action: ReadinessAction,
    val strain: StrainState = StrainState(),
    val drivers: List<ReadinessDriver> = emptyList(),
    val disciplineVerdicts: List<DisciplineVerdict> = emptyList(),
    val guidance: String = "",
    /**
     * This week's load against last week's, as a percentage. **Descriptive only.** A ratio of
     * recent to chronic load is widely quoted as an injury predictor and that claim does not hold
     * up — the original "sweet spot" figure has been the subject of a retraction request, and
     * reviews since have found no consistent, causal relationship. Shown so a sharp jump is
     * *visible*, never used to score or to block.
     */
    val weeklyLoadRampPct: Double? = null,
    /**
     * The fuelling screening band that fed [drivers], carried through so callers do not have to
     * rebuild the fuel model to ask a question the assessment already answered. A screening signal,
     * never a finding — see [com.tripath.domain.health.EnergyAvailability].
     */
    val energyAvailability: EnergyAvailabilityBand = EnergyAvailabilityBand.UNKNOWN,
    /**
     * What today's training means the athlete should eat, carried for the same reason as
     * [energyAvailability]: the fuel model has already run to produce the fuelling driver, and a
     * second caller rebuilding it is how two parts of one screen end up quoting different numbers.
     *
     * Null on a projection, and whenever the fuel model has no weight or goal to work from.
     */
    val fuelTarget: DailyNutritionTarget? = null,
    /** True when this is a forecast rather than an observation — see [ReadinessModel.projected]. */
    val isProjected: Boolean = false
)

/** Everything the model can take into account. Every field is optional and degrades gracefully. */
data class ReadinessInputs(
    val strain: StrainState = StrainState(),
    /** Training stress balance (fitness − fatigue). */
    val tsb: Double? = null,
    val sleepMinutesLastNight: Int? = null,
    /** Rolling sleep debt in minutes over the last week; positive means short. */
    val sleepDebtMinutes: Int? = null,
    val sleepNeedMinutes: Int = 480,
    /** 7-day rolling rMSSD. */
    val hrvRecent: Double? = null,
    /** 60-day rMSSD baseline for this athlete. */
    val hrvBaseline: Double? = null,
    /** Mean daily energy balance over the last week, kcal. Negative means under expenditure. */
    val energyBalanceKcal: Double? = null,
    val energyAvailability: EnergyAvailabilityBand = EnergyAvailabilityBand.UNKNOWN,
    /**
     * Today's fuelling target. Not scored — the score already reads the *outcome* of fuelling
     * through [energyBalanceKcal]. This is passed through so the assessment can also say what to do
     * about it, rather than only that something is wrong.
     */
    val fuelTarget: DailyNutritionTarget? = null,
    /** 1–10, higher is worse. */
    val soreness: Int? = null,
    /** 1–10, higher is better. */
    val mood: Int? = null,
    val weeklyLoadRampPct: Double? = null
)

/**
 * Combines everything the app knows into one explainable readiness verdict.
 *
 * ## Weights renormalise over what exists
 * Every input is optional. A missing one is *removed* and the remaining weights rescale, rather
 * than being scored at a neutral 50 — scoring absent data as "average" quietly drags every result
 * toward the middle and makes a well-evidenced 85 indistinguishable from a guess.
 *
 * ## Nothing here predicts injury
 * The components are the ones with reasonable evidence behind them for *readiness to train*: form,
 * regional loading, sleep, heart-rate variability against personal baseline, and fuelling. Load
 * ramp is reported but deliberately not scored — see [ReadinessAssessment.weeklyLoadRampPct].
 *
 * ## Per-discipline, not one number
 * A red impact channel should stop a hard run and leave swimming alone. The single score exists for
 * the headline; [ReadinessAssessment.disciplineVerdicts] is what should actually drive decisions.
 */
object ReadinessModel {

    private const val WEIGHT_FORM = 0.25
    private const val WEIGHT_STRAIN = 0.20
    private const val WEIGHT_SLEEP = 0.20
    private const val WEIGHT_HRV = 0.15
    private const val WEIGHT_FUEL = 0.15
    private const val WEIGHT_SUBJECTIVE = 0.05

    /**
     * Soreness at or above this caps the score however good everything else looks.
     *
     * A hard override rather than a weighted term on purpose: an athlete reporting 9/10 soreness is
     * telling the model something it cannot see, and a 5% weight would let a good night's sleep and
     * a flattering TSB bury it.
     */
    private const val SEVERE_SORENESS = 8
    private const val SEVERE_SORENESS_CAP = 45

    /**
     * The one component that is per-tissue rather than whole-athlete. Named because [assess] has to
     * exclude it when building the floor for the per-discipline verdicts.
     */
    private const val LABEL_REGIONAL_LOAD = "Regional load"

    fun assess(inputs: ReadinessInputs): ReadinessAssessment {
        val components = mutableListOf<Component>()

        formScore(inputs.tsb)?.let { components += it }
        strainScore(inputs.strain)?.let { components += it }
        sleepScore(inputs)?.let { components += it }
        hrvScore(inputs.hrvRecent, inputs.hrvBaseline)?.let { components += it }
        fuelScore(inputs)?.let { components += it }
        subjectiveScore(inputs.soreness, inputs.mood)?.let { components += it }

        if (components.isEmpty()) {
            return ReadinessAssessment(
                score = 0,
                band = ReadinessBand.READY,
                action = ReadinessAction.GO,
                strain = inputs.strain,
                guidance = "Not enough data yet — log sleep, weight or a wellness check to get a reading.",
                weeklyLoadRampPct = inputs.weeklyLoadRampPct,
                energyAvailability = inputs.energyAvailability,
                fuelTarget = inputs.fuelTarget
            )
        }

        val totalWeight = components.sumOf { it.weight }
        val weighted = components.sumOf { it.score * it.weight } / totalWeight
        var score = weighted.roundToInt().coerceIn(0, 100)

        val capped = inputs.soreness != null && inputs.soreness >= SEVERE_SORENESS
        if (capped) score = minOf(score, SEVERE_SORENESS_CAP)

        // Impact is measured against the mean, so a driver reads as "this is what is pulling the
        // number around" rather than as its own raw score.
        val drivers = components
            .map { ReadinessDriver(it.label, it.detail, (it.score - weighted) * it.weight / totalWeight) }
            .sortedBy { it.impact }
            .let { sorted -> sorted.filter { !it.isPositive } + sorted.filter { it.isPositive }.reversed() }

        val action = actionFor(score)

        // The floor under every per-discipline verdict, built from the components that apply to the
        // whole athlete rather than to one tissue.
        //
        // Regional load is deliberately excluded. It is the one signal already expressed per
        // discipline, so letting it set the floor would undo the distinction this model exists to
        // make: a pair of wrecked legs would drag the score down far enough to veto a swim. Sleep,
        // HRV, fuelling, form and how the athlete feels are not like that — they follow the athlete
        // into whichever pool or bike they choose, and a row reading "Upper body is recovered —
        // train as planned" on four hours' sleep and a 900 kcal deficit is advice nobody should act
        // on.
        val bodyWide = components.filter { it.label != LABEL_REGIONAL_LOAD }
        val bodyWideAction = if (bodyWide.isEmpty()) {
            ReadinessAction.GO
        } else {
            val bodyWideWeight = bodyWide.sumOf { it.weight }
            val bodyWideScore = (bodyWide.sumOf { it.score * it.weight } / bodyWideWeight)
                .roundToInt()
                .coerceIn(0, 100)
                .let { if (capped) minOf(it, SEVERE_SORENESS_CAP) else it }
            actionFor(bodyWideScore)
        }

        return ReadinessAssessment(
            score = score,
            band = ReadinessBand.forScore(score),
            action = action,
            strain = inputs.strain,
            drivers = drivers,
            disciplineVerdicts = disciplineVerdicts(bodyWideAction, inputs.strain),
            guidance = guidance(score, action, drivers, capped),
            weeklyLoadRampPct = inputs.weeklyLoadRampPct,
            energyAvailability = inputs.energyAvailability,
            fuelTarget = inputs.fuelTarget
        )
    }

    /**
     * A forecast for a future day: the training-load components projected forward, everything else
     * held at baseline and marked as assumed.
     *
     * The app does not know what Friday's sleep or heart-rate variability will be, and must not
     * imply that it does. So a projection carries only what follows from planned training, and
     * [ReadinessAssessment.isProjected] tells the UI to present it as the guess it is.
     */
    fun projected(strain: StrainState, tsb: Double?): ReadinessAssessment {
        val assessment = assess(ReadinessInputs(strain = strain, tsb = tsb))
        return assessment.copy(
            isProjected = true,
            guidance = "Projected from planned training only — sleep, HRV and fuelling are assumed " +
                "to sit at your normal levels."
        )
    }

    // ---- Components ---------------------------------------------------------------------------

    private data class Component(
        val label: String,
        val detail: String,
        val score: Double,
        val weight: Double
    )

    /**
     * Training stress balance. Deeply negative means fatigue has outrun fitness; slightly negative
     * is the productive range an athlete in a training block lives in, so it is not penalised hard.
     */
    private fun formScore(tsb: Double?): Component? {
        val value = tsb ?: return null
        val score = when {
            value >= 5 -> 100.0
            value <= -30 -> 0.0
            else -> ((value + 30) / 35.0) * 100.0
        }
        val detail = when {
            value >= 5 -> "Form is positive (${value.roundToInt()}) — fitness is ahead of fatigue"
            value >= -10 -> "Form is mildly negative (${value.roundToInt()}) — a normal training block"
            else -> "Form is deep in the red (${value.roundToInt()}) — fatigue has outrun fitness"
        }
        return Component("Form", detail, score.coerceIn(0.0, 100.0), WEIGHT_FORM)
    }

    /**
     * Regional loading, scored on the worst channel rather than the average.
     *
     * Averaging would let three fresh channels hide one that is wrecked, which is precisely the
     * failure a per-channel model exists to avoid.
     */
    private fun strainScore(strain: StrainState): Component? {
        val worst = strain.mostLoaded ?: return null
        val detail = if (worst.freshness >= 90) {
            "All areas are close to their usual load"
        } else {
            "${worst.channel.label} is the most loaded" +
                (worst.hoursToFresh?.let { " — about ${formatHours(it)} to clear" } ?: "")
        }
        return Component(LABEL_REGIONAL_LOAD, detail, worst.freshness.toDouble(), WEIGHT_STRAIN)
    }

    /**
     * Last night plus the week's accumulated debt.
     *
     * Both matter and they are not the same thing: one good night does not repay a week of short
     * ones, and one short night after a well-slept week is not much of a problem.
     */
    private fun sleepScore(inputs: ReadinessInputs): Component? {
        val lastNight = inputs.sleepMinutesLastNight
        val debt = inputs.sleepDebtMinutes
        if (lastNight == null && debt == null) return null

        val need = inputs.sleepNeedMinutes.coerceAtLeast(1)
        val nightScore = lastNight?.let { (it.toDouble() / need * 100.0).coerceIn(0.0, 100.0) }
        // A week's debt of one full night's sleep scores zero on this half.
        val debtScore = debt?.let { (100.0 - (it.toDouble() / need) * 100.0).coerceIn(0.0, 100.0) }

        val score = listOfNotNull(nightScore, debtScore).average()
        val detail = buildString {
            if (lastNight != null) append("Slept ${formatMinutes(lastNight)} of ${formatMinutes(need)}")
            if (debt != null && debt > 30) {
                if (isNotEmpty()) append(" · ")
                append("${formatMinutes(debt)} short over the week")
            }
        }.ifEmpty { "Sleep is on track" }

        return Component("Sleep", detail, score, WEIGHT_SLEEP)
    }

    /**
     * Heart-rate variability against the athlete's own baseline.
     *
     * The absolute number is meaningless between people — it is the deviation from a personal
     * norm that carries information, which is why a baseline is required and no reading is
     * produced without one. Scored on a band around the baseline rather than a knife edge, because
     * night-to-night variation is large and a single low reading is noise.
     */
    private fun hrvScore(recent: Double?, baseline: Double?): Component? {
        val r = recent ?: return null
        val b = baseline ?: return null
        if (b <= 0.0) return null

        val ratio = r / b
        val score = when {
            ratio >= 1.05 -> 100.0
            ratio <= 0.80 -> 0.0
            else -> ((ratio - 0.80) / 0.25) * 100.0
        }
        val pct = ((ratio - 1.0) * 100).roundToInt()
        val detail = when {
            ratio >= 1.05 -> "HRV is above your baseline (+$pct%)"
            ratio >= 0.95 -> "HRV is normal for you"
            else -> "HRV is below your baseline ($pct%)"
        }
        return Component("HRV", detail, score.coerceIn(0.0, 100.0), WEIGHT_HRV)
    }

    /**
     * Whether the athlete is fuelled for the work they are doing.
     *
     * Two signals: a chronic energy deficit, and energy availability. The availability band is
     * treated as a screening flag with a modest penalty, never as a finding — its thresholds are
     * derived largely from female athletes and the male picture is both less settled and probably
     * lower.
     */
    private fun fuelScore(inputs: ReadinessInputs): Component? {
        val balance = inputs.energyBalanceKcal
        val band = inputs.energyAvailability
        if (balance == null && band == EnergyAvailabilityBand.UNKNOWN) return null

        var score = 100.0
        val notes = mutableListOf<String>()

        if (balance != null && balance < 0) {
            // A modest deficit is a deliberate choice and not a readiness problem; a large one is.
            val penalty = ((-balance - 300.0) / 700.0 * 60.0).coerceIn(0.0, 60.0)
            score -= penalty
            if (penalty > 0) notes += "averaging ${(-balance).roundToInt()} kcal under expenditure"
        }

        when (band) {
            EnergyAvailabilityBand.LOW_SIGNAL -> {
                score -= 25.0
                notes += "energy availability screening flagged low — worth watching the trend"
            }
            EnergyAvailabilityBand.REDUCED -> {
                score -= 10.0
                notes += "energy availability is reduced"
            }
            else -> Unit
        }

        val detail = if (notes.isEmpty()) "Fuelling matches the training" else notes.joinToString(" · ")
        return Component("Fuelling", detail.replaceFirstChar { it.uppercase() }, score.coerceIn(0.0, 100.0), WEIGHT_FUEL)
    }

    private fun subjectiveScore(soreness: Int?, mood: Int?): Component? {
        if (soreness == null && mood == null) return null
        val sorenessScore = soreness?.let { (10 - it) / 9.0 * 100.0 }
        val moodScore = mood?.let { (it - 1) / 9.0 * 100.0 }
        val score = listOfNotNull(sorenessScore, moodScore).average()

        val detail = buildString {
            soreness?.let { append("Soreness $it/10") }
            mood?.let {
                if (isNotEmpty()) append(" · ")
                append("Mood $it/10")
            }
        }
        return Component("How you feel", detail, score, WEIGHT_SUBJECTIVE)
    }

    // ---- Verdicts -----------------------------------------------------------------------------

    private fun actionFor(score: Int): ReadinessAction = when {
        score < 35 -> ReadinessAction.REST
        score < 50 -> ReadinessAction.EASY
        score < 70 -> ReadinessAction.MODERATE
        else -> ReadinessAction.GO
    }

    /**
     * Per-discipline advice from the channels each one actually loads.
     *
     * This is the payoff of splitting strain by tissue: hammered legs are a reason not to run, not
     * a reason to skip a swim, and a global score can never make that distinction.
     *
     * [floor] is the best any discipline may be rated — see where it is built in [assess]. Fresh
     * tissue is a necessary condition for a hard session, not a sufficient one.
     */
    internal fun disciplineVerdicts(floor: ReadinessAction, strain: StrainState): List<DisciplineVerdict> {
        if (!strain.hasData) return emptyList()

        fun freshness(channel: StrainChannel): Int = strain[channel]?.freshness ?: 100

        val impact = freshness(StrainChannel.LOWER_IMPACT)
        val legs = freshness(StrainChannel.LOWER_MUSCULAR)
        val upper = freshness(StrainChannel.UPPER_MUSCULAR)
        val systemic = freshness(StrainChannel.SYSTEMIC)

        fun verdict(
            discipline: WorkoutType,
            limiting: Int,
            limitingLabel: String
        ): DisciplineVerdict {
            val effective = minOf(limiting, systemic)
            val fromTissue = when {
                effective < 35 -> ReadinessAction.REST
                effective < 55 -> ReadinessAction.EASY
                effective < 75 -> ReadinessAction.MODERATE
                else -> ReadinessAction.GO
            }
            // The actions are ordered GO → MODERATE → EASY → REST, so the more cautious of the two
            // is simply the larger.
            val action = maxOf(fromTissue, floor)
            val reason = when {
                action > fromTissue -> "This tissue is fine — today's sleep, fuelling and form are the limit"
                effective >= 75 -> "$limitingLabel is recovered"
                systemic < limiting -> "Systemic fatigue is the limit right now"
                else -> "$limitingLabel is still carrying load"
            }
            return DisciplineVerdict(discipline, action, reason)
        }

        return listOf(
            verdict(WorkoutType.RUN, minOf(impact, legs), "Legs (impact)"),
            verdict(WorkoutType.BIKE, legs, "Legs (muscular)"),
            verdict(WorkoutType.SWIM, upper, "Upper body"),
            verdict(WorkoutType.STRENGTH, minOf(legs, upper), "Lifting muscles")
        )
    }

    private fun guidance(
        score: Int,
        action: ReadinessAction,
        drivers: List<ReadinessDriver>,
        sorenessCapped: Boolean
    ): String {
        if (sorenessCapped) {
            return "Soreness is high enough to override everything else — treat today as recovery " +
                "whatever the other numbers say."
        }
        val worst = drivers.firstOrNull { !it.isPositive }
        return when (action) {
            ReadinessAction.GO ->
                "Everything is where it should be. Train as planned."
            ReadinessAction.MODERATE ->
                "Good enough to train. ${worst?.detail ?: "Keep something in reserve"}."
            ReadinessAction.EASY ->
                "Keep it easy today. ${worst?.detail ?: "Several signals are down"}."
            ReadinessAction.REST ->
                "Rest is the productive choice. ${worst?.detail ?: "Multiple signals are down"}."
        }
    }

    // ---- Formatting ---------------------------------------------------------------------------

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun formatHours(hours: Int): String =
        if (hours >= 24) "${(hours / 24.0).roundToInt()}d" else "${hours}h"

    /** Signed percentage change between this week's load and last week's. Descriptive only. */
    fun weeklyRampPct(thisWeekTss: Int, lastWeekTss: Int): Double? {
        if (lastWeekTss <= 0) return null
        val ramp = (thisWeekTss - lastWeekTss).toDouble() / lastWeekTss * 100.0
        return if (abs(ramp) < 0.5) 0.0 else ramp
    }
}
