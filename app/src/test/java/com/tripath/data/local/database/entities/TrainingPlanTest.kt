package com.tripath.data.local.database.entities

import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrainingPlanTest {
    @Test
    fun `planned distance defaults to null when omitted`() {
        val plan = TrainingPlan(
            date = LocalDate.of(2026, 5, 17),
            type = WorkoutType.BIKE,
            durationMinutes = 60,
            plannedTSS = 55
        )

        assertNull(plan.plannedDistanceMeters)
    }

    @Test
    fun `planned distance can be stored for a run`() {
        val plan = TrainingPlan(
            date = LocalDate.of(2026, 5, 17),
            type = WorkoutType.RUN,
            durationMinutes = 50,
            plannedTSS = 48,
            plannedDistanceMeters = 10000
        )

        assertEquals(10000, plan.plannedDistanceMeters)
    }

    @Test
    fun `planned distance can remain null for non-run workouts`() {
        val plan = TrainingPlan(
            date = LocalDate.of(2026, 5, 17),
            type = WorkoutType.STRENGTH,
            durationMinutes = 45,
            plannedTSS = 30,
            plannedDistanceMeters = null
        )

        assertNull(plan.plannedDistanceMeters)
    }
}