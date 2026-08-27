package com.tripath.domain.strain

import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSessionLog
import com.tripath.data.local.database.entities.LiftSetLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The deduplication rule and the source filter, which are the same mechanism seen from two angles:
 * [StrainSource] decides which inputs exist, and the dedup falls out of that rather than being
 * applied twice.
 */
class StrainAnalyticsTest {

    private val today = LocalDate.of(2026, 8, 20)
    private val liftDay = today.minusDays(1)

    private fun workout(
        type: WorkoutType,
        date: LocalDate,
        tss: Int = 80,
        distanceM: Double? = null,
        ignored: Boolean = false
    ) = WorkoutLog(
        connectId = "$type-$date",
        date = date,
        type = type,
        durationMinutes = 60,
        computedTSS = tss,
        distanceMeters = distanceM,
        isIgnored = ignored
    )

    private val catalog = listOf(
        LiftExerciseCatalogEntry(
            id = 1,
            name = "Back squat",
            region = "LOWER",
            tier = "TIER_1",
            pattern = "SQUAT",
            mechanics = "COMPOUND",
            primaryTargets = "QUADS",
            secondaryTargets = "GLUTES"
        )
    )

    private val liftSession = LiftSessionLog(id = "s1", date = liftDay, totalSets = 5)

    private val liftSets = (1..5).map { number ->
        LiftSetLog(
            sessionId = "s1",
            exerciseId = 1,
            setNumber = number,
            kg = 120f,
            reps = 5,
            rpe = 8f,
            isWarmup = false
        )
    }

    private fun build(
        source: StrainSource,
        workouts: List<WorkoutLog>,
        sessions: List<LiftSessionLog> = listOf(liftSession),
        sets: List<LiftSetLog> = liftSets
    ) = StrainAnalytics.build(
        workouts = workouts,
        liftSessions = sessions,
        liftSets = sets,
        catalog = catalog,
        from = today.minusDays(30),
        to = today,
        source = source
    )

    private fun StrainHistory.on(date: LocalDate): StrainVector =
        days.firstOrNull { it.date == date }?.strain ?: StrainVector.ZERO

    private fun StrainHistory.musclesOn(date: LocalDate): Map<String, Double> =
        muscleByDate.firstOrNull { it.first == date }?.second ?: emptyMap()

    // ---- The dedup rule ------------------------------------------------------------------------

    /** The rule the whole join exists for: one lifting session, scored once. */
    @Test
    fun `in both mode a strength record with LiftPath detail is scored from the sets`() {
        val hc = workout(WorkoutType.STRENGTH, liftDay, tss = 60)
        val both = build(StrainSource.BOTH, listOf(hc)).on(liftDay)
        val setsOnly = build(StrainSource.LIFT_PATH, listOf(hc)).on(liftDay)

        assertEquals(setsOnly, both)
    }

    /**
     * With LiftPath excluded there is nothing to be superseded *by*, so the Health Connect record
     * stops being dropped and scores at its own flat estimate. That is the only view of the session
     * this mode has — and it is still exactly one view of it.
     */
    @Test
    fun `in TriPath mode the strength record scores at its own estimate instead`() {
        val hc = workout(WorkoutType.STRENGTH, liftDay, tss = 60)
        val triPath = build(StrainSource.TRI_PATH, listOf(hc)).on(liftDay)

        assertEquals(StrainMapper.forWorkout(hc), triPath)
        assertFalse("the Health Connect record must not vanish", triPath.isEmpty)
    }

    /**
     * Stated explicitly because it looks like a bug and is not: Both discards the Health Connect
     * strength record, so it is strictly less than the two restricted views added together.
     */
    @Test
    fun `both is not the sum of the two sources on a lifting day`() {
        val hc = workout(WorkoutType.STRENGTH, liftDay, tss = 60)
        val both = build(StrainSource.BOTH, listOf(hc)).on(liftDay)
        val lift = build(StrainSource.LIFT_PATH, listOf(hc)).on(liftDay)
        val tri = build(StrainSource.TRI_PATH, listOf(hc)).on(liftDay)

        assertEquals("both must equal the set-level view alone", lift, both)
        assertTrue("the sum would count the day twice", (lift + tri).systemic > both.systemic)
    }

    /** A strength record on a day LiftPath never detailed is not superseded by another day's sets. */
    @Test
    fun `a strength record on an undetailed day is kept in both mode`() {
        val hc = workout(WorkoutType.STRENGTH, today, tss = 60)
        assertEquals(
            StrainMapper.forWorkout(hc),
            build(StrainSource.BOTH, listOf(hc)).on(today)
        )
    }

    // ---- The source filter ---------------------------------------------------------------------

    @Test
    fun `LiftPath mode excludes endurance sessions entirely`() {
        val history = build(
            StrainSource.LIFT_PATH,
            listOf(workout(WorkoutType.BIKE, today, tss = 90))
        )
        assertTrue(history.on(today).isEmpty)
        assertFalse("the lifting day must survive", history.on(liftDay).isEmpty)
    }

    @Test
    fun `TriPath mode excludes lifting sets entirely`() {
        val history = build(
            StrainSource.TRI_PATH,
            listOf(workout(WorkoutType.BIKE, today, tss = 90))
        )
        assertFalse(history.on(today).isEmpty)
        assertTrue("no set-level load in this mode", history.on(liftDay).isEmpty)
    }

    @Test
    fun `an ignored session contributes nothing in any mode`() {
        StrainSource.entries.forEach { source ->
            val history = build(
                source,
                listOf(workout(WorkoutType.BIKE, today, tss = 90, ignored = true))
            )
            assertTrue("$source counted an ignored session", history.on(today).isEmpty)
        }
    }

    // ---- The muscle map ------------------------------------------------------------------------

    /** The point of the whole change: a ride is no longer invisible on the body diagram. */
    @Test
    fun `a ride paints muscle groups in TriPath and both modes but not in LiftPath mode`() {
        val ride = listOf(workout(WorkoutType.BIKE, today, tss = 90))

        assertTrue(build(StrainSource.TRI_PATH, ride).musclesOn(today).isNotEmpty())
        assertTrue(build(StrainSource.BOTH, ride).musclesOn(today).isNotEmpty())
        assertTrue(build(StrainSource.LIFT_PATH, ride).musclesOn(today).isEmpty())
    }

    @Test
    fun `a day with both a ride and lifting merges the two into one group map`() {
        val ride = listOf(workout(WorkoutType.BIKE, liftDay, tss = 90))
        val merged = build(StrainSource.BOTH, ride).musclesOn(liftDay)
        val liftOnly = build(StrainSource.LIFT_PATH, ride).musclesOn(liftDay)

        // The squat's quads and the ride's calves both have to be in there.
        assertTrue(merged.getValue(MuscleGroups.QUADS) > liftOnly.getValue(MuscleGroups.QUADS))
        assertTrue(merged.containsKey(MuscleGroups.CALVES))
        assertFalse(liftOnly.containsKey(MuscleGroups.CALVES))
    }

    /** The diagram is a finer view of the strain that was counted, never a second helping of it. */
    @Test
    fun `a superseded strength record adds no muscle load in both mode`() {
        val hc = listOf(workout(WorkoutType.STRENGTH, liftDay, tss = 60))
        assertEquals(
            build(StrainSource.LIFT_PATH, hc).musclesOn(liftDay),
            build(StrainSource.BOTH, hc).musclesOn(liftDay)
        )
    }

    @Test
    fun `the muscle history is ordered by date`() {
        val workouts = listOf(
            workout(WorkoutType.BIKE, today, tss = 90),
            workout(WorkoutType.SWIM, today.minusDays(5), tss = 70)
        )
        val dates = build(StrainSource.BOTH, workouts).muscleByDate.map { it.first }
        assertEquals(dates.sorted(), dates)
    }

    // ---- The window ----------------------------------------------------------------------------

    @Test
    fun `sessions outside the window are dropped`() {
        val history = build(
            StrainSource.BOTH,
            listOf(workout(WorkoutType.BIKE, today.minusDays(90), tss = 90))
        )
        assertTrue(history.on(today.minusDays(90)).isEmpty)
    }

    @Test
    fun `datesWithLiftDetail keys only on sessions that actually carry sets`() {
        val empty = LiftSessionLog(id = "s2", date = today, totalSets = 0)
        val dates = StrainAnalytics.datesWithLiftDetail(listOf(liftSession, empty), liftSets)

        assertEquals(setOf(liftDay), dates)
    }
}
