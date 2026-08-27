package com.tripath.ui.coach

import com.tripath.domain.running.RunningGoal
import com.tripath.domain.running.RunningGoalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RunningGoalEditorStateTest {
    @Test
    fun `loading persisted goal populates editor state`() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 14000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            preferredDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            baselineLongestRunMeters = 6000,
            baselineWeeklyRunMeters = 18000
        )

        val state = RunningGoalEditorState.fromGoal(goal)

        assertEquals(RunningGoalType.COMPLETE_DISTANCE, state.goalType)
        assertEquals("14", state.targetDistanceKm)
        assertEquals(LocalDate.of(2026, 8, 10), state.targetDate)
        assertEquals("3", state.runsPerWeek)
        assertTrue(DayOfWeek.TUESDAY in state.preferredDays)
        assertEquals("6", state.baselineLongestRunKm)
        assertEquals("18", state.baselineWeeklyRunKm)
    }

    @Test
    fun `saving edited state produces running goal`() {
        val state = RunningGoalEditorState(
            goalType = RunningGoalType.CONSISTENCY,
            runsPerWeek = "4",
            preferredDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            baselineLongestRunKm = "8",
            baselineWeeklyRunKm = "24"
        )

        val goal = state.toRunningGoalOrNull()

        assertEquals(RunningGoalType.CONSISTENCY, goal?.type)
        assertEquals(4, goal?.runsPerWeek)
        assertEquals(8000, goal?.baselineLongestRunMeters)
        assertEquals(24000, goal?.baselineWeeklyRunMeters)
    }

    @Test
    fun `clearing editor state yields no goal`() {
        val state = RunningGoalEditorState.fromGoal(null)

        assertNull(state.toRunningGoalOrNull()?.targetDistanceMeters)
        // A blank editor opens on ENDURANCE — the default moved there in 0.9 and this assertion
        // was left behind on the old COMPLETE_DISTANCE.
        assertEquals(RunningGoalType.ENDURANCE, state.goalType)
        assertEquals("", state.targetDistanceKm)
    }
}