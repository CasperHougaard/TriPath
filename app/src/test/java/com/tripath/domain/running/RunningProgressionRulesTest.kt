package com.tripath.domain.running

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RunningProgressionRulesTest {
    @Test
    fun completeDistanceGoal_14km_12weeks_3runs() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 14000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            baselineLongestRunMeters = 6000
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start)
        assertEquals(12, result.weeklyTargets.size)
        assertEquals(3, result.weeklyTargets[0].runsPerWeek)
        assertEquals(14000, result.weeklyTargets.last().sessionDistancesMeters.maxOrNull())
        assertTrue(result.weeklyTargets.all { it.progressionMode == RunningProgressionMode.DISTANCE_BUILDING })
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun completeDistanceGoal_runnerNearTarget_usesPerformanceFocusedMode() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 10000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            baselineLongestRunMeters = 9000,
            baselineWeeklyRunMeters = 26000
        )

        val result = RunningProgressionRules.generateWeeklyTargets(goal, LocalDate.of(2026, 5, 18))

        assertTrue(result.weeklyTargets.all { it.progressionMode == RunningProgressionMode.PERFORMANCE_FOCUSED })
        assertTrue(result.weeklyTargets.any { week ->
            week.sessionTypes.any { it == RunningSessionType.TEMPO || it == RunningSessionType.THRESHOLD || it == RunningSessionType.RACE_PACE }
        })
        assertTrue(result.weeklyTargets.first().sessionDistancesMeters.maxOrNull() ?: 0 >= 8500)
    }

    @Test
    fun completeDistanceGoal_runnerAboveTarget_usesPerformanceFocusedMode() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 10000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            baselineLongestRunMeters = 12000,
            baselineWeeklyRunMeters = 30000
        )

        val result = RunningProgressionRules.generateWeeklyTargets(goal, LocalDate.of(2026, 5, 18))

        assertTrue(result.weeklyTargets.all { it.progressionMode == RunningProgressionMode.PERFORMANCE_FOCUSED })
        assertTrue(result.warnings.contains(RunningProgressionWarning.TARGET_ALREADY_REACHED))
        assertTrue(result.weeklyTargets.first().sessionDistancesMeters.maxOrNull() ?: 0 >= 10000)
    }

    @Test
    fun performanceFocusedMode_generatesQualitySessions() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 10000,
            targetDate = LocalDate.of(2026, 7, 20),
            runsPerWeek = 4,
            baselineLongestRunMeters = 9500,
            baselineWeeklyRunMeters = 32000
        )

        val result = RunningProgressionRules.generateWeeklyTargets(goal, LocalDate.of(2026, 5, 18))
        val firstBuildWeek = result.weeklyTargets.first { !it.isRecoveryWeek }

        assertTrue(firstBuildWeek.sessionTypes.contains(RunningSessionType.TEMPO))
        assertTrue(firstBuildWeek.sessionTypes.any { it == RunningSessionType.THRESHOLD || it == RunningSessionType.RACE_PACE })
        assertTrue(firstBuildWeek.sessionTypes.contains(RunningSessionType.LONG_RUN))
    }

    @Test
    fun distanceBuildingMode_generatesMostlyEasyPlusLongRun() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 14000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            baselineLongestRunMeters = 4000,
            baselineWeeklyRunMeters = 12000
        )

        val result = RunningProgressionRules.generateWeeklyTargets(goal, LocalDate.of(2026, 5, 18))
        val firstWeek = result.weeklyTargets.first()

        assertEquals(RunningProgressionMode.DISTANCE_BUILDING, firstWeek.progressionMode)
        assertEquals(
            listOf(RunningSessionType.EASY, RunningSessionType.EASY, RunningSessionType.LONG_RUN),
            firstWeek.sessionTypes
        )
    }

    @Test
    fun completeDistanceGoal_includesRecoveryWeeks() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 14000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            baselineLongestRunMeters = 6000
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start)
        val recoveryWeeks = result.weeklyTargets.filter { it.isRecoveryWeek }
        assertTrue(recoveryWeeks.isNotEmpty())
        for (week in recoveryWeeks) {
            val idx = week.weekIndex - 1
            if (idx > 0) {
                val prevLong = result.weeklyTargets[idx - 1].sessionDistancesMeters.maxOrNull() ?: 0
                val thisLong = week.sessionDistancesMeters.maxOrNull() ?: 0
                assertTrue(thisLong < prevLong)
            }
        }
    }

    @Test
    fun completeDistanceGoal_missingBaseline() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 10000,
            targetDate = LocalDate.of(2026, 7, 1),
            runsPerWeek = 2
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start)
        assertTrue(result.warnings.contains(RunningProgressionWarning.MISSING_BASELINE_DISTANCE))
        assertEquals(2, result.weeklyTargets[0].runsPerWeek)
    }

    @Test
    fun completeDistanceGoal_longHorizon_warnsButStillBuilds() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 21097,
            targetDate = LocalDate.of(2026, 12, 14),
            runsPerWeek = 4,
            baselineLongestRunMeters = 10000,
            baselineWeeklyRunMeters = 28000
        )

        val result = RunningProgressionRules.generateWeeklyTargets(goal, LocalDate.of(2026, 5, 18))

        assertTrue(result.warnings.contains(RunningProgressionWarning.LONG_GOAL_HORIZON))
        assertTrue(result.weeklyTargets.isNotEmpty())
    }

    @Test
    fun completeDistanceGoal_tooFar_isRejected() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 42195,
            targetDate = LocalDate.of(2027, 6, 1),
            runsPerWeek = 4,
            baselineLongestRunMeters = 12000
        )

        val result = RunningProgressionRules.generateWeeklyTargets(goal, LocalDate.of(2026, 5, 18))

        assertTrue(result.weeklyTargets.isEmpty())
        assertEquals(listOf(RunningProgressionWarning.TARGET_DATE_TOO_FAR), result.warnings)
    }

    @Test
    fun completeDistanceGoal_aggressiveProgression() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 20000,
            targetDate = LocalDate.of(2026, 6, 15), // short timeframe
            runsPerWeek = 2,
            baselineLongestRunMeters = 2000
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start)
        assertTrue(result.warnings.contains(RunningProgressionWarning.AGGRESSIVE_PROGRESSION_REQUIRED) ||
                   result.warnings.contains(RunningProgressionWarning.TARGET_DATE_TOO_SOON))
    }

    @Test
    fun completeDistanceGoal_alreadyReached() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 8000,
            targetDate = LocalDate.of(2026, 7, 1),
            runsPerWeek = 2,
            baselineLongestRunMeters = 9000
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start)
        assertTrue(result.warnings.contains(RunningProgressionWarning.TARGET_ALREADY_REACHED))
        assertEquals(2, result.weeklyTargets[0].runsPerWeek)
        assertEquals(RunningProgressionMode.PERFORMANCE_FOCUSED, result.weeklyTargets[0].progressionMode)
    }

    @Test
    fun runConsistently_3runs_noDistance() {
        val goal = RunningGoal(
            type = RunningGoalType.CONSISTENCY,
            runsPerWeek = 3
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start, openEndedWeeks = 6)
        assertEquals(6, result.weeklyTargets.size)
        assertEquals(3, result.weeklyTargets[0].runsPerWeek)
        assertTrue(result.weeklyTargets.all { it.sessionDistancesMeters.isEmpty() })
    }

    @Test
    fun runConsistently_withBaseline() {
        val goal = RunningGoal(
            type = RunningGoalType.CONSISTENCY,
            runsPerWeek = 3,
            baselineLongestRunMeters = 6000
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start, openEndedWeeks = 4)
        assertEquals(4, result.weeklyTargets.size)
        assertTrue(result.weeklyTargets.all { it.sessionDistancesMeters.size == 3 })
        assertTrue(result.weeklyTargets.all { it.sessionDistancesMeters.all { d -> d > 0 } })
    }

    @Test
    fun buildEndurance_withBaseline() {
        val goal = RunningGoal(
            type = RunningGoalType.ENDURANCE,
            runsPerWeek = 2,
            baselineLongestRunMeters = 5000
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start, openEndedWeeks = 5)
        assertEquals(5, result.weeklyTargets.size)
        assertTrue(result.weeklyTargets.any { it.isRecoveryWeek })
        assertTrue(result.weeklyTargets.all { it.sessionDistancesMeters.isNotEmpty() })
    }

    @Test
    fun buildEndurance_noBaseline() {
        val goal = RunningGoal(
            type = RunningGoalType.ENDURANCE,
            runsPerWeek = 2
        )
        val start = LocalDate.of(2026, 5, 18)
        val result = RunningProgressionRules.generateWeeklyTargets(goal, start, openEndedWeeks = 3)
        assertTrue(result.warnings.contains(RunningProgressionWarning.MISSING_BASELINE_DISTANCE))
        assertTrue(result.weeklyTargets.all { it.sessionDistancesMeters.isEmpty() })
    }
}
