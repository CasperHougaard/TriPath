package com.tripath.domain.running

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RunningGoalTest {
    @Test
    fun `valid complete-distance goal`() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 14000,
            targetDate = LocalDate.of(2026, 8, 17),
            baselineLongestRunMeters = 8000,
            baselineWeeklyRunMeters = 20000
        )
        assertEquals(RunningGoalType.COMPLETE_DISTANCE, goal.type)
        assertEquals(14000, goal.targetDistanceMeters)
        assertEquals(LocalDate.of(2026, 8, 17), goal.targetDate)
        assertEquals(8000, goal.baselineLongestRunMeters)
        assertEquals(20000, goal.baselineWeeklyRunMeters)
    }

    @Test
    fun `valid consistency goal`() {
        val goal = RunningGoal(
            type = RunningGoalType.CONSISTENCY,
            runsPerWeek = 3,
            preferredDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            baselineWeeklyRunMeters = 15000
        )
        assertEquals(RunningGoalType.CONSISTENCY, goal.type)
        assertEquals(3, goal.runsPerWeek)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), goal.preferredDays)
        assertEquals(15000, goal.baselineWeeklyRunMeters)
    }

    @Test
    fun `invalid negative distance`() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = -5000,
            targetDate = LocalDate.of(2026, 8, 17)
        )
        assertTrue(goal.targetDistanceMeters != null && goal.targetDistanceMeters!! < 0)
    }

    @Test
    fun `invalid runs per week`() {
        val goalZero = RunningGoal(type = RunningGoalType.CONSISTENCY, runsPerWeek = 0)
        val goalTooMany = RunningGoal(type = RunningGoalType.CONSISTENCY, runsPerWeek = 8)
        assertEquals(0, goalZero.runsPerWeek)
        assertEquals(8, goalTooMany.runsPerWeek)
    }

    @Test
    fun `missing target date for distance goal`() {
        val goal = RunningGoal(type = RunningGoalType.COMPLETE_DISTANCE, targetDistanceMeters = 10000)
        assertNull(goal.targetDate)
    }
}
