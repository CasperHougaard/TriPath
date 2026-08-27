package com.tripath.domain.coach

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import com.tripath.domain.health.EnergyAvailabilityBand
import com.tripath.domain.strain.ReadinessAction
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.StrainChannel
import kotlin.math.roundToInt

/**
 * Turns a readiness verdict into advice about a specific planned session.
 *
 * ## Why this replaces the old rule rather than adding to it
 * The engine used to carry one hardcoded rule: "yesterday was strength, so keep today easy unless
 * it's a swim". That is the right instinct expressed in the only way it could be before the app
 * knew what a session had loaded — a date-and-type heuristic standing in for tissue state.
 *
 * With per-channel strain the same instinct generalises properly. It is no longer "yesterday was
 * strength" but "the tissue this session needs is still carrying load", which catches the cases the
 * heuristic missed (a long run three days ago still limiting today) and stops firing on the ones it
 * got wrong (a heavy upper-body day has no bearing on a bike session).
 *
 * ## Warnings, not blocks
 * Everything here is advisory. The athlete knows things the model does not — a race, a training
 * partner, a week of travel coming up — and a planner that refuses to schedule is a planner that
 * gets ignored. The only historical blocker (severe allergy) is left exactly as it was.
 */
object ReadinessPlanRules {

    /** Below this a channel is treated as still meaningfully loaded. */
    const val LOADED_FRESHNESS = 55

    /** Below this it is loaded enough that a hard session on it is a bad idea. */
    const val DEPLETED_FRESHNESS = 35

    /** A planned session above this zone counts as "hard" for these rules. */
    const val EASY_ZONE_CEILING = 2

    /**
     * Which channels a discipline draws on. Mirrors
     * [com.tripath.domain.strain.StrainMapper.disciplineVector] — the same physical facts, expressed
     * as "what would limit this" rather than "what does this load".
     */
    internal fun limitingChannels(type: WorkoutType): List<StrainChannel> = when (type) {
        WorkoutType.RUN -> listOf(StrainChannel.LOWER_IMPACT, StrainChannel.LOWER_MUSCULAR)
        WorkoutType.HIKE, WorkoutType.WALK -> listOf(StrainChannel.LOWER_IMPACT)
        WorkoutType.BIKE -> listOf(StrainChannel.LOWER_MUSCULAR)
        WorkoutType.SWIM -> listOf(StrainChannel.UPPER_MUSCULAR)
        // A strength session could be either; without knowing the plan's focus, both are fair game.
        WorkoutType.STRENGTH -> listOf(StrainChannel.LOWER_MUSCULAR, StrainChannel.UPPER_MUSCULAR)
        WorkoutType.OTHER -> emptyList()
    }

    /**
     * Advice for one planned session.
     *
     * [plannedZone] comes from the engine's own zone inference, so an easy recovery spin is not
     * flagged merely because the legs are tired — easy work on tired legs is often the point.
     */
    fun evaluate(
        plan: TrainingPlan,
        plannedZone: Int,
        readiness: ReadinessAssessment?
    ): List<CoachWarning> {
        val assessment = readiness ?: return emptyList()
        if (!assessment.strain.hasData) return emptyList()

        val warnings = mutableListOf<CoachWarning>()
        val isHard = plannedZone > EASY_ZONE_CEILING

        // Systemic fatigue limits everything, so it is checked first and reported once.
        val systemic = assessment.strain[StrainChannel.SYSTEMIC]?.freshness
        if (systemic != null && systemic < DEPLETED_FRESHNESS && isHard) {
            warnings += CoachWarning(
                type = WarningType.RECOVERY_ADVICE,
                title = "Systemically fatigued",
                message = "Whole-body fatigue is high (${systemic}% fresh). Today's ${plan.type.readable()} " +
                    "is planned hard — an easy session or a rest day will be worth more.",
                isBlocker = false
            )
            return warnings
        }

        limitingChannels(plan.type)
            .mapNotNull { channel -> assessment.strain[channel] }
            .filter { it.freshness < LOADED_FRESHNESS }
            .minByOrNull { it.freshness }
            ?.let { worst ->
                val clears = worst.hoursToFresh?.let { " (~${formatHours(it)} to clear)" }.orEmpty()
                warnings += if (worst.freshness < DEPLETED_FRESHNESS && isHard) {
                    CoachWarning(
                        type = WarningType.INJURY_RISK,
                        title = "${worst.channel.label} not recovered",
                        message = "${worst.channel.label} is at ${worst.freshness}% fresh$clears. " +
                            "A hard ${plan.type.readable()} loads exactly that — drop the intensity or " +
                            "swap in something that doesn't.",
                        isBlocker = false
                    )
                } else {
                    CoachWarning(
                        type = WarningType.RECOVERY_ADVICE,
                        title = "${worst.channel.label} still loaded",
                        message = "${worst.channel.label} is at ${worst.freshness}% fresh$clears. " +
                            "Fine to train, but keep today honest.",
                        isBlocker = false
                    )
                }
            }

        return warnings
    }

    /**
     * Advice about the week rather than the day: chronic under-fuelling is a reason to stop adding
     * load, not a reason to skip today's session.
     *
     * Deliberately never a blocker. Energy availability especially is a screening signal whose
     * thresholds come largely from studies in female athletes, with the male picture both less
     * settled and probably lower — it earns a "look at this", not a veto.
     */
    fun evaluateFuelling(
        readiness: ReadinessAssessment?,
        energyAvailability: EnergyAvailabilityBand,
        weeklyRampPct: Double?
    ): List<CoachWarning> {
        val warnings = mutableListOf<CoachWarning>()

        val fuellingDriver = readiness?.drivers?.firstOrNull { it.label == "Fuelling" && !it.isPositive }
        if (fuellingDriver != null || energyAvailability == EnergyAvailabilityBand.LOW_SIGNAL) {
            val ramping = weeklyRampPct != null && weeklyRampPct > 10.0
            warnings += CoachWarning(
                type = WarningType.RECOVERY_ADVICE,
                title = "Under-fuelled for this load",
                message = buildString {
                    append(fuellingDriver?.detail ?: "Energy availability screening flagged low")
                    append(". ")
                    append(
                        if (ramping) {
                            "Load is also up ${weeklyRampPct!!.roundToInt()}% on last week — hold it " +
                                "flat until intake catches up."
                        } else {
                            "Worth holding load flat until intake catches up."
                        }
                    )
                },
                isBlocker = false
            )
        }
        return warnings
    }

    /**
     * The whole-day verdict, for a screen that wants one line rather than a list.
     * Null when there is nothing worth saying.
     */
    fun dayHeadline(readiness: ReadinessAssessment?): String? {
        val assessment = readiness ?: return null
        return when (assessment.action) {
            ReadinessAction.GO -> null
            else -> assessment.guidance.takeIf { it.isNotBlank() }
        }
    }

    private fun WorkoutType.readable(): String = name.lowercase()

    private fun formatHours(hours: Int): String =
        if (hours >= 24) "${(hours / 24.0).roundToInt()}d" else "${hours}h"
}
