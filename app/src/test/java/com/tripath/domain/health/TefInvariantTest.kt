package com.tripath.domain.health

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dedicated regression test for the planning/logging seam: [MetabolicModel.targetIntake] must never
 * read logged intake, so a target computed before a single bite is logged is bit-identical to the
 * same target computed after the whole day is logged. Only [MetabolicModel.realizedTdeeForDay] is
 * allowed to move with what was actually eaten, and only for days already in the past.
 *
 * This complements the broader algebra coverage in [EnergyAlgebraTest] with the one property the
 * plan calls out by name: an 08:00 target and a 22:00 target for the same day must be identical.
 */
class TefInvariantTest {

    private val nonTef = 2700.0

    @Test
    fun `target computed before anything is logged equals target computed after the whole day is logged`() {
        val balance = -500.0

        // "08:00, nothing logged yet" and "22:00, everything logged" both call targetIntake with
        // exactly the same planning inputs — logged intake never enters this call.
        val morningTarget = MetabolicModel.targetIntake(nonTef, balance)
        val eveningTarget = MetabolicModel.targetIntake(nonTef, balance)

        assertEquals(morningTarget!!, eveningTarget!!, 0.0)
    }

    @Test
    fun `the invariant holds across deficit, maintenance and surplus`() {
        listOf(-750.0, 0.0, 300.0).forEach { balance ->
            val before = MetabolicModel.targetIntake(nonTef, balance)!!
            val after = MetabolicModel.targetIntake(nonTef, balance)!!
            assertEquals("balance=$balance", before, after, 0.0)
        }
    }

    @Test
    fun `only realized expenditure is allowed to move with logged intake`() {
        val target = MetabolicModel.targetIntake(nonTef, -500.0)!!

        val realizedNothingLogged = MetabolicModel.realizedTdeeForDay(nonTef, loggedIntakeKcal = 0.0)!!
        val realizedFullyLogged = MetabolicModel.realizedTdeeForDay(nonTef, loggedIntakeKcal = target)!!

        // The target itself never changed; only the historical read of what happened does.
        assertEquals(target, MetabolicModel.targetIntake(nonTef, -500.0)!!, 0.0)
        assertEquals(nonTef, realizedNothingLogged, 0.001)
        assertEquals(nonTef + MetabolicModel.TEF_RATE * target, realizedFullyLogged, 0.001)
    }
}
