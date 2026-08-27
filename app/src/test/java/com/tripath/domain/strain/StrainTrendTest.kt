package com.tripath.domain.strain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StrainTrendTest {

    private val today = LocalDate.of(2026, 8, 20)

    private fun day(daysAgo: Long, strain: StrainVector) =
        DailyStrain(today.minusDays(daysAgo), strain)

    private fun history(days: List<DailyStrain>) = StrainHistory(days = days)

    // ---- The calendar walk -----------------------------------------------------------------

    @Test
    fun `every day in the window is present, trained or not`() {
        val trend = StrainTrend.build(
            history = history(listOf(day(6, StrainVector(systemic = 100.0)))),
            from = today.minusDays(6),
            to = today
        )

        assertEquals(7, trend.days.size)
        assertEquals(today.minusDays(6), trend.days.first().date)
        assertEquals(today, trend.days.last().date)
    }

    /**
     * The reason [StrainTrend.build] walks the calendar rather than reusing the sparse history: a
     * stacked area over trained days only would draw a rest week as if it never happened.
     */
    @Test
    fun `untrained days report zero input rather than being skipped`() {
        val trend = StrainTrend.build(
            history = history(listOf(day(3, StrainVector(lowerMuscular = 80.0)))),
            from = today.minusDays(6),
            to = today
        )

        val inputs = trend.inputSeries(StrainChannel.LOWER_MUSCULAR)
        assertEquals(7, inputs.size)
        assertEquals(80.0, inputs.single { it.first == today.minusDays(3) }.second, 0.001)
        assertTrue(inputs.filter { it.first != today.minusDays(3) }.all { it.second == 0.0 })
    }

    @Test
    fun `an empty window builds an empty trend`() {
        val trend = StrainTrend.build(history(emptyList()), from = today, to = today.minusDays(1))
        assertTrue(trend.days.isEmpty())
        assertFalse(trend.hasData)
    }

    // ---- Agreement with the point-in-time model --------------------------------------------

    /**
     * The whole reason the trend delegates to [StrainTimeline.stateAt] rather than rolling its own
     * incremental pass: a charted day and a queried day must be the same number, or the chart and
     * the bars beside it will disagree about today.
     */
    @Test
    fun `the last day of a trend matches a direct query for that day`() {
        val days = (0..40).filter { it % 2 == 0 }
            .map { day(it.toLong(), StrainVector(60.0, 80.0, 20.0, 90.0)) }

        val trend = StrainTrend.build(history(days), from = today.minusDays(29), to = today)
        val direct = StrainTimeline.stateAt(days, today)

        StrainChannel.entries.forEach { channel ->
            assertEquals(
                direct[channel]!!.freshness,
                trend.latest!!.state[channel]!!.freshness
            )
        }
    }

    @Test
    fun `freshness dips after a hard day and recovers on the rest days that follow`() {
        // A steady habit, so the baseline settles, then one much harder day four days ago.
        val habitual = (4..70).filter { it % 2 == 0 }
            .map { day(it.toLong(), StrainVector(lowerImpact = 40.0)) }
        val spike = day(3, StrainVector(lowerImpact = 400.0))

        val trend = StrainTrend.build(history(habitual + spike), from = today.minusDays(6), to = today)
        val series = trend.freshnessSeries(StrainChannel.LOWER_IMPACT).toMap()

        val beforeSpike = series.getValue(today.minusDays(4))
        val onSpike = series.getValue(today.minusDays(3))
        val threeDaysLater = series.getValue(today)

        assertTrue("the spike should cost freshness", onSpike < beforeSpike)
        assertTrue("and it should be clearing again by now", threeDaysLater > onSpike)
    }

    // ---- Windowing -------------------------------------------------------------------------

    @Test
    fun `lastDays trims from the old end and keeps today`() {
        val trend = StrainTrend.build(
            history = history(listOf(day(0, StrainVector(systemic = 50.0)))),
            from = today.minusDays(29),
            to = today
        )

        val narrowed = trend.lastDays(7)

        assertEquals(7, narrowed.days.size)
        assertEquals(today, narrowed.days.last().date)
        assertEquals(today.minusDays(6), narrowed.days.first().date)
    }

    @Test
    fun `asking for more days than exist returns the trend unchanged`() {
        val trend = StrainTrend.build(
            history = history(listOf(day(0, StrainVector(systemic = 50.0)))),
            from = today.minusDays(6),
            to = today
        )

        assertEquals(trend.days.size, trend.lastDays(90).days.size)
    }

    // ---- hasInput vs hasData ---------------------------------------------------------------

    /**
     * These are deliberately different questions. A window with no sessions in it can still be
     * carrying residual load from before it, so the freshness chart has something to draw while the
     * daily-load chart does not.
     */
    @Test
    fun `a window after the training still has data but no input`() {
        val trend = StrainTrend.build(
            history = history(listOf(day(10, StrainVector(lowerImpact = 200.0)))),
            from = today.minusDays(2),
            to = today
        )

        assertTrue(trend.hasData)
        assertFalse(trend.hasInput)
    }
}
