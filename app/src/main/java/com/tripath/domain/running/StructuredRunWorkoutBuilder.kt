package com.tripath.domain.running

import kotlin.math.ceil
import kotlin.math.roundToInt

object StructuredRunWorkoutBuilder {
    private const val DEFAULT_PACE_SEC_PER_KM = 420
    private const val MIN_SIMPLE_RUN_MINUTES = 20
    private const val STANDARD_WARM_UP_MINUTES = 10
    private const val STANDARD_COOL_DOWN_MINUTES = 10
    private const val INTERVAL_WORK_MINUTES = 3
    private const val INTERVAL_RECOVERY_MINUTES = 2
    private const val THRESHOLD_WORK_MINUTES = 5
    private const val THRESHOLD_RECOVERY_MINUTES = 2

    fun build(
        sessionType: RunningSessionType,
        plannedDistanceMeters: Int?,
        baselinePaceSecPerKm: Int? = null,
        targetDistanceMeters: Int? = null,
        weekIndex: Int = 1,
        totalWeeks: Int = 1
    ): StructuredRunWorkout {
        val estimatedDurationMinutes = estimateDurationMinutes(
            plannedDistanceMeters = plannedDistanceMeters,
            baselinePaceSecPerKm = baselinePaceSecPerKm,
            sessionType = sessionType
        )

        return when (sessionType) {
            RunningSessionType.EASY -> buildSimpleWorkout(
                title = "Easy Run",
                sessionType = sessionType,
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                stepType = RunWorkoutStepType.EASY,
                descriptionLabel = "Easy aerobic running",
                targetProfile = targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks)
            )

            RunningSessionType.RECOVERY -> buildSimpleWorkout(
                title = "Recovery Run",
                sessionType = sessionType,
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes.coerceAtMost(40),
                stepType = RunWorkoutStepType.VERY_EASY,
                descriptionLabel = "Very easy recovery running",
                targetProfile = targetProfileForRecovery(baselinePaceSecPerKm, weekIndex, totalWeeks)
            )

            RunningSessionType.LONG_RUN -> buildSimpleWorkout(
                title = "Long Run",
                sessionType = sessionType,
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                stepType = RunWorkoutStepType.LONG_AEROBIC,
                descriptionLabel = "Long easy aerobic run",
                targetProfile = targetProfileForLongRun(baselinePaceSecPerKm, weekIndex, totalWeeks)
            )

            RunningSessionType.TEMPO -> buildTempoWorkout(
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                baselinePaceSecPerKm = baselinePaceSecPerKm,
                weekIndex = weekIndex,
                totalWeeks = totalWeeks
            )

            RunningSessionType.THRESHOLD -> buildThresholdWorkout(
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                baselinePaceSecPerKm = baselinePaceSecPerKm,
                weekIndex = weekIndex,
                totalWeeks = totalWeeks
            )

            RunningSessionType.INTERVALS -> buildIntervalsWorkout(
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                baselinePaceSecPerKm = baselinePaceSecPerKm,
                weekIndex = weekIndex,
                totalWeeks = totalWeeks
            )

            RunningSessionType.PROGRESSION -> buildProgressionWorkout(
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                baselinePaceSecPerKm = baselinePaceSecPerKm,
                weekIndex = weekIndex,
                totalWeeks = totalWeeks
            )

            RunningSessionType.RACE_PACE -> buildRacePaceWorkout(
                plannedDistanceMeters = plannedDistanceMeters,
                estimatedDurationMinutes = estimatedDurationMinutes,
                baselinePaceSecPerKm = baselinePaceSecPerKm,
                targetDistanceMeters = targetDistanceMeters,
                weekIndex = weekIndex,
                totalWeeks = totalWeeks
            )
        }
    }

    private fun buildSimpleWorkout(
        title: String,
        sessionType: RunningSessionType,
        plannedDistanceMeters: Int?,
        estimatedDurationMinutes: Int,
        stepType: RunWorkoutStepType,
        descriptionLabel: String,
        targetProfile: StepTargetProfile
    ): StructuredRunWorkout {
        val step = buildStep(
            order = 1,
            type = stepType,
            durationMinutes = estimatedDurationMinutes,
            targetProfile = targetProfile,
            descriptionLabel = descriptionLabel
        )

        return StructuredRunWorkout(
            title = title,
            sessionType = sessionType,
            totalDistanceMeters = plannedDistanceMeters,
            estimatedDurationMinutes = estimatedDurationMinutes,
            steps = listOf(step),
            summaryText = "${estimatedDurationMinutes} min ${descriptionLabel.lowercase()}${summarySuffix(targetProfile)}."
        )
    }

    private fun buildTempoWorkout(
        plannedDistanceMeters: Int?,
        estimatedDurationMinutes: Int,
        baselinePaceSecPerKm: Int?,
        weekIndex: Int,
        totalWeeks: Int
    ): StructuredRunWorkout {
        val tempoMinutes = (estimatedDurationMinutes - STANDARD_WARM_UP_MINUTES - STANDARD_COOL_DOWN_MINUTES)
            .coerceIn(15, 30)
        val warmUp = buildStep(1, RunWorkoutStepType.WARM_UP, STANDARD_WARM_UP_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Easy warm-up")
        val tempo = buildStep(2, RunWorkoutStepType.TEMPO, tempoMinutes, targetProfileForTempo(baselinePaceSecPerKm, weekIndex, totalWeeks), "Tempo block")
        val coolDown = buildStep(3, RunWorkoutStepType.COOL_DOWN, STANDARD_COOL_DOWN_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Cool-down")
        val steps = listOf(warmUp, tempo, coolDown)

        return StructuredRunWorkout(
            title = "Tempo Run",
            sessionType = RunningSessionType.TEMPO,
            totalDistanceMeters = plannedDistanceMeters,
            estimatedDurationMinutes = steps.sumOf { it.durationValue },
            steps = steps,
            summaryText = "10 min easy warm-up${summarySuffix(warmUp)}, ${tempoMinutes} min tempo${summarySuffix(tempo)}, 10 min cool-down${summarySuffix(coolDown)}."
        )
    }

    private fun buildThresholdWorkout(
        plannedDistanceMeters: Int?,
        estimatedDurationMinutes: Int,
        baselinePaceSecPerKm: Int?,
        weekIndex: Int,
        totalWeeks: Int
    ): StructuredRunWorkout {
        val repCount = when {
            estimatedDurationMinutes >= 60 -> 5
            estimatedDurationMinutes >= 48 -> 4
            else -> 3
        }
        val warmUp = buildStep(1, RunWorkoutStepType.WARM_UP, STANDARD_WARM_UP_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Easy warm-up")
        val repTarget = targetProfileForThreshold(baselinePaceSecPerKm, weekIndex, totalWeeks)
        val recoveryTarget = targetProfileForRecovery(baselinePaceSecPerKm, weekIndex, totalWeeks)
        val reps = (0 until repCount).flatMap { index ->
            buildList {
                add(buildStep(index * 2 + 2, RunWorkoutStepType.THRESHOLD, THRESHOLD_WORK_MINUTES, repTarget, "Threshold rep ${index + 1}"))
                if (index < repCount - 1) {
                    add(buildStep(index * 2 + 3, RunWorkoutStepType.RECOVERY_JOG, THRESHOLD_RECOVERY_MINUTES, recoveryTarget, "Easy jog recovery"))
                }
            }
        }
        val coolDown = buildStep(repCount * 2 + 1, RunWorkoutStepType.COOL_DOWN, STANDARD_COOL_DOWN_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Cool-down")
        val steps = listOf(warmUp) + reps + coolDown

        return StructuredRunWorkout(
            title = "Threshold Run",
            sessionType = RunningSessionType.THRESHOLD,
            totalDistanceMeters = plannedDistanceMeters,
            estimatedDurationMinutes = steps.sumOf { it.durationValue },
            steps = steps,
            summaryText = "10 min easy warm-up${summarySuffix(warmUp)}, then ${repCount} x ${THRESHOLD_WORK_MINUTES} min threshold${summarySuffix(repTarget)} with ${THRESHOLD_RECOVERY_MINUTES} min easy jog recovery${summarySuffix(recoveryTarget)}, then 10 min cool-down${summarySuffix(coolDown)}."
        )
    }

    private fun buildIntervalsWorkout(
        plannedDistanceMeters: Int?,
        estimatedDurationMinutes: Int,
        baselinePaceSecPerKm: Int?,
        weekIndex: Int,
        totalWeeks: Int
    ): StructuredRunWorkout {
        val repCount = when {
            estimatedDurationMinutes >= 55 -> 6
            estimatedDurationMinutes >= 45 -> 5
            else -> 4
        }
        val warmUp = buildStep(1, RunWorkoutStepType.WARM_UP, STANDARD_WARM_UP_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Easy warm-up")
        val intervalTarget = targetProfileForIntervals(baselinePaceSecPerKm, weekIndex, totalWeeks)
        val recoveryTarget = targetProfileForRecovery(baselinePaceSecPerKm, weekIndex, totalWeeks)
        val reps = (0 until repCount).flatMap { index ->
            buildList {
                add(buildStep(index * 2 + 2, RunWorkoutStepType.INTERVAL, INTERVAL_WORK_MINUTES, intervalTarget, "Hard interval ${index + 1}"))
                if (index < repCount - 1) {
                    add(buildStep(index * 2 + 3, RunWorkoutStepType.RECOVERY_JOG, INTERVAL_RECOVERY_MINUTES, recoveryTarget, "Easy jog recovery"))
                }
            }
        }
        val coolDown = buildStep(repCount * 2 + 1, RunWorkoutStepType.COOL_DOWN, STANDARD_COOL_DOWN_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Cool-down")
        val steps = listOf(warmUp) + reps + coolDown

        return StructuredRunWorkout(
            title = "Intervals",
            sessionType = RunningSessionType.INTERVALS,
            totalDistanceMeters = plannedDistanceMeters,
            estimatedDurationMinutes = steps.sumOf { it.durationValue },
            steps = steps,
            summaryText = "10 min easy warm-up${summarySuffix(warmUp)}, then ${repCount} x ${INTERVAL_WORK_MINUTES} min hard${summarySuffix(intervalTarget)} with ${INTERVAL_RECOVERY_MINUTES} min easy jog recovery${summarySuffix(recoveryTarget)}, then 10 min cool-down${summarySuffix(coolDown)}."
        )
    }

    private fun buildProgressionWorkout(
        plannedDistanceMeters: Int?,
        estimatedDurationMinutes: Int,
        baselinePaceSecPerKm: Int?,
        weekIndex: Int,
        totalWeeks: Int
    ): StructuredRunWorkout {
        val totalMinutes = estimatedDurationMinutes.coerceAtLeast(30)
        val easyMinutes = (totalMinutes * 0.35).roundToInt().coerceAtLeast(10)
        val steadyMinutes = (totalMinutes * 0.35).roundToInt().coerceAtLeast(10)
        val hardMinutes = (totalMinutes - easyMinutes - steadyMinutes).coerceAtLeast(8)
        val easy = buildStep(1, RunWorkoutStepType.EASY, easyMinutes, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Start easy")
        val steady = buildStep(2, RunWorkoutStepType.STEADY, steadyMinutes, targetProfileForSteady(baselinePaceSecPerKm, weekIndex, totalWeeks), "Settle into steady running")
        val hard = buildStep(3, RunWorkoutStepType.COMFORTABLY_HARD, hardMinutes, targetProfileForProgressionFinish(baselinePaceSecPerKm, weekIndex, totalWeeks), "Finish comfortably hard")
        val steps = listOf(easy, steady, hard)

        return StructuredRunWorkout(
            title = "Progression Run",
            sessionType = RunningSessionType.PROGRESSION,
            totalDistanceMeters = plannedDistanceMeters,
            estimatedDurationMinutes = steps.sumOf { it.durationValue },
            steps = steps,
            summaryText = if (baselinePaceSecPerKm != null) {
                "Start easy${summarySuffix(easy)}, settle into steady running${summarySuffix(steady)}, and finish comfortably hard${summarySuffix(hard)}."
            } else {
                "Start easy${summarySuffix(easy)}, settle into steady running${summarySuffix(steady)}, and finish comfortably hard${summarySuffix(hard)}."
            }
        )
    }

    private fun buildRacePaceWorkout(
        plannedDistanceMeters: Int?,
        estimatedDurationMinutes: Int,
        baselinePaceSecPerKm: Int?,
        targetDistanceMeters: Int?,
        weekIndex: Int,
        totalWeeks: Int
    ): StructuredRunWorkout {
        val racePaceMinutes = (estimatedDurationMinutes - STANDARD_WARM_UP_MINUTES - STANDARD_COOL_DOWN_MINUTES)
            .coerceIn(15, when {
                (targetDistanceMeters ?: plannedDistanceMeters ?: 0) >= 21_000 -> 25
                else -> 20
            })
        val warmUp = buildStep(1, RunWorkoutStepType.WARM_UP, STANDARD_WARM_UP_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Easy warm-up")
        val racePace = buildStep(2, RunWorkoutStepType.RACE_PACE, racePaceMinutes, targetProfileForRacePace(baselinePaceSecPerKm, weekIndex, totalWeeks), "Race pace block")
        val coolDown = buildStep(3, RunWorkoutStepType.COOL_DOWN, STANDARD_COOL_DOWN_MINUTES, targetProfileForEasy(baselinePaceSecPerKm, weekIndex, totalWeeks), "Cool-down")
        val steps = listOf(warmUp, racePace, coolDown)

        return StructuredRunWorkout(
            title = "Race Pace Run",
            sessionType = RunningSessionType.RACE_PACE,
            totalDistanceMeters = plannedDistanceMeters,
            estimatedDurationMinutes = steps.sumOf { it.durationValue },
            steps = steps,
            summaryText = "10 min easy warm-up${summarySuffix(warmUp)}, ${racePaceMinutes} min race pace${summarySuffix(racePace)}, 10 min cool-down${summarySuffix(coolDown)}."
        )
    }

    private fun estimateDurationMinutes(
        plannedDistanceMeters: Int?,
        baselinePaceSecPerKm: Int?,
        sessionType: RunningSessionType
    ): Int {
        val estimated = plannedDistanceMeters?.let { distanceMeters ->
            ceil((distanceMeters / 1000.0) * (baselinePaceSecPerKm ?: DEFAULT_PACE_SEC_PER_KM) / 60.0).toInt()
        }

        val defaultMinutes = when (sessionType) {
            RunningSessionType.EASY -> 40
            RunningSessionType.RECOVERY -> 30
            RunningSessionType.LONG_RUN -> 75
            RunningSessionType.TEMPO -> 40
            RunningSessionType.THRESHOLD -> 45
            RunningSessionType.INTERVALS -> 40
            RunningSessionType.PROGRESSION -> 40
            RunningSessionType.RACE_PACE -> 40
        }

        return (estimated ?: defaultMinutes).coerceAtLeast(MIN_SIMPLE_RUN_MINUTES)
    }

    private fun buildStep(
        order: Int,
        type: RunWorkoutStepType,
        durationMinutes: Int,
        targetProfile: StepTargetProfile,
        descriptionLabel: String
    ): RunWorkoutStep {
        return RunWorkoutStep(
            order = order,
            type = type,
            durationType = RunStepDurationType.TIME,
            durationValue = durationMinutes,
            targetType = targetProfile.type,
            targetLow = targetProfile.low,
            targetHigh = targetProfile.high,
            description = "$descriptionLabel${stepTargetSuffix(targetProfile)}"
        )
    }

    private fun summarySuffix(step: RunWorkoutStep): String = summarySuffix(
        StepTargetProfile(step.targetType, step.targetLow, step.targetHigh)
    )

    private fun summarySuffix(targetProfile: StepTargetProfile): String {
        return when (targetProfile.type) {
            RunStepTargetType.NONE -> ""
            RunStepTargetType.PACE -> " at ${formatPaceRange(targetProfile.low, targetProfile.high)}"
            RunStepTargetType.EFFORT -> " at ${formatEffortRange(targetProfile.low, targetProfile.high)}"
            RunStepTargetType.HEART_RATE -> " at ${targetProfile.low}-${targetProfile.high} bpm"
        }
    }

    private fun stepTargetSuffix(targetProfile: StepTargetProfile): String {
        return when (targetProfile.type) {
            RunStepTargetType.NONE -> ""
            RunStepTargetType.PACE -> " (${formatPaceRange(targetProfile.low, targetProfile.high)})"
            RunStepTargetType.EFFORT -> " (${formatEffortRange(targetProfile.low, targetProfile.high)})"
            RunStepTargetType.HEART_RATE -> " (${targetProfile.low}-${targetProfile.high} bpm)"
        }
    }

    private fun formatPaceRange(low: Int?, high: Int?): String {
        return "${formatPace(low)}-${formatPace(high)}/km"
    }

    private fun formatPace(secondsPerKm: Int?): String {
        val totalSeconds = secondsPerKm ?: return "--:--"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun formatEffortRange(low: Int?, high: Int?): String {
        return "RPE ${low ?: 0}-${high ?: 0}"
    }

    private fun targetProfileForEasy(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.EASY, weekIndex, totalWeeks, 20, 75, 3, 4)

    private fun targetProfileForLongRun(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.LONG_RUN, weekIndex, totalWeeks, 20, 75, 3, 4)

    private fun targetProfileForRecovery(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.RECOVERY, weekIndex, totalWeeks, 45, 90, 2, 3)

    private fun targetProfileForTempo(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.TEMPO, weekIndex, totalWeeks, -15, 5, 6, 7)

    private fun targetProfileForThreshold(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.THRESHOLD, weekIndex, totalWeeks, -25, -10, 7, 8)

    private fun targetProfileForIntervals(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.INTERVALS, weekIndex, totalWeeks, -40, -20, 8, 9)

    private fun targetProfileForSteady(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.PROGRESSION, weekIndex, totalWeeks, 5, 20, 5, 6)

    private fun targetProfileForProgressionFinish(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.PROGRESSION, weekIndex, totalWeeks, -10, 5, 6, 7)

    private fun targetProfileForRacePace(baselinePaceSecPerKm: Int?, weekIndex: Int, totalWeeks: Int): StepTargetProfile =
        paceOrEffortProfile(baselinePaceSecPerKm, RunningSessionType.RACE_PACE, weekIndex, totalWeeks, -10, 5, 7, 8)

    private fun paceOrEffortProfile(
        baselinePaceSecPerKm: Int?,
        sessionType: RunningSessionType,
        weekIndex: Int,
        totalWeeks: Int,
        lowOffset: Int,
        highOffset: Int,
        effortLow: Int,
        effortHigh: Int
    ): StepTargetProfile {
        val baseline = baselinePaceSecPerKm
        if (baseline == null) {
            return StepTargetProfile(RunStepTargetType.EFFORT, effortLow, effortHigh)
        }

        val adjustedRange = RunningPaceProgressionRules.adjustRange(
            baselinePaceSecPerKm = baseline,
            sessionType = sessionType,
            weekIndex = weekIndex,
            totalWeeks = totalWeeks,
            baseLow = baseline + lowOffset,
            baseHigh = baseline + highOffset
        )

        return StepTargetProfile(
            type = RunStepTargetType.PACE,
            low = adjustedRange?.low,
            high = adjustedRange?.high
        )
    }

    private data class StepTargetProfile(
        val type: RunStepTargetType,
        val low: Int? = null,
        val high: Int? = null
    )
}