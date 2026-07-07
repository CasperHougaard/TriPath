package com.tripath.ui.daydetail

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StrengthCascadeTest {
    private val d = LocalDate.of(2026, 5, 18) // Monday anchor

    private fun strength(date: LocalDate, id: String) =
        TrainingPlan(id = id, date = date, type = WorkoutType.STRENGTH, durationMinutes = 70, plannedTSS = 52)

    private fun run(date: LocalDate, id: String) =
        TrainingPlan(id = id, date = date, type = WorkoutType.RUN, durationMinutes = 40, plannedTSS = 40)

    @Test
    fun `moving strength shifts every later strength by the same delta`() {
        val moved = strength(d, "s0")
        val plans = listOf(
            moved,
            strength(d.plusDays(3), "s1"),
            strength(d.plusDays(6), "s2")
        )

        val updates = cascadeStrengthMoveUpdates(
            activity = moved,
            newDate = d.plusDays(1),
            allPlans = plans,
            considerRuns = false
        )

        val byId = updates.associateBy { it.id }
        assertEquals(d.plusDays(1), byId.getValue("s0").date)
        assertEquals(d.plusDays(4), byId.getValue("s1").date)
        assertEquals(d.plusDays(7), byId.getValue("s2").date)
    }

    @Test
    fun `runs shift with strength only when consider-runs is on`() {
        val moved = strength(d, "s0")
        val laterRun = run(d.plusDays(2), "r1")
        val plans = listOf(moved, laterRun, strength(d.plusDays(3), "s1"))

        val withRuns = cascadeStrengthMoveUpdates(moved, d.plusDays(1), plans, considerRuns = true)
        assertEquals(d.plusDays(3), withRuns.single { it.id == "r1" }.date)

        val withoutRuns = cascadeStrengthMoveUpdates(moved, d.plusDays(1), plans, considerRuns = false)
        assertNull(withoutRuns.firstOrNull { it.id == "r1" }) // run left untouched
    }

    @Test
    fun `plans before the moved session are never shifted`() {
        val moved = strength(d, "s0")
        val earlierRun = run(d.minusDays(1), "r0")
        val plans = listOf(earlierRun, moved, strength(d.plusDays(3), "s1"))

        val updates = cascadeStrengthMoveUpdates(moved, d.plusDays(1), plans, considerRuns = true)

        assertNull(updates.firstOrNull { it.id == "r0" }) // earlier plan untouched
    }

    @Test
    fun `no-op move returns no updates`() {
        val moved = strength(d, "s0")
        val updates = cascadeStrengthMoveUpdates(moved, d, listOf(moved), considerRuns = true)
        assertTrue(updates.isEmpty())
    }
}
