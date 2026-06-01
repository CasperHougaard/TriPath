package com.tripath.domain.running

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object RunningProgressionRules {
    // Constants
    private const val RECOVERY_WEEK_FRACTION = 0.8
    private const val PERFORMANCE_RECOVERY_FRACTION = 0.9
    private const val EASY_RUN_FRACTION = 0.45
    private const val TEMPO_RUN_FRACTION = 0.65
    private const val THRESHOLD_RUN_FRACTION = 0.55
    private const val RACE_PACE_RUN_FRACTION = 0.5
    private const val MAX_WEEKLY_PROGRESS = 0.15 // 15% max safe increase
    private const val DEFAULT_BASELINE_FRACTION = 0.4 // fallback baseline = 40% of target
    private const val PERFORMANCE_MODE_LONG_RUN_THRESHOLD = 0.85
    private const val PERFORMANCE_MODE_SUPPORTING_THRESHOLD = 0.75
    private const val PERFORMANCE_MODE_WEEKLY_VOLUME_MULTIPLIER = 1.75
    private const val PERFORMANCE_EXTENSION_FRACTION = 1.05
    private const val PERFORMANCE_EXTENSION_CAP = 1.1
    private const val MIN_WEEKS = 4
    private const val MIN_COMPLETE_DISTANCE_WEEKS = 2
    private const val NORMAL_GOAL_HORIZON_WEEKS = 24
    private const val MAX_WARNING_GOAL_HORIZON_WEEKS = 52
    private const val MIN_LONG_RUN_METERS = 2000
    private const val ROUND_TO = 100

    fun generateWeeklyTargets(
        goal: RunningGoal,
        planStartDate: LocalDate,
        openEndedWeeks: Int = 4
    ): RunningProgressionResult {
        return when (goal.type) {
            RunningGoalType.COMPLETE_DISTANCE -> generateCompleteDistance(goal, planStartDate)
            RunningGoalType.CONSISTENCY -> generateConsistency(goal, planStartDate, openEndedWeeks)
            RunningGoalType.ENDURANCE -> generateEndurance(goal, planStartDate, openEndedWeeks)
        }
    }

    private fun generateCompleteDistance(goal: RunningGoal, planStartDate: LocalDate): RunningProgressionResult {
        val warnings = mutableListOf<RunningProgressionWarning>()
        val maxProgress = goal.maxWeeklyProgressPercent?.toDouble() ?: MAX_WEEKLY_PROGRESS
        val targetDistance = goal.targetDistanceMeters
        val targetDate = goal.targetDate
        val runsPerWeek = goal.runsPerWeek ?: 3
        val baseline = goal.baselineLongestRunMeters
        val baselineWeekly = goal.baselineWeeklyRunMeters

        if (targetDistance == null || targetDate == null) {
            return RunningProgressionResult(emptyList(), listOf(RunningProgressionWarning.MISSING_BASELINE_DISTANCE))
        }
        if (!planStartDate.isBefore(targetDate)) {
            return RunningProgressionResult(emptyList(), listOf(RunningProgressionWarning.TARGET_DATE_TOO_SOON))
        }

        val rawWeeksUntilGoal = ceil(ChronoUnit.DAYS.between(planStartDate, targetDate) / 7.0).toInt()
        if (rawWeeksUntilGoal < MIN_COMPLETE_DISTANCE_WEEKS) {
            return RunningProgressionResult(emptyList(), listOf(RunningProgressionWarning.TARGET_DATE_TOO_SOON))
        }
        if (rawWeeksUntilGoal > MAX_WARNING_GOAL_HORIZON_WEEKS) {
            return RunningProgressionResult(emptyList(), listOf(RunningProgressionWarning.TARGET_DATE_TOO_FAR))
        }
        if (rawWeeksUntilGoal > NORMAL_GOAL_HORIZON_WEEKS) {
            warnings += RunningProgressionWarning.LONG_GOAL_HORIZON
        }

        val weeks = max(rawWeeksUntilGoal, MIN_WEEKS)
        var startLongRun = when {
            baseline != null && baseline > 0 -> baseline
            baselineWeekly != null && baselineWeekly > 0 -> (baselineWeekly / runsPerWeek)
            else -> (targetDistance * DEFAULT_BASELINE_FRACTION).roundToInt()
        }
        if (startLongRun < MIN_LONG_RUN_METERS) startLongRun = MIN_LONG_RUN_METERS
        if (baseline == null && baselineWeekly == null) warnings += RunningProgressionWarning.MISSING_BASELINE_DISTANCE

        val usePerformanceMode = shouldUsePerformanceFocusedMode(
            baselineLongestRunMeters = baseline,
            baselineWeeklyRunMeters = baselineWeekly,
            targetDistanceMeters = targetDistance,
            runsPerWeek = runsPerWeek
        )

        if (usePerformanceMode) {
            if (baseline != null && baseline >= targetDistance) {
                warnings += RunningProgressionWarning.TARGET_ALREADY_REACHED
            }
            return generatePerformanceFocusedCompleteDistance(
                goal = goal,
                planStartDate = planStartDate,
                weeks = weeks,
                startLongRun = startLongRun,
                targetDistance = targetDistance,
                runsPerWeek = runsPerWeek,
                warnings = warnings.distinct()
            )
        }

        if (startLongRun >= targetDistance) {
            warnings += RunningProgressionWarning.TARGET_ALREADY_REACHED
            // Generate maintenance plan
            val weekTargets = (1..weeks).map { weekIdx ->
                val isRecovery = (weekIdx % 4 == 0) && (weekIdx != weeks)
                val longRun = roundToNearest(startLongRun, ROUND_TO)
                val easyRun = if (runsPerWeek > 1) roundToNearest((longRun * EASY_RUN_FRACTION).toInt(), ROUND_TO) else null
                val sessionDistances =
                    if (runsPerWeek == 1) listOf(longRun)
                    else List(runsPerWeek - 1) { easyRun!! } + longRun
                val sessionTypes =
                    if (runsPerWeek == 1) listOf(RunningSessionType.LONG_RUN)
                    else List(runsPerWeek - 1) { RunningSessionType.EASY } + RunningSessionType.LONG_RUN
                RunningWeekTarget(
                    weekIndex = weekIdx,
                    weekStartDate = planStartDate.plusWeeks((weekIdx - 1).toLong()),
                    isRecoveryWeek = isRecovery,
                    runsPerWeek = runsPerWeek,
                    sessionDistancesMeters = sessionDistances,
                    sessionTypes = sessionTypes,
                    progressionMode = RunningProgressionMode.DISTANCE_BUILDING
                )
            }
            return RunningProgressionResult(weekTargets, warnings)
        }

        // Progression
        val weekTargets = mutableListOf<RunningWeekTarget>()
        // Determine if the goal requires aggressive progression by checking whether the
        // smooth build rate (over non-recovery weeks only) exceeds the safe threshold.
        // This avoids false positives from post-recovery catch-up steps.
        val buildWeekCount = (1..weeks).count { w -> !((w % 4 == 0) && (w != weeks)) }
        val neededRate = Math.pow(targetDistance.toDouble() / startLongRun, 1.0 / buildWeekCount) - 1
        if (neededRate > maxProgress) {
            warnings += RunningProgressionWarning.AGGRESSIVE_PROGRESSION_REQUIRED
        }
        var prevLongRun = startLongRun.toDouble()
        val totalProgressionWeeks = weeks - 1
        for (weekIdx in 1..weeks) {
            val isRecovery = (weekIdx % 4 == 0) && (weekIdx != weeks)
            val isFinal = weekIdx == weeks
            val longRun = when {
                isFinal -> targetDistance
                isRecovery -> roundToNearest((prevLongRun * RECOVERY_WEEK_FRACTION).toInt(), ROUND_TO)
                else -> {
                    val remaining = targetDistance - prevLongRun
                    val stepsLeft = max(1, totalProgressionWeeks - (weekIdx - 1))
                    val next = prevLongRun + (remaining / stepsLeft)
                    val maxAllowed = prevLongRun * (1 + maxProgress)
                    val safeNext = minOf(next, maxAllowed, targetDistance.toDouble())
                    roundToNearest(safeNext.toInt(), ROUND_TO)
                }
            }
            val easyRun = if (runsPerWeek > 1) roundToNearest((longRun * EASY_RUN_FRACTION).toInt(), ROUND_TO) else null
            val sessionDistances =
                if (runsPerWeek == 1) listOf(longRun)
                else List(runsPerWeek - 1) { easyRun!! } + longRun
            val sessionTypes =
                if (runsPerWeek == 1) listOf(RunningSessionType.LONG_RUN)
                else List(runsPerWeek - 1) { RunningSessionType.EASY } + RunningSessionType.LONG_RUN
            weekTargets += RunningWeekTarget(
                weekIndex = weekIdx,
                weekStartDate = planStartDate.plusWeeks((weekIdx - 1).toLong()),
                isRecoveryWeek = isRecovery,
                runsPerWeek = runsPerWeek,
                sessionDistancesMeters = sessionDistances,
                sessionTypes = sessionTypes,
                progressionMode = RunningProgressionMode.DISTANCE_BUILDING
            )
            prevLongRun = longRun.toDouble()
        }
        return RunningProgressionResult(weekTargets, warnings.distinct())
    }

    private fun generateConsistency(goal: RunningGoal, planStartDate: LocalDate, openEndedWeeks: Int): RunningProgressionResult {
        val runsPerWeek = goal.runsPerWeek ?: 3
        val baseline = goal.baselineLongestRunMeters
        val sessionDistance = baseline?.let { roundToNearest((it * EASY_RUN_FRACTION).toInt(), ROUND_TO) }
        val weekTargets = (1..openEndedWeeks).map { weekIdx ->
            val isRecovery = (weekIdx % 4 == 0)
            val sessionDistances = if (sessionDistance != null)
                List(runsPerWeek) { sessionDistance } else emptyList()
            val sessionTypes = if (sessionDistance != null) {
                List(runsPerWeek) { RunningSessionType.EASY }
            } else {
                emptyList()
            }
            RunningWeekTarget(
                weekIndex = weekIdx,
                weekStartDate = planStartDate.plusWeeks((weekIdx - 1).toLong()),
                isRecoveryWeek = isRecovery,
                runsPerWeek = runsPerWeek,
                sessionDistancesMeters = sessionDistances,
                sessionTypes = sessionTypes,
                progressionMode = RunningProgressionMode.DISTANCE_BUILDING
            )
        }
        return RunningProgressionResult(weekTargets)
    }

    private fun generateEndurance(goal: RunningGoal, planStartDate: LocalDate, openEndedWeeks: Int): RunningProgressionResult {
        val warnings = mutableListOf<RunningProgressionWarning>()
        val maxProgress = goal.maxWeeklyProgressPercent?.toDouble() ?: MAX_WEEKLY_PROGRESS
        val runsPerWeek = goal.runsPerWeek ?: 3
        val baseline = goal.baselineLongestRunMeters
        if (baseline == null) {
            warnings += RunningProgressionWarning.MISSING_BASELINE_DISTANCE
            val weekTargets = (1..openEndedWeeks).map { weekIdx ->
                RunningWeekTarget(
                    weekIndex = weekIdx,
                    weekStartDate = planStartDate.plusWeeks((weekIdx - 1).toLong()),
                    isRecoveryWeek = (weekIdx % 4 == 0),
                    runsPerWeek = runsPerWeek,
                    sessionDistancesMeters = emptyList(),
                    sessionTypes = emptyList(),
                    progressionMode = RunningProgressionMode.DISTANCE_BUILDING
                )
            }
            return RunningProgressionResult(weekTargets, warnings)
        }
        // Gradually increase long run
        val weekTargets = mutableListOf<RunningWeekTarget>()
        var prevLongRun = baseline.toDouble()
        for (weekIdx in 1..openEndedWeeks) {
            val isRecovery = (weekIdx % 4 == 0)
            val longRun = if (isRecovery) roundToNearest((prevLongRun * RECOVERY_WEEK_FRACTION).toInt(), ROUND_TO)
                          else roundToNearest((prevLongRun * (1 + maxProgress)).toInt(), ROUND_TO)
            val easyRun = if (runsPerWeek > 1) roundToNearest((longRun * EASY_RUN_FRACTION).toInt(), ROUND_TO) else null
            val sessionDistances =
                if (runsPerWeek == 1) listOf(longRun)
                else List(runsPerWeek - 1) { easyRun!! } + longRun
            val sessionTypes =
                if (runsPerWeek == 1) listOf(RunningSessionType.LONG_RUN)
                else List(runsPerWeek - 1) { RunningSessionType.EASY } + RunningSessionType.LONG_RUN
            weekTargets += RunningWeekTarget(
                weekIndex = weekIdx,
                weekStartDate = planStartDate.plusWeeks((weekIdx - 1).toLong()),
                isRecoveryWeek = isRecovery,
                runsPerWeek = runsPerWeek,
                sessionDistancesMeters = sessionDistances,
                sessionTypes = sessionTypes,
                progressionMode = RunningProgressionMode.DISTANCE_BUILDING
            )
            prevLongRun = longRun.toDouble()
        }
        return RunningProgressionResult(weekTargets, warnings)
    }

    private fun shouldUsePerformanceFocusedMode(
        baselineLongestRunMeters: Int?,
        baselineWeeklyRunMeters: Int?,
        targetDistanceMeters: Int,
        runsPerWeek: Int
    ): Boolean {
        val longestRun = baselineLongestRunMeters ?: return false
        if (longestRun >= (targetDistanceMeters * PERFORMANCE_MODE_LONG_RUN_THRESHOLD).toInt()) {
            return true
        }

        return longestRun >= (targetDistanceMeters * PERFORMANCE_MODE_SUPPORTING_THRESHOLD).toInt() &&
            baselineWeeklyRunMeters != null &&
            baselineWeeklyRunMeters >= (targetDistanceMeters * maxOf(1.0, runsPerWeek * PERFORMANCE_MODE_WEEKLY_VOLUME_MULTIPLIER / 3.0)).toInt()
    }

    private fun generatePerformanceFocusedCompleteDistance(
        goal: RunningGoal,
        planStartDate: LocalDate,
        weeks: Int,
        startLongRun: Int,
        targetDistance: Int,
        runsPerWeek: Int,
        warnings: List<RunningProgressionWarning>
    ): RunningProgressionResult {
        val baseLongRun = roundToNearest(
            max(startLongRun, (targetDistance * PERFORMANCE_MODE_LONG_RUN_THRESHOLD).roundToInt()),
            ROUND_TO
        )
        val peakLongRun = roundToNearest(
            max(
                baseLongRun,
                minOf(
                    max(targetDistance, (baseLongRun * PERFORMANCE_EXTENSION_FRACTION).roundToInt()),
                    (targetDistance * PERFORMANCE_EXTENSION_CAP).roundToInt()
                )
            ),
            ROUND_TO
        )

        val weekTargets = (1..weeks).map { weekIdx ->
            val isRecovery = (weekIdx % 4 == 0) && (weekIdx != weeks)
            val longRun = when {
                isRecovery -> roundToNearest((baseLongRun * PERFORMANCE_RECOVERY_FRACTION).roundToInt(), ROUND_TO)
                weekIdx % 2 == 0 || weekIdx == weeks -> peakLongRun
                else -> baseLongRun
            }
            val sessions = buildPerformanceFocusedSessions(
                runsPerWeek = runsPerWeek,
                longRunMeters = longRun,
                targetDistanceMeters = targetDistance,
                weekIndex = weekIdx,
                isRecoveryWeek = isRecovery
            )

            RunningWeekTarget(
                weekIndex = weekIdx,
                weekStartDate = planStartDate.plusWeeks((weekIdx - 1).toLong()),
                isRecoveryWeek = isRecovery,
                runsPerWeek = runsPerWeek,
                sessionDistancesMeters = sessions.map { it.distanceMeters },
                sessionTypes = sessions.map { it.type },
                progressionMode = RunningProgressionMode.PERFORMANCE_FOCUSED
            )
        }

        return RunningProgressionResult(weekTargets, warnings)
    }

    private fun buildPerformanceFocusedSessions(
        runsPerWeek: Int,
        longRunMeters: Int,
        targetDistanceMeters: Int,
        weekIndex: Int,
        isRecoveryWeek: Boolean
    ): List<RunningSessionTarget> {
        if (runsPerWeek <= 0) return emptyList()
        if (runsPerWeek == 1) {
            return listOf(RunningSessionTarget(longRunMeters, RunningSessionType.LONG_RUN))
        }

        val easyRun = roundToNearest((longRunMeters * EASY_RUN_FRACTION).roundToInt(), ROUND_TO)
        val tempoRun = roundToNearest(max(easyRun, (longRunMeters * TEMPO_RUN_FRACTION).roundToInt()), ROUND_TO)
        val thresholdRun = roundToNearest(max(easyRun, (longRunMeters * THRESHOLD_RUN_FRACTION).roundToInt()), ROUND_TO)
        val racePaceRun = roundToNearest(
            max(easyRun, minOf((targetDistanceMeters * RACE_PACE_RUN_FRACTION).roundToInt(), thresholdRun)),
            ROUND_TO
        )

        if (isRecoveryWeek) {
            return List(runsPerWeek - 1) { RunningSessionTarget(easyRun, RunningSessionType.EASY) } +
                RunningSessionTarget(longRunMeters, RunningSessionType.LONG_RUN)
        }

        val rotatingQualityType = when (weekIndex % 3) {
            1 -> RunningSessionType.TEMPO
            2 -> RunningSessionType.THRESHOLD
            else -> RunningSessionType.RACE_PACE
        }

        val sessionTypes = mutableListOf<RunningSessionType>()
        when (runsPerWeek) {
            2 -> {
                sessionTypes += rotatingQualityType
            }
            3 -> {
                sessionTypes += RunningSessionType.EASY
                sessionTypes += rotatingQualityType
            }
            else -> {
                sessionTypes += RunningSessionType.EASY
                sessionTypes += RunningSessionType.TEMPO
                if (runsPerWeek >= 4) {
                    sessionTypes += if (rotatingQualityType == RunningSessionType.TEMPO) {
                        RunningSessionType.THRESHOLD
                    } else {
                        rotatingQualityType
                    }
                }
                while (sessionTypes.size < runsPerWeek - 1) {
                    sessionTypes.add(sessionTypes.size.coerceAtLeast(1), RunningSessionType.EASY)
                }
            }
        }

        val sessions = sessionTypes.take(runsPerWeek - 1).map { type ->
            val distanceMeters = when (type) {
                RunningSessionType.EASY -> easyRun
                RunningSessionType.RECOVERY -> easyRun
                RunningSessionType.TEMPO -> tempoRun
                RunningSessionType.THRESHOLD -> thresholdRun
                RunningSessionType.INTERVALS -> thresholdRun
                RunningSessionType.PROGRESSION -> tempoRun
                RunningSessionType.RACE_PACE -> racePaceRun
                RunningSessionType.LONG_RUN -> longRunMeters
            }
            RunningSessionTarget(distanceMeters, type)
        }.toMutableList()

        sessions += RunningSessionTarget(longRunMeters, RunningSessionType.LONG_RUN)
        return sessions
    }

    private data class RunningSessionTarget(
        val distanceMeters: Int,
        val type: RunningSessionType
    )

    private fun roundToNearest(value: Int, nearest: Int): Int {
        return ((value + nearest / 2) / nearest) * nearest
    }
}
