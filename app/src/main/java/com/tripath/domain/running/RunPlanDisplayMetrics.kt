package com.tripath.domain.running

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType

/**
 * Display-ready metrics derived from a TrainingPlan using structured workout logic.
 * The structured run workout (with session-type-specific pace ranges) is the source of truth.
 */
data class RunPlanDisplayMetrics(
    val durationMinutes: Int,
    val distanceMeters: Int?,
    val tss: Int
) {
    companion object {
        /**
         * Compute display metrics for a TrainingPlan.
         * For RUN workouts with a known session type and threshold pace, builds the structured
         * workout and derives metrics from step pace ranges.
         * For non-run or unknown workouts, returns stored plan values.
         */
        fun fromPlan(plan: TrainingPlan, thresholdRunPace: Int? = null): RunPlanDisplayMetrics {
            if (plan.type != WorkoutType.RUN || thresholdRunPace == null) {
                return RunPlanDisplayMetrics(plan.durationMinutes, plan.plannedDistanceMeters, plan.plannedTSS)
            }

            val sessionType = runningSessionTypeFromPlanSubType(plan.subType)
                ?: return RunPlanDisplayMetrics(plan.durationMinutes, plan.plannedDistanceMeters, plan.plannedTSS)

            val workout = StructuredRunWorkoutBuilder.build(
                sessionType = sessionType,
                plannedDistanceMeters = plan.plannedDistanceMeters,
                baselinePaceSecPerKm = thresholdRunPace,
                targetDistanceMeters = plan.plannedDistanceMeters
            )

            return fromStructuredWorkout(workout)
        }

        /**
         * Compute display metrics from a pre-built StructuredRunWorkout.
         * Duration = sum of step durations.
         * Distance = sum of (step_duration / avg_pace) per segment with pace targets.
         * TSS = duration * session-type multiplier.
         */
        fun fromStructuredWorkout(workout: StructuredRunWorkout): RunPlanDisplayMetrics {
            val totalDuration = workout.steps.sumOf { it.durationValue }

            val hasAnyPaceTarget = workout.steps.any {
                it.targetType == RunStepTargetType.PACE && it.targetLow != null && it.targetHigh != null
            }

            val distanceMeters = if (hasAnyPaceTarget) {
                var totalDistanceM = 0.0
                for (step in workout.steps) {
                    if (step.durationType == RunStepDurationType.TIME) {
                        val stepDurationSec = step.durationValue * 60.0
                        val avgPaceSecPerKm = if (step.targetType == RunStepTargetType.PACE &&
                            step.targetLow != null && step.targetHigh != null
                        ) {
                            (step.targetLow + step.targetHigh) / 2.0
                        } else {
                            420.0 // fallback 7 min/km
                        }
                        totalDistanceM += (stepDurationSec / avgPaceSecPerKm) * 1000.0
                    }
                }
                totalDistanceM.toInt()
            } else {
                workout.totalDistanceMeters
            }

            val tssMultiplier = when (workout.sessionType) {
                RunningSessionType.EASY -> 1.0
                RunningSessionType.RECOVERY -> 0.85
                RunningSessionType.LONG_RUN -> 1.2
                RunningSessionType.TEMPO -> 1.1
                RunningSessionType.THRESHOLD -> 1.15
                RunningSessionType.INTERVALS -> 1.2
                RunningSessionType.PROGRESSION -> 1.1
                RunningSessionType.RACE_PACE -> 1.15
            }
            val tss = (totalDuration * tssMultiplier).toInt()

            return RunPlanDisplayMetrics(
                durationMinutes = totalDuration,
                distanceMeters = distanceMeters,
                tss = tss
            )
        }
    }
}
