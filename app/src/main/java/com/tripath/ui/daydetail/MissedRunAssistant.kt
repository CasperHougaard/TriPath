package com.tripath.ui.daydetail

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import java.time.LocalDate

enum class MissedRunAction {
    MOVE_TO_TOMORROW,
    MOVE_TO_CUSTOM_DATE,
    DROP
}

data class MissedRunAssistantState(
    val isEligible: Boolean,
    val actions: List<MissedRunAction> = emptyList()
)

/** Activity types the missed-session assistant can reschedule. */
private val missedAssistantTypes = setOf(WorkoutType.RUN, WorkoutType.STRENGTH)

fun buildMissedRunAssistantState(
    activity: TrainingPlan,
    completedWorkouts: List<WorkoutLog>,
    today: LocalDate = LocalDate.now()
): MissedRunAssistantState {
    // A session counts as "done" when any workout of the same type was logged that day.
    // Strength durations vary, so we intentionally match on type + date only (no length check).
    val isEligible = activity.type in missedAssistantTypes &&
        activity.date.isBefore(today) &&
        completedWorkouts.none { workout ->
            workout.type == activity.type && workout.date == activity.date
        }

    return if (isEligible) {
        MissedRunAssistantState(
            isEligible = true,
            actions = defaultMissedRunActions()
        )
    } else {
        MissedRunAssistantState(isEligible = false)
    }
}

fun defaultMissedRunActions(): List<MissedRunAction> = listOf(
    MissedRunAction.MOVE_TO_TOMORROW,
    MissedRunAction.MOVE_TO_CUSTOM_DATE,
    MissedRunAction.DROP
)

fun missedRunTomorrowDate(today: LocalDate = LocalDate.now()): LocalDate = today.plusDays(1)