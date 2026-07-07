package com.tripath.ui.daydetail

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MissedRunAssistantTest {
    private val today = LocalDate.of(2026, 5, 17)

    @Test
    fun `past planned run without same day completed run is eligible`() {
        val activity = plannedRun(date = today.minusDays(1))

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = emptyList(),
            today = today
        )

        assertTrue(state.isEligible)
        assertEquals(defaultMissedRunActions(), state.actions)
    }

    @Test
    fun `today run is not treated as missed`() {
        val activity = plannedRun(date = today)

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = emptyList(),
            today = today
        )

        assertFalse(state.isEligible)
        assertTrue(state.actions.isEmpty())
    }

    @Test
    fun `non run activity is not eligible`() {
        val activity = TrainingPlan(
            date = today.minusDays(1),
            type = WorkoutType.BIKE,
            durationMinutes = 60,
            plannedTSS = 55
        )

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = emptyList(),
            today = today
        )

        assertFalse(state.isEligible)
    }

    @Test
    fun `same day completed run suppresses missed assistant`() {
        val activity = plannedRun(date = today.minusDays(2))
        val completedWorkouts = listOf(
            WorkoutLog(
                connectId = "run-1",
                date = activity.date,
                type = WorkoutType.RUN,
                durationMinutes = 42,
                computedTSS = 40
            )
        )

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = completedWorkouts,
            today = today
        )

        assertFalse(state.isEligible)
        assertTrue(state.actions.isEmpty())
    }

    @Test
    fun `non run completion does not suppress missed assistant`() {
        val activity = plannedRun(date = today.minusDays(3))
        val completedWorkouts = listOf(
            WorkoutLog(
                connectId = "bike-1",
                date = activity.date,
                type = WorkoutType.BIKE,
                durationMinutes = 60,
                computedTSS = 55
            )
        )

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = completedWorkouts,
            today = today
        )

        assertTrue(state.isEligible)
        assertEquals(defaultMissedRunActions(), state.actions)
    }

    @Test
    fun `past planned strength without same day logged strength is eligible`() {
        val activity = TrainingPlan(
            date = today.minusDays(1),
            type = WorkoutType.STRENGTH,
            durationMinutes = 70,
            plannedTSS = 52
        )

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = emptyList(),
            today = today
        )

        assertTrue(state.isEligible)
    }

    @Test
    fun `logged strength of any duration suppresses missed strength assistant`() {
        val activity = TrainingPlan(
            date = today.minusDays(1),
            type = WorkoutType.STRENGTH,
            durationMinutes = 70,
            plannedTSS = 52
        )
        val completedWorkouts = listOf(
            WorkoutLog(
                connectId = "strength-1",
                date = activity.date,
                type = WorkoutType.STRENGTH,
                durationMinutes = 25, // shorter than planned — still counts as done
                computedTSS = 20
            )
        )

        val state = buildMissedRunAssistantState(
            activity = activity,
            completedWorkouts = completedWorkouts,
            today = today
        )

        assertFalse(state.isEligible)
    }

    @Test
    fun `tomorrow action uses next calendar day from current date`() {
        assertEquals(today.plusDays(1), missedRunTomorrowDate(today))
    }

    private fun plannedRun(date: LocalDate): TrainingPlan {
        return TrainingPlan(
            date = date,
            type = WorkoutType.RUN,
            durationMinutes = 45,
            plannedTSS = 40,
            plannedDistanceMeters = 8000
        )
    }
}