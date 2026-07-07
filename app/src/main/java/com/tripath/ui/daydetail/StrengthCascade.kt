package com.tripath.ui.daydetail

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Computes the plan updates for moving a strength session and sliding the schedule forward.
 *
 * Moving [activity] to [newDate] shifts every later strength session by the same delta so the
 * every-3rd-day cadence is preserved. When [considerRuns] is true, later runs shift by the same
 * delta too, keeping them the day before their strength session.
 *
 * @return the plans (with updated dates) that should be persisted, including the moved [activity].
 *         Empty when the move is a no-op.
 */
fun cascadeStrengthMoveUpdates(
    activity: TrainingPlan,
    newDate: LocalDate,
    allPlans: List<TrainingPlan>,
    considerRuns: Boolean
): List<TrainingPlan> {
    val delta = ChronoUnit.DAYS.between(activity.date, newDate)
    if (delta == 0L) return emptyList()

    val shifted = allPlans
        .filter { it.id != activity.id && it.date.isAfter(activity.date) }
        .filter { it.type == WorkoutType.STRENGTH || (considerRuns && it.type == WorkoutType.RUN) }
        .map { it.copy(date = it.date.plusDays(delta)) }

    return shifted + activity.copy(date = newDate)
}
