package com.tripath.domain.strain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionStrainTest {

    private val date = LocalDate.of(2026, 8, 20)

    private fun session(strain: StrainVector) = SessionStrain(date = date, added = strain)

    // ---- Decay -----------------------------------------------------------------------------

    @Test
    fun `nothing has decayed at the moment the session ends`() {
        val strain = session(StrainVector(100.0, 100.0, 100.0, 100.0))
        val atZero = strain.residualAfter(0.0)

        StrainChannel.entries.forEach { channel ->
            assertEquals(100.0, atZero[channel], 0.001)
        }
    }

    /** Same arithmetic as the timeline's, so a session and the aggregate cannot drift apart. */
    @Test
    fun `each channel falls to roughly a third after its own time constant`() {
        val strain = session(StrainVector(lowerMuscular = 100.0, systemic = 100.0))

        assertEquals(100.0 / Math.E, strain.residualAfter(2.0).lowerMuscular, 1.0) // tau 48h
        assertEquals(100.0 / Math.E, strain.residualAfter(1.5).systemic, 1.0)      // tau 36h
    }

    @Test
    fun `impact outlasts systemic cost from the same session`() {
        val strain = session(StrainVector(lowerImpact = 100.0, systemic = 100.0))
        val afterThreeDays = strain.residualAfter(3.0)
        assertTrue(afterThreeDays.lowerImpact > afterThreeDays.systemic * 2)
    }

    // ---- The curve -------------------------------------------------------------------------

    @Test
    fun `the curve starts at the session and is monotonically decreasing`() {
        val curve = session(StrainVector(lowerImpact = 200.0)).decayCurve(days = 10)

        assertEquals(0.0, curve.first().first, 0.001)
        assertEquals(10.0, curve.last().first, 0.001)
        assertEquals(200.0, curve.first().second.lowerImpact, 0.001)

        curve.zipWithNext { (_, earlier), (_, later) ->
            assertTrue(later.lowerImpact < earlier.lowerImpact)
        }
    }

    /**
     * The default horizon has to be long enough to show the whole story for the slowest channel,
     * or the chart implies impact never clears.
     */
    @Test
    fun `the default horizon takes even the slowest channel under a tenth`() {
        val curve = session(StrainVector(lowerImpact = 100.0)).decayCurve()
        assertTrue(curve.last().second.lowerImpact < 10.0)
    }

    // ---- Clearing times --------------------------------------------------------------------

    @Test
    fun `an unloaded channel has no clearing time`() {
        val strain = session(StrainVector(lowerMuscular = 50.0))
        assertNull(strain.daysUntilSpent(StrainChannel.UPPER_MUSCULAR))
    }

    @Test
    fun `clearing time is ordered by time constant, not by how much load landed`() {
        // Deliberately lopsided: a trivial amount of impact and a large systemic cost.
        val strain = session(StrainVector(lowerImpact = 5.0, systemic = 500.0))

        val impact = strain.daysUntilSpent(StrainChannel.LOWER_IMPACT)!!
        val systemic = strain.daysUntilSpent(StrainChannel.SYSTEMIC)!!

        assertTrue("impact clears slowest whatever the amount", impact > systemic)
    }

    @Test
    fun `clearing time solves the decay rather than approximating it`() {
        val strain = session(StrainVector(lowerImpact = 100.0))
        val days = strain.daysUntilSpent(StrainChannel.LOWER_IMPACT)!!
        // At the reported time, exactly the spent fraction should remain.
        assertEquals(
            100.0 * SessionStrain.SPENT_FRACTION,
            strain.residualAfter(days).lowerImpact,
            0.01
        )
    }

    // ---- Remaining as of a given day -------------------------------------------------------

    @Test
    fun `all of a session logged today is still on the athlete`() {
        val strain = session(StrainVector(lowerMuscular = 100.0))
        assertEquals(1.0, strain.remainingFraction(StrainChannel.LOWER_MUSCULAR, date), 0.001)
    }

    @Test
    fun `a future date does not report more than the session put on`() {
        val strain = session(StrainVector(lowerMuscular = 100.0))
        // A session dated tomorrow (a clock skew, or a log synced ahead) must not read as 270%.
        assertEquals(
            1.0,
            strain.remainingFraction(StrainChannel.LOWER_MUSCULAR, date.minusDays(1)),
            0.001
        )
    }

    @Test
    fun `an unloaded channel has nothing remaining`() {
        val strain = session(StrainVector(lowerMuscular = 100.0))
        assertEquals(0.0, strain.remainingFraction(StrainChannel.UPPER_MUSCULAR, date), 0.001)
    }

    // ---- Reporting -------------------------------------------------------------------------

    @Test
    fun `loaded channels are reported heaviest first and exclude untouched ones`() {
        val strain = session(StrainVector(lowerImpact = 30.0, lowerMuscular = 90.0, systemic = 60.0))

        assertEquals(
            listOf(
                StrainChannel.LOWER_MUSCULAR,
                StrainChannel.SYSTEMIC,
                StrainChannel.LOWER_IMPACT
            ),
            strain.loadedChannels
        )
    }

    @Test
    fun `a session that cost nothing is empty`() {
        assertTrue(session(StrainVector.ZERO).isEmpty)
        assertTrue(session(StrainVector.ZERO).loadedChannels.isEmpty())
    }
}
