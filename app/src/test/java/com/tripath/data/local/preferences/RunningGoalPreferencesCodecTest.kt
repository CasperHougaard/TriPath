package com.tripath.data.local.preferences

import com.tripath.domain.running.RunningGoal
import com.tripath.domain.running.RunningGoalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RunningGoalPreferencesCodecTest {
    @Test
    fun `save and load running goal round-trips correctly`() {
        val goal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 14000,
            targetDate = LocalDate.of(2026, 8, 10),
            runsPerWeek = 3,
            preferredDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            baselineLongestRunMeters = 6000,
            baselineWeeklyRunMeters = 18000
        )

        val encoded = RunningGoalPreferencesCodec.encode(goal)
        val decoded = RunningGoalPreferencesCodec.decode(encoded)

        assertEquals(goal, decoded)
    }

    @Test
    fun `load returns null when no active goal exists`() {
        assertNull(RunningGoalPreferencesCodec.decode(null))
        assertNull(RunningGoalPreferencesCodec.decode(""))
    }

    @Test
    fun `clear semantics produce no active goal`() {
        val goal = RunningGoal(type = RunningGoalType.CONSISTENCY, runsPerWeek = 3)
        val encoded = RunningGoalPreferencesCodec.encode(goal)

        assertEquals(goal, RunningGoalPreferencesCodec.decode(encoded))
        assertNull(RunningGoalPreferencesCodec.decode(null))
    }
}