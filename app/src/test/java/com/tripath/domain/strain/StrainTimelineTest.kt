package com.tripath.domain.strain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StrainTimelineTest {

    private val today = LocalDate.of(2026, 8, 20)

    private fun day(daysAgo: Long, strain: StrainVector) =
        DailyStrain(today.minusDays(daysAgo), strain)

    /** A steady habit, so the baseline settles somewhere realistic. */
    private fun habitualHistory(
        perSession: StrainVector = StrainVector(60.0, 80.0, 20.0, 90.0),
        everyNDays: Int = 2,
        overDays: Int = 70
    ): List<DailyStrain> = (0 until overDays)
        .filter { it % everyNDays == 0 }
        .map { day(it.toLong(), perSession) }

    // ---- Decay ---------------------------------------------------------------------------------

    @Test
    fun `strain decays exponentially on each channel's own clock`() {
        val history = listOf(day(0, StrainVector(100.0, 100.0, 100.0, 100.0)))

        val sameDay = StrainTimeline.residualAt(history, today)
        val twoDays = StrainTimeline.residualAt(history, today.plusDays(2))

        assertEquals(100.0, sameDay.lowerImpact, 0.001)
        // Impact clears slowest, systemic fastest — that ordering is the point of the channels.
        assertTrue(twoDays.lowerImpact > twoDays.lowerMuscular)
        assertTrue(twoDays.lowerMuscular > twoDays.systemic)
    }

    /** After one time constant a channel should sit near 1/e of where it started. */
    @Test
    fun `a channel falls to roughly a third after its own time constant`() {
        val history = listOf(day(0, StrainVector(lowerMuscular = 100.0)))
        val afterTau = StrainTimeline.residualAt(history, today.plusDays(2)) // tau is 48h
        assertEquals(100.0 / Math.E, afterTau.lowerMuscular, 2.0)
    }

    @Test
    fun `impact still lingers after muscular soreness has cleared`() {
        val history = listOf(day(0, StrainVector(lowerImpact = 100.0, lowerMuscular = 100.0)))
        val fourDays = StrainTimeline.residualAt(history, today.plusDays(4))
        assertTrue(fourDays.lowerImpact > fourDays.lowerMuscular * 2)
    }

    @Test
    fun `sessions accumulate rather than replacing one another`() {
        val one = StrainTimeline.residualAt(listOf(day(0, StrainVector(systemic = 100.0))), today)
        val two = StrainTimeline.residualAt(
            listOf(day(0, StrainVector(systemic = 100.0)), day(1, StrainVector(systemic = 100.0))),
            today
        )
        assertTrue(two.systemic > one.systemic)
    }

    @Test
    fun `future sessions are ignored`() {
        val history = listOf(day(-5, StrainVector(systemic = 500.0)))
        assertEquals(0.0, StrainTimeline.residualAt(history, today).systemic, 0.001)
    }

    // ---- Baseline normalisation ------------------------------------------------------------------

    /**
     * The numbers are arbitrary units. 300 on the legs means nothing without knowing whether this
     * athlete habitually carries 100 or 500, which is the whole reason for a personal baseline.
     */
    @Test
    fun `the same absolute load reads differently for a big and a small athlete`() {
        val heavyTrainer = habitualHistory(StrainVector(120.0, 160.0, 40.0, 180.0))
        val lightTrainer = habitualHistory(StrainVector(30.0, 40.0, 10.0, 45.0))

        val extraSession = StrainVector(60.0, 80.0, 20.0, 90.0)
        val heavyState = StrainTimeline.stateAt(heavyTrainer + day(0, extraSession), today)
        val lightState = StrainTimeline.stateAt(lightTrainer + day(0, extraSession), today)

        val heavyFreshness = heavyState[StrainChannel.LOWER_MUSCULAR]!!.freshness
        val lightFreshness = lightState[StrainChannel.LOWER_MUSCULAR]!!.freshness
        assertTrue(
            "heavy $heavyFreshness vs light $lightFreshness",
            heavyFreshness > lightFreshness
        )
    }

    /**
     * Without a floor, someone returning from a layoff has a near-zero baseline, every ratio
     * explodes, and one easy jog declares them wrecked.
     */
    @Test
    fun `a returning athlete is not declared wrecked by their first easy session`() {
        val state = StrainTimeline.stateAt(listOf(day(0, StrainVector(lowerMuscular = 20.0))), today)
        assertTrue(state[StrainChannel.LOWER_MUSCULAR]!!.freshness > 60)
    }

    @Test
    fun `habitual training reads as fresh, not as permanently overloaded`() {
        val state = StrainTimeline.stateAt(habitualHistory(), today.minusDays(1))
        // A day after a normal session, on a normal routine, nothing should be in the red.
        StrainChannel.entries.forEach { channel ->
            val freshness = state[channel]!!.freshness
            assertTrue("$channel read $freshness on a habitual routine", freshness > 30)
            assertTrue("$channel pinned at $freshness — the scale is not moving", freshness < 100)
        }
    }

    @Test
    fun `an unusually hard day does drop freshness below habitual`() {
        val history = habitualHistory() + day(0, StrainVector(240.0, 320.0, 80.0, 360.0))
        val state = StrainTimeline.stateAt(history, today)
        assertTrue(state[StrainChannel.LOWER_MUSCULAR]!!.freshness < 60)
    }

    // ---- Freshness and recovery time ---------------------------------------------------------------

    @Test
    fun `freshness is capped at both ends`() {
        assertEquals(100, StrainTimeline.freshness(residual = 10.0, baseline = 100.0))
        assertEquals(0, StrainTimeline.freshness(residual = 1000.0, baseline = 100.0))
    }

    /**
     * The scale has to move for someone who trains consistently. With the top of the scale set at
     * the baseline itself, an athlete whose residual hovers around their own average reads 100%
     * fresh every single day and the bars say nothing.
     */
    @Test
    fun `sitting at your own baseline is ready, not maximally fresh`() {
        val atBaseline = StrainTimeline.freshness(residual = 100.0, baseline = 100.0)
        assertTrue("at baseline read $atBaseline", atBaseline in 65..90)

        val tapered = StrainTimeline.freshness(residual = 50.0, baseline = 100.0)
        val hammered = StrainTimeline.freshness(residual = 190.0, baseline = 100.0)
        assertTrue("tapered $tapered vs baseline $atBaseline", tapered > atBaseline)
        assertTrue("hammered $hammered vs baseline $atBaseline", hammered < atBaseline)
    }

    @Test
    fun `hours to fresh is null once a channel is back at baseline`() {
        assertNull(StrainTimeline.hoursToFresh(50.0, 100.0, StrainChannel.LOWER_MUSCULAR))
        assertNotNull(StrainTimeline.hoursToFresh(300.0, 100.0, StrainChannel.LOWER_MUSCULAR))
    }

    /** Solved from the decay curve, so the answer must actually satisfy it. */
    @Test
    fun `hours to fresh solves the decay equation it claims to`() {
        val hours = StrainTimeline.hoursToFresh(200.0, 100.0, StrainChannel.LOWER_MUSCULAR)!!
        val remaining = 200.0 * Math.exp(-hours / StrainChannel.LOWER_MUSCULAR.tauHours)
        assertEquals(100.0, remaining, 1.0)
    }

    @Test
    fun `the same overload takes longer to clear on the impact channel`() {
        val impact = StrainTimeline.hoursToFresh(200.0, 100.0, StrainChannel.LOWER_IMPACT)!!
        val muscular = StrainTimeline.hoursToFresh(200.0, 100.0, StrainChannel.LOWER_MUSCULAR)!!
        assertTrue(impact > muscular)
    }

    // ---- Degradation -------------------------------------------------------------------------------

    @Test
    fun `no history yields no state rather than a fabricated one`() {
        val state = StrainTimeline.stateAt(emptyList(), today)
        assertTrue(!state.hasData)
        assertTrue(state.channels.isEmpty())
        assertNull(state.mostLoaded)
    }

    @Test
    fun `the most loaded channel is the one with the least freshness`() {
        val history = habitualHistory() + day(0, StrainVector(lowerImpact = 400.0))
        val state = StrainTimeline.stateAt(history, today)
        assertEquals(StrainChannel.LOWER_IMPACT, state.mostLoaded?.channel)
    }

    /** The baseline pass is quadratic, so an unbounded history would crawl on every screen build. */
    @Test
    fun `years of history stay fast enough to compute on demand`() {
        val history = (0 until 1_000).map { day(it.toLong(), StrainVector(50.0, 60.0, 20.0, 70.0)) }
        val startedAt = System.nanoTime()
        StrainTimeline.stateAt(history, today)
        val millis = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("took ${millis}ms", millis < 2_000)
    }

    // ---- Muscle freshness ----------------------------------------------------------------------------

    @Test
    fun `a muscle group trained hard yesterday reads less fresh than one left alone`() {
        val history = listOf(
            today.minusDays(1) to mapOf(MuscleGroups.QUADS to 30.0, MuscleGroups.BACK to 3.0)
        )
        val freshness = StrainTimeline.muscleFreshness(history, today)
        assertTrue(
            "quads ${freshness[MuscleGroups.QUADS]} vs back ${freshness[MuscleGroups.BACK]}",
            freshness.getValue(MuscleGroups.QUADS) < freshness.getValue(MuscleGroups.BACK)
        )
    }

    /**
     * The regression the device test caught, in two parts: scoring each group against the average
     * of the others on the same day meant a balanced session left every group equal to the average,
     * and the channel-scale baseline floor was six times too large for a single muscle group. Both
     * pinned the whole map at 100% the morning after a hard session.
     *
     * Loads here are deliberately in real muscle-group units (single digits to low tens), not the
     * TSS-scale numbers a channel sees — that difference is what the bug was made of.
     */
    @Test
    fun `a balanced hard session does not leave every muscle reading fully fresh`() {
        val groups = listOf(MuscleGroups.QUADS, MuscleGroups.CHEST, MuscleGroups.BACK)
        // A light habitual routine, then one much harder balanced day.
        val history = (2..40).filter { it % 3 == 0 }.map { daysAgo ->
            today.minusDays(daysAgo.toLong()) to groups.associateWith { 8.0 }
        } + listOf(today to groups.associateWith { 40.0 })

        val freshness = StrainTimeline.muscleFreshness(history, today)
        groups.forEach { group ->
            assertTrue(
                "$group read ${freshness[group]}% fresh right after a hard balanced session",
                freshness.getValue(group) < 60
            )
        }
    }

    /**
     * A group trained once a week should read loaded straight after that session and recovered by
     * mid-week — not permanently exhausted simply because it is trained rarely.
     */
    @Test
    fun `an infrequently trained group loads and then recovers`() {
        // Weekly calf work, the most recent session four days ago.
        val history = (4..40).filter { (it - 4) % 7 == 0 }.map { daysAgo ->
            today.minusDays(daysAgo.toLong()) to mapOf(MuscleGroups.CALVES to 12.0)
        }

        val onSessionDay = StrainTimeline.muscleFreshness(history, today.minusDays(4))
        val fourDaysLater = StrainTimeline.muscleFreshness(history, today)

        assertTrue(
            "session day read ${onSessionDay[MuscleGroups.CALVES]}",
            onSessionDay.getValue(MuscleGroups.CALVES) < 50
        )
        assertTrue(
            "four days later read ${fourDaysLater[MuscleGroups.CALVES]}",
            fourDaysLater.getValue(MuscleGroups.CALVES) > 80
        )
    }

    @Test
    fun `muscle freshness recovers as the days pass`() {
        val history = listOf(today.minusDays(5) to mapOf(MuscleGroups.QUADS to 30.0))
        val recent = listOf(today to mapOf(MuscleGroups.QUADS to 30.0))
        assertTrue(
            StrainTimeline.muscleFreshness(history, today).getValue(MuscleGroups.QUADS) >=
                StrainTimeline.muscleFreshness(recent, today).getValue(MuscleGroups.QUADS)
        )
    }

    @Test
    fun `no lifting history yields no muscle map`() {
        assertTrue(StrainTimeline.muscleFreshness(emptyList(), today).isEmpty())
    }
}
