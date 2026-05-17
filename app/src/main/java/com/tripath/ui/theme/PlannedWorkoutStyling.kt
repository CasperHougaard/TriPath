package com.tripath.ui.theme

import androidx.compose.ui.graphics.Color
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.Intensity
import com.tripath.data.model.WorkoutType
import com.tripath.domain.running.RunningSessionType
import com.tripath.domain.running.runningSessionTypeFromPlanSubType

fun TrainingPlan.plannedContentTint(onSurfaceColor: Color): Color {
    return onSurfaceColor.copy(alpha = plannedContentAlpha())
}

private fun TrainingPlan.plannedContentAlpha(): Float {
    return when (type) {
        WorkoutType.RUN -> runAlphaFromSubType()
        WorkoutType.STRENGTH -> when (intensity) {
            Intensity.HEAVY, Intensity.HIGH -> 0.95f
            Intensity.MODERATE -> 0.85f
            Intensity.LIGHT, Intensity.LOW, null -> 0.75f
        }
        else -> 0.8f
    }
}

private fun TrainingPlan.runAlphaFromSubType(): Float {
    return when (runningSessionTypeFromPlanSubType(subType)) {
        RunningSessionType.RECOVERY -> 0.58f
        RunningSessionType.EASY -> 0.68f
        RunningSessionType.LONG_RUN -> 0.74f
        RunningSessionType.PROGRESSION -> 0.84f
        RunningSessionType.TEMPO -> 0.9f
        RunningSessionType.THRESHOLD -> 0.94f
        RunningSessionType.RACE_PACE -> 0.96f
        RunningSessionType.INTERVALS -> 1.0f
        null -> 0.8f
    }
}