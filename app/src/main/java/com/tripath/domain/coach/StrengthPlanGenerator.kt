package com.tripath.domain.coach

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Generates strength sessions on a simple every-3rd-day cadence (2 rest days between sessions).
 *
 * Sessions are fixed at 70 min / MODERATE intensity / FULL_BODY focus — real durations vary, so the
 * planner never tries to length-match against logged strength workouts.
 */
object StrengthPlanGenerator {
    /** Days between consecutive strength sessions (session, rest, rest, session -> every 3rd day). */
    const val CADENCE_DAYS = 3
    const val SESSION_DURATION_MINUTES = 70

    /** ~70 min at a moderate ~45 TSS/hr, midway between the profile's heavy (60) and light (30) defaults. */
    const val SESSION_PLANNED_TSS = 52

    fun generateStrengthPlans(
        firstWorkoutDate: LocalDate,
        planStartDate: LocalDate,
        weeks: Int,
        // The earliest day a session may land on. Strength runs on its own cadence and is not tied to
        // the running plan's week-aligned start, so it can begin before [planStartDate] (e.g. today)
        // when the user chose an earlier first workout date. Defaults to [planStartDate].
        earliestSessionDate: LocalDate = planStartDate
    ): List<TrainingPlan> {
        if (weeks <= 0) return emptyList()
        val planEndExclusive = planStartDate.plusWeeks(weeks.toLong())

        // Roll the cadence forward only far enough to reach the first allowed day, so a chosen first
        // workout date is honored as-is instead of being pushed to the running plan's week start.
        var sessionDate = firstWorkoutDate
        if (sessionDate.isBefore(earliestSessionDate)) {
            val daysBehind = ChronoUnit.DAYS.between(sessionDate, earliestSessionDate)
            val steps = (daysBehind + CADENCE_DAYS - 1) / CADENCE_DAYS
            sessionDate = sessionDate.plusDays(steps * CADENCE_DAYS)
        }

        val plans = mutableListOf<TrainingPlan>()
        while (sessionDate.isBefore(planEndExclusive)) {
            plans += TrainingPlan(
                date = sessionDate,
                type = WorkoutType.STRENGTH,
                subType = "Strength",
                durationMinutes = SESSION_DURATION_MINUTES,
                plannedTSS = SESSION_PLANNED_TSS,
                plannedDistanceMeters = null,
                strengthFocus = StrengthFocus.FULL_BODY,
                intensity = Intensity.MODERATE
            )
            sessionDate = sessionDate.plusDays(CADENCE_DAYS.toLong())
        }
        return plans
    }
}
