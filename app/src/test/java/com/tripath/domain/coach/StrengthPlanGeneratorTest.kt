package com.tripath.domain.coach

import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StrengthPlanGeneratorTest {
    private val monday = LocalDate.of(2026, 5, 18) // Monday

    @Test
    fun `sessions land every third day starting on the first workout date`() {
        val plans = StrengthPlanGenerator.generateStrengthPlans(
            firstWorkoutDate = monday,
            planStartDate = monday,
            weeks = 1
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 21),
                LocalDate.of(2026, 5, 24)
            ),
            plans.map { it.date }
        )
        plans.zipWithNext().forEach { (a, b) ->
            assertEquals(3L, ChronoUnit.DAYS.between(a.date, b.date))
        }
    }

    @Test
    fun `each session is a 70 min moderate full-body strength plan`() {
        val plans = StrengthPlanGenerator.generateStrengthPlans(
            firstWorkoutDate = monday,
            planStartDate = monday,
            weeks = 2
        )

        assertTrue(plans.isNotEmpty())
        plans.forEach { plan ->
            assertEquals(WorkoutType.STRENGTH, plan.type)
            assertEquals(70, plan.durationMinutes)
            assertEquals(Intensity.MODERATE, plan.intensity)
            assertEquals(StrengthFocus.FULL_BODY, plan.strengthFocus)
            assertEquals(StrengthPlanGenerator.SESSION_PLANNED_TSS, plan.plannedTSS)
            assertEquals(null, plan.plannedDistanceMeters)
        }
    }

    @Test
    fun `first workout date before plan start rolls forward onto the cadence`() {
        val plans = StrengthPlanGenerator.generateStrengthPlans(
            firstWorkoutDate = LocalDate.of(2026, 5, 16), // Saturday, 2 days before plan start
            planStartDate = monday,
            weeks = 1
        )

        // 5/16 + 3 = 5/19 is the first cadence date on/after the Monday plan start.
        assertEquals(
            listOf(LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 22)),
            plans.map { it.date }
        )
        assertTrue(plans.all { !it.date.isBefore(monday) })
    }

    @Test
    fun `first workout date before plan start is honored when allowed by earliest session date`() {
        val wednesday = LocalDate.of(2026, 5, 13) // 5 days before the Monday plan start
        val plans = StrengthPlanGenerator.generateStrengthPlans(
            firstWorkoutDate = wednesday,
            planStartDate = monday,
            weeks = 1,
            earliestSessionDate = wednesday
        )

        // Sessions begin on the chosen day and are not pushed to the running plan's week start.
        assertEquals(LocalDate.of(2026, 5, 13), plans.first().date)
        assertTrue(plans.any { it.date.isBefore(monday) })
        plans.zipWithNext().forEach { (a, b) ->
            assertEquals(3L, ChronoUnit.DAYS.between(a.date, b.date))
        }
    }

    @Test
    fun `no session falls on or after the plan horizon`() {
        val weeks = 3
        val plans = StrengthPlanGenerator.generateStrengthPlans(
            firstWorkoutDate = monday,
            planStartDate = monday,
            weeks = weeks
        )

        val horizonEnd = monday.plusWeeks(weeks.toLong())
        assertTrue(plans.all { it.date.isBefore(horizonEnd) })
        assertFalse(plans.isEmpty())
    }

    @Test
    fun `zero weeks produces no sessions`() {
        val plans = StrengthPlanGenerator.generateStrengthPlans(
            firstWorkoutDate = monday,
            planStartDate = monday,
            weeks = 0
        )
        assertTrue(plans.isEmpty())
    }
}
