package com.tripath.domain.coach

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import com.tripath.domain.strain.StrainChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannedStrainAdvisorTest {

    private val start = LocalDate.of(2026, 9, 1)

    private fun plan(
        dayOffset: Long,
        type: WorkoutType,
        tss: Int = 90,
        distanceM: Int? = null
    ) = TrainingPlan(
        date = start.plusDays(dayOffset),
        type = type,
        durationMinutes = 60,
        plannedTSS = tss,
        plannedDistanceMeters = distanceM
    )

    /** Three hard run days back to back is the shape this is meant to notice. */
    @Test
    fun `stacked hard runs are flagged`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 130, distanceM = 18_000),
            plan(1, WorkoutType.RUN, tss = 120, distanceM = 16_000),
            plan(2, WorkoutType.RUN, tss = 120, distanceM = 16_000)
        )
        val conflicts = PlannedStrainAdvisor.findConflicts(plans)
        assertTrue("found ${conflicts.size}", conflicts.isNotEmpty())
        assertTrue(conflicts.any { it.channel == StrainChannel.LOWER_IMPACT })
    }

    /**
     * The distinction the channel model exists for: a swim the day after a long run asks nothing of
     * the tissue that run loaded, so it is not a conflict.
     */
    @Test
    fun `a swim after a long run is not a conflict`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 150, distanceM = 22_000),
            plan(1, WorkoutType.SWIM, tss = 90)
        )
        assertTrue(PlannedStrainAdvisor.findConflicts(plans).isEmpty())
    }

    @Test
    fun `a ride the day after a long run is judged on leg muscle, not impact`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 150, distanceM = 22_000),
            plan(1, WorkoutType.BIKE, tss = 90)
        )
        val conflicts = PlannedStrainAdvisor.findConflicts(plans)
        assertTrue(conflicts.none { it.channel == StrainChannel.LOWER_IMPACT })
    }

    @Test
    fun `well-spaced training produces no conflicts`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 90, distanceM = 10_000),
            plan(3, WorkoutType.BIKE, tss = 90),
            plan(6, WorkoutType.SWIM, tss = 70)
        )
        assertTrue(PlannedStrainAdvisor.findConflicts(plans).isEmpty())
    }

    @Test
    fun `an easy session is not flagged however tired the plan leaves that channel`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 160, distanceM = 24_000),
            plan(1, WorkoutType.RUN, tss = 20, distanceM = 3_000)
        )
        assertTrue(PlannedStrainAdvisor.findConflicts(plans).none { it.date == start.plusDays(1) })
    }

    /** A session is measured against what came *before* it, never against its own load. */
    @Test
    fun `the first session of a plan is never in conflict with itself`() {
        val conflicts = PlannedStrainAdvisor.findConflicts(
            listOf(plan(0, WorkoutType.RUN, tss = 200, distanceM = 30_000))
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `an empty plan is handled without throwing`() {
        assertTrue(PlannedStrainAdvisor.findConflicts(emptyList()).isEmpty())
    }

    /** Reporting, not reshuffling — the advisor must never quietly change the schedule. */
    @Test
    fun `conflicts surface as non-blocking warnings`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 140, distanceM = 20_000),
            plan(1, WorkoutType.RUN, tss = 130, distanceM = 18_000)
        )
        val warnings = PlannedStrainAdvisor.asWarnings(PlannedStrainAdvisor.findConflicts(plans))
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.none { it.isBlocker })
        assertEquals(WarningType.RECOVERY_ADVICE, warnings.first().type)
    }

    @Test
    fun `a conflict names the day, the tissue and what to do about it`() {
        val plans = listOf(
            plan(0, WorkoutType.RUN, tss = 140, distanceM = 20_000),
            plan(1, WorkoutType.RUN, tss = 130, distanceM = 18_000)
        )
        val conflict = PlannedStrainAdvisor.findConflicts(plans).first()
        assertTrue(conflict.message.contains("fresh"))
        assertTrue(conflict.message.contains("moving it a day") || conflict.message.contains("easing"))
    }
}
