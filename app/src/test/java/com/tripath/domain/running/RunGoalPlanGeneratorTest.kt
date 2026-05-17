package com.tripath.domain.running

import com.tripath.data.local.preferences.RunningGoalPreferencesCodec
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RunGoalPlanGeneratorTest {
    @Test
    fun `running-goal mode returns run training plans`() {
        val plans = RunGoalPlanGenerator.generatePlans(
            runningGoal = RunningGoal(
                type = RunningGoalType.COMPLETE_DISTANCE,
                targetDistanceMeters = 14000,
                targetDate = LocalDate.of(2026, 8, 10),
                runsPerWeek = 3,
                preferredDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
                baselineLongestRunMeters = 6000
            ),
            startDate = LocalDate.of(2026, 5, 18),
            months = 3
        )

        assertNotNull(plans)
        assertTrue(plans!!.isNotEmpty())
        assertTrue(plans.all { it.type == WorkoutType.RUN })
        assertTrue(plans.all { it.plannedDistanceMeters != null })
        assertTrue(plans.any { it.subType == "Long Run" })
        assertEquals(plans.size, plans.map { it.date }.distinct().size)
    }

    @Test
    fun `persisted running goal activates generation path when loaded`() {
        val savedGoal = RunningGoal(
            type = RunningGoalType.COMPLETE_DISTANCE,
            targetDistanceMeters = 10000,
            targetDate = LocalDate.of(2026, 7, 20),
            runsPerWeek = 3,
            preferredDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            baselineLongestRunMeters = 5000
        )

        val loadedGoal = RunningGoalPreferencesCodec.decode(RunningGoalPreferencesCodec.encode(savedGoal))
        val plans = RunGoalPlanGenerator.generatePlans(
            runningGoal = loadedGoal,
            startDate = LocalDate.of(2026, 5, 18),
            months = 3
        )

        assertNotNull(plans)
        assertTrue(plans!!.all { it.type == WorkoutType.RUN })
    }

    @Test
    fun `no-running-goal path remains inactive`() {
        val plans = RunGoalPlanGenerator.generatePlans(
            runningGoal = null,
            startDate = LocalDate.of(2026, 5, 18),
            months = 3
        )

        assertEquals(null, plans)
    }
}