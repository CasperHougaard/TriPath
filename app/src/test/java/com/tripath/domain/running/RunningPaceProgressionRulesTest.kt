package com.tripath.domain.running

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningPaceProgressionRulesTest {
    @Test
    fun `week one pace stays at baseline range`() {
        val adjusted = RunningPaceProgressionRules.adjustRange(
            baselinePaceSecPerKm = 330,
            sessionType = RunningSessionType.TEMPO,
            weekIndex = 1,
            totalWeeks = 12,
            baseLow = 315,
            baseHigh = 335
        )

        assertEquals(315, adjusted?.low)
        assertEquals(335, adjusted?.high)
    }

    @Test
    fun `interval improvement does not exceed cap`() {
        val improvementFraction = RunningPaceProgressionRules.improvementFraction(
            sessionType = RunningSessionType.INTERVALS,
            weekIndex = 26,
            totalWeeks = 26
        )

        assertTrue(improvementFraction <= 0.06)
    }

    @Test
    fun `six month horizon caps overall progression`() {
        val adjusted = RunningPaceProgressionRules.adjustRange(
            baselinePaceSecPerKm = 300,
            sessionType = RunningSessionType.INTERVALS,
            weekIndex = 26,
            totalWeeks = 26,
            baseLow = 260,
            baseHigh = 280
        )

        assertEquals(242, adjusted?.low)
        assertEquals(262, adjusted?.high)
    }
}