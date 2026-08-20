package com.tripath.domain

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.ProjectionMode
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProjectionSourceTest {

    /** A Thursday, so weekday arithmetic in the tests is not accidentally aligned to week starts. */
    private val today = LocalDate.of(2026, 8, 20)

    private fun workout(
        date: LocalDate,
        type: WorkoutType,
        tss: Int,
        ignored: Boolean = false
    ) = WorkoutLog(
        connectId = "$type-$date",
        date = date,
        type = type,
        durationMinutes = 60,
        computedTSS = tss,
        isIgnored = ignored
    )

    private fun plan(date: LocalDate, type: WorkoutType, tss: Int) = TrainingPlan(
        date = date,
        type = type,
        durationMinutes = 60,
        plannedTSS = tss
    )

    /** [weeks] occurrences of [type] on [weekday], counting back from today. */
    private fun weekly(
        weekday: DayOfWeek,
        type: WorkoutType,
        tss: Int,
        weeks: Int
    ): List<WorkoutLog> = (1..weeks).map { i ->
        var d = today.minusWeeks(i.toLong())
        while (d.dayOfWeek != weekday) d = d.minusDays(1)
        workout(d, type, tss)
    }

    // ---- Planned mode --------------------------------------------------------------------------

    @Test
    fun `planned mode reads the planner and reports high confidence`() {
        val plans = listOf(
            plan(today.plusDays(1), WorkoutType.RUN, 70),
            plan(today.plusDays(1), WorkoutType.STRENGTH, 40),
            plan(today.plusDays(3), WorkoutType.BIKE, 120)
        )
        val projection = ProjectionSource.project(
            ProjectionMode.PLANNED, emptyList(), plans, today.plusDays(1), today.plusDays(7), today
        )
        assertEquals(ProjectionMode.PLANNED, projection.mode)
        assertEquals(110, projection.forDate(today.plusDays(1))!!.tss)
        assertEquals(120, projection.forDate(today.plusDays(3))!!.tss)
        assertNull(projection.forDate(today.plusDays(2)))
        assertEquals(ProjectionConfidence.HIGH, projection.confidence)
    }

    /**
     * The reason the pattern mode is the default: an unfinished planner projects a fitness collapse
     * that is not going to happen, and it does so with total confidence.
     */
    @Test
    fun `planned mode with an empty planner honestly reports no basis`() {
        val projection = ProjectionSource.project(
            ProjectionMode.PLANNED, emptyList(), emptyList(), today, today.plusDays(7), today
        )
        assertTrue(projection.days.isEmpty())
        assertEquals(ProjectionConfidence.NONE, projection.confidence)
    }

    @Test
    fun `planned mode keeps disciplines separate`() {
        val plans = listOf(
            plan(today.plusDays(1), WorkoutType.RUN, 70),
            plan(today.plusDays(1), WorkoutType.SWIM, 30)
        )
        val projection = ProjectionSource.project(
            ProjectionMode.PLANNED, emptyList(), plans, today.plusDays(1), today.plusDays(2), today
        )
        val day = projection.forDate(today.plusDays(1))!!
        assertEquals(70, day.byDiscipline[WorkoutType.RUN])
        assertEquals(30, day.byDiscipline[WorkoutType.SWIM])
    }

    // ---- Recent pattern ------------------------------------------------------------------------

    @Test
    fun `a firm weekly habit projects onto the same weekday`() {
        val history = weekly(DayOfWeek.SUNDAY, WorkoutType.BIKE, 180, weeks = 8)
        val projection = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN, history, emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        val nextSunday = (1..14).map { today.plusDays(it.toLong()) }
            .first { it.dayOfWeek == DayOfWeek.SUNDAY }
        val day = projection.forDate(nextSunday)
        assertNotNull(day)
        assertEquals(ProjectionConfidence.HIGH, day!!.confidence)
        assertTrue("projected ${day.tss}", day.tss > 100)
    }

    /**
     * The exact case that motivated per-cell confidence: three Sundays with a ride is a real signal,
     * but presenting the next one as a scheduled session would be a lie.
     */
    @Test
    fun `a sporadic habit is shrunk toward the average and flagged as less certain`() {
        val firm = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN,
            weekly(DayOfWeek.SUNDAY, WorkoutType.BIKE, 180, weeks = 8),
            emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        val sporadic = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN,
            weekly(DayOfWeek.SUNDAY, WorkoutType.BIKE, 180, weeks = 3),
            emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        val nextSunday = (1..14).map { today.plusDays(it.toLong()) }
            .first { it.dayOfWeek == DayOfWeek.SUNDAY }

        val firmDay = firm.forDate(nextSunday)!!
        val sporadicDay = sporadic.forDate(nextSunday)!!

        assertTrue("sporadic ${sporadicDay.tss} vs firm ${firmDay.tss}", sporadicDay.tss < firmDay.tss)
        assertEquals(ProjectionConfidence.MEDIUM, sporadicDay.confidence)
        assertEquals(ProjectionConfidence.HIGH, firmDay.confidence)
    }

    @Test
    fun `a single occurrence is barely projected at all`() {
        val once = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN,
            weekly(DayOfWeek.SUNDAY, WorkoutType.BIKE, 180, weeks = 8) +
                weekly(DayOfWeek.TUESDAY, WorkoutType.SWIM, 90, weeks = 1),
            emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        val nextTuesday = (1..14).map { today.plusDays(it.toLong()) }
            .first { it.dayOfWeek == DayOfWeek.TUESDAY }
        val day = once.forDate(nextTuesday)
        if (day != null) {
            assertTrue("projected ${day.tss} from one swim", day.tss < 45)
        }
    }

    @Test
    fun `too little history gives no projection rather than a confident guess`() {
        val history = listOf(workout(today.minusDays(2), WorkoutType.RUN, 80))
        val projection = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN, history, emptyList(), today.plusDays(1), today.plusDays(7), today
        )
        assertTrue(projection.days.isEmpty())
        assertEquals(ProjectionConfidence.NONE, projection.confidence)
    }

    @Test
    fun `no history at all is handled without throwing`() {
        val projection = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN, emptyList(), emptyList(), today, today.plusDays(7), today
        )
        assertTrue(projection.days.isEmpty())
        assertEquals(ProjectionConfidence.NONE, projection.confidence)
    }

    @Test
    fun `ignored workouts do not shape the projection`() {
        val real = weekly(DayOfWeek.SUNDAY, WorkoutType.BIKE, 180, weeks = 8)
        val withIgnored = real + weekly(DayOfWeek.MONDAY, WorkoutType.RUN, 400, weeks = 8)
            .map { it.copy(isIgnored = true) }

        val a = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN, real, emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        val b = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN, withIgnored, emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        assertEquals(a.tssByDate(), b.tssByDate())
    }

    @Test
    fun `training before the window does not leak into the projection`() {
        val old = weekly(DayOfWeek.SUNDAY, WorkoutType.BIKE, 180, weeks = 8)
            .map { it.copy(date = it.date.minusWeeks(20)) }
        val projection = ProjectionSource.project(
            ProjectionMode.RECENT_PATTERN, old, emptyList(), today.plusDays(1), today.plusDays(14), today
        )
        assertTrue(projection.days.isEmpty())
    }

    // ---- Shape -----------------------------------------------------------------------------------

    @Test
    fun `tssByDate produces exactly what the performance series consumes`() {
        val plans = listOf(plan(today.plusDays(2), WorkoutType.RUN, 85))
        val projection = ProjectionSource.project(
            ProjectionMode.PLANNED, emptyList(), plans, today.plusDays(1), today.plusDays(7), today
        )
        assertEquals(mapOf(today.plusDays(2) to 85), projection.tssByDate())
    }

    @Test
    fun `confidence degrades to the weaker of two`() {
        assertEquals(
            ProjectionConfidence.LOW,
            ProjectionConfidence.HIGH.coerceDownTo(ProjectionConfidence.LOW)
        )
        assertEquals(
            ProjectionConfidence.LOW,
            ProjectionConfidence.LOW.coerceDownTo(ProjectionConfidence.HIGH)
        )
    }
}
