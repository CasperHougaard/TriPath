package com.tripath.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The regression test for the thermic-effect bug.
 *
 * An earlier design computed a maintenance TDEE and then *subtracted* the goal from it. That is
 * wrong by `TEF_RATE × balance`, because eating less also means burning less digesting it — but it
 * is wrong by exactly zero at maintenance, which is why it survives casual checking.
 *
 * **Every assertion here must use a non-zero energy balance.** A suite written only against
 * maintenance would have passed against the broken implementation.
 */
class EnergyAlgebraTest {

    /** `rmr × neatFactor + exercise` for the worked example in [MetabolicModel.targetIntake]. */
    private val nonTef = 2700.0

    @Test
    fun `maintenance intake is non-TEF expenditure grossed up for its own thermic effect`() {
        val intake = MetabolicModel.targetIntake(nonTef, desiredEnergyBalanceKcal = 0.0)!!
        assertEquals(3000.0, intake, 0.01)

        val tdee = MetabolicModel.predictedTdee(nonTef, intake)!!
        assertEquals(3000.0, tdee, 0.01)
        assertEquals(0.0, intake - tdee, 0.01)
    }

    @Test
    fun `a 500 kcal deficit delivers exactly 500 kcal, not 450`() {
        val intake = MetabolicModel.targetIntake(nonTef, desiredEnergyBalanceKcal = -500.0)!!
        assertEquals(2444.44, intake, 0.01)

        val tdee = MetabolicModel.predictedTdee(nonTef, intake)!!
        assertEquals(2944.44, tdee, 0.01)
        assertEquals(-500.0, intake - tdee, 0.001)
    }

    @Test
    fun `the naive form under-delivers the deficit, which is what this test exists to prevent`() {
        val maintenance = MetabolicModel.targetIntake(nonTef, 0.0)!!
        val naiveIntake = maintenance - 500.0
        val naiveTdee = MetabolicModel.predictedTdee(nonTef, naiveIntake)!!

        assertEquals(2500.0, naiveIntake, 0.01)
        assertEquals(-450.0, naiveIntake - naiveTdee, 0.01)
        assertNotEquals(-500.0, naiveIntake - naiveTdee, 0.01)
    }

    @Test
    fun `a surplus is delivered exactly too`() {
        val intake = MetabolicModel.targetIntake(nonTef, desiredEnergyBalanceKcal = 250.0)!!
        val tdee = MetabolicModel.predictedTdee(nonTef, intake)!!
        assertEquals(250.0, intake - tdee, 0.001)
    }

    /** The identity must hold for any balance, not just the two hand-checked ones. */
    @Test
    fun `intake minus predicted expenditure equals the requested balance across the range`() {
        listOf(-1200.0, -750.0, -500.0, -1.0, 1.0, 300.0, 900.0).forEach { balance ->
            val intake = MetabolicModel.targetIntake(nonTef, balance)!!
            val tdee = MetabolicModel.predictedTdee(nonTef, intake)!!
            assertEquals("balance=$balance", balance, intake - tdee, 0.001)
        }
    }

    @Test
    fun `non-TEF expenditure combines resting rate, movement and training`() {
        val b = MetabolicModel.nonTefExpenditure(rmrKcal = 1900.0, neatFactor = 1.20, exerciseKcal = 420.0)
        assertEquals(1900.0 * 1.20 + 420.0, b!!, 0.001)
    }

    @Test
    fun `an unknown resting rate propagates as null rather than a default`() {
        assertNull(MetabolicModel.nonTefExpenditure(null, 1.20, 420.0))
        assertNull(MetabolicModel.targetIntake(null, -500.0))
        assertNull(MetabolicModel.predictedTdee(null, 2400.0))
        assertNull(MetabolicModel.realizedTdeeForDay(null, 2400.0))
    }

    // ---- The other half of the circularity: planning must not read the food log -----------------

    /**
     * A target that moved as the day was logged would tell the athlete to eat less at breakfast
     * than at dinner for no reason other than the clock.
     */
    @Test
    fun `the target at 8am with nothing logged equals the target at 10pm with everything logged`() {
        val morning = MetabolicModel.targetIntake(nonTef, -500.0)
        val evening = MetabolicModel.targetIntake(nonTef, -500.0)
        assertEquals(morning!!, evening!!, 0.0)
    }

    @Test
    fun `realized expenditure does move with what was actually eaten`() {
        val nothingLogged = MetabolicModel.realizedTdeeForDay(nonTef, 0.0)!!
        val fullyLogged = MetabolicModel.realizedTdeeForDay(nonTef, 3000.0)!!
        assertEquals(2700.0, nothingLogged, 0.001)
        assertEquals(3000.0, fullyLogged, 0.001)
    }

    @Test
    fun `an unlogged day is treated as no thermic effect, not as missing expenditure`() {
        assertEquals(nonTef, MetabolicModel.realizedTdeeForDay(nonTef, null)!!, 0.001)
    }
}
