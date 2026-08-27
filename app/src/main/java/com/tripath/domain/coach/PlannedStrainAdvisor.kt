package com.tripath.domain.coach

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import com.tripath.domain.strain.DailyStrain
import com.tripath.domain.strain.StrainChannel
import com.tripath.domain.strain.StrainMapper
import com.tripath.domain.strain.StrainTimeline
import com.tripath.domain.strain.StrainVector
import java.time.LocalDate
import kotlin.math.roundToInt

/** A place in a generated plan where two sessions draw on the same tissue too close together. */
data class PlanConflict(
    val date: LocalDate,
    val channel: StrainChannel,
    /** Projected freshness on the day, 0–100. */
    val freshness: Int,
    val plan: TrainingPlan,
    val message: String
)

/**
 * Runs the strain model forward over a *plan* rather than over history, to find days where the
 * schedule asks a tissue for something it will not have recovered enough to give.
 *
 * ## Why this reports rather than reshuffles
 * The auto-planner builds a season from a running goal and a strength cadence, and those have their
 * own reasons for the days they pick — a long run on the preferred long day, strength spaced evenly.
 * Silently moving sessions to satisfy a model whose constants are informed estimates rather than
 * measurements would trade a reason the athlete understands for one they cannot see.
 *
 * So this surfaces the collision and leaves the decision where it belongs. The same information
 * feeds [ReadinessPlanRules] on the day itself, where actual recovery data is available and the
 * advice can be trusted further.
 *
 * ## Projected, not measured
 * Every figure here is derived from *planned* sessions, so it assumes each one happens as written
 * and says nothing about sleep, fuelling or how the athlete actually feels. It is a scheduling
 * check, not a readiness forecast.
 */
object PlannedStrainAdvisor {

    /** Below this, a planned session on that channel is worth flagging. */
    const val CONFLICT_FRESHNESS = 45

    /** A planned session at or above this TSS counts as a real demand on its channels. */
    const val SIGNIFICANT_TSS = 45

    /**
     * Conflicts across [plans], optionally seeded with real strain history so the first days of a
     * plan account for what the athlete has already done.
     */
    fun findConflicts(
        plans: List<TrainingPlan>,
        history: List<DailyStrain> = emptyList()
    ): List<PlanConflict> {
        if (plans.isEmpty()) return emptyList()

        val projected = plans
            .groupBy { it.date }
            .mapValues { (_, dayPlans) -> dayPlans.fold(StrainVector.ZERO) { acc, p -> acc + p.strain() } }
            .map { (date, strain) -> DailyStrain(date, strain) }

        val timeline = (history + projected).sortedBy { it.date }
        val conflicts = mutableListOf<PlanConflict>()

        plans.sortedBy { it.date }.forEach { plan ->
            if (plan.plannedTSS < SIGNIFICANT_TSS) return@forEach

            // State at the *start* of the day: everything before it, so a session is never measured
            // against the load it is itself about to add.
            val before = timeline.filter { it.date.isBefore(plan.date) }
            if (before.isEmpty()) return@forEach
            val state = StrainTimeline.stateAt(before, plan.date)
            if (!state.hasData) return@forEach

            ReadinessPlanRules.limitingChannels(plan.type)
                .mapNotNull { state[it] }
                .filter { it.freshness < CONFLICT_FRESHNESS }
                .minByOrNull { it.freshness }
                ?.let { worst ->
                    conflicts += PlanConflict(
                        date = plan.date,
                        channel = worst.channel,
                        freshness = worst.freshness,
                        plan = plan,
                        message = "${plan.type.readable()} on ${plan.date} lands with " +
                            "${worst.channel.label.lowercase()} at ${worst.freshness}% fresh" +
                            (worst.hoursToFresh?.let { " (~${formatHours(it)} to clear)" } ?: "") +
                            ". Consider moving it a day or easing it."
                    )
                }
        }
        return conflicts
    }

    /** Conflicts as coach warnings, for the screens that already render those. */
    fun asWarnings(conflicts: List<PlanConflict>): List<CoachWarning> = conflicts.map { conflict ->
        CoachWarning(
            type = WarningType.RECOVERY_ADVICE,
            title = "Plan stacks ${conflict.channel.label.lowercase()}",
            message = conflict.message,
            isBlocker = false
        )
    }

    /**
     * Projected strain for a planned session.
     *
     * Uses the same per-discipline vectors a completed session would, so a planned Sunday ride loads
     * the legs exactly as the real one will. What differs is certainty, and that belongs in the
     * copy rather than in a quietly discounted number.
     */
    private fun TrainingPlan.strain(): StrainVector {
        val metabolic = StrainMapper.disciplineVector(type) * plannedTSS.toDouble()
        val impact = plannedDistanceMeters?.let { meters ->
            val km = meters / 1000.0
            when (type) {
                WorkoutType.RUN -> km * PLANNED_IMPACT_PER_KM
                WorkoutType.HIKE -> km * PLANNED_IMPACT_PER_KM * 0.6
                else -> 0.0
            }
        } ?: 0.0
        return metabolic + StrainVector(lowerImpact = impact)
    }

    /**
     * Impact per planned kilometre. A plan has no zone distribution to read, so this is the
     * mid-zone equivalent of what [StrainMapper.impactLoad] computes for a completed run.
     */
    private const val PLANNED_IMPACT_PER_KM = 4.2

    private fun WorkoutType.readable(): String =
        name.lowercase().replaceFirstChar { it.uppercase() }

    private fun formatHours(hours: Int): String =
        if (hours >= 24) "${(hours / 24.0).roundToInt()}d" else "${hours}h"
}
