package com.tripath.domain.health

import com.tripath.domain.health.EnergyAvailability.DayEnergy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnergyAvailabilityTest {

    @Test
    fun `energy availability is what is left after training, per kg of fat-free mass`() {
        // 3,000 in, 600 spent training, 60 kg lean -> 40 kcal/kg FFM.
        val result = EnergyAvailability.forDay(intakeKcal = 3000.0, exerciseKcal = 600.0, ffmKg = 60.0)
        assertEquals(40.0, result.kcalPerKgFfm!!, 0.001)
        assertEquals(EnergyAvailabilityBand.REDUCED, result.band)
    }

    @Test
    fun `bands sit on the published reference points`() {
        assertEquals(EnergyAvailabilityBand.ADEQUATE, EnergyAvailability.band(45.0))
        assertEquals(EnergyAvailabilityBand.REDUCED, EnergyAvailability.band(44.9))
        assertEquals(EnergyAvailabilityBand.REDUCED, EnergyAvailability.band(30.0))
        assertEquals(EnergyAvailabilityBand.LOW_SIGNAL, EnergyAvailability.band(29.9))
        assertEquals(EnergyAvailabilityBand.UNKNOWN, EnergyAvailability.band(null))
    }

    /**
     * An unlogged day is not a day of eating nothing. Scoring it as zero would manufacture a
     * screening flag out of a forgotten dinner entry.
     */
    @Test
    fun `an unlogged day scores nothing rather than zero intake`() {
        val result = EnergyAvailability.forDay(intakeKcal = null, exerciseKcal = 600.0, ffmKg = 60.0)
        assertEquals(EnergyAvailabilityBand.UNKNOWN, result.band)
        assertNull(result.kcalPerKgFfm)
    }

    @Test
    fun `without fat-free mass there is no figure to give`() {
        val result = EnergyAvailability.forDay(intakeKcal = 3000.0, exerciseKcal = 600.0, ffmKg = null)
        assertEquals(EnergyAvailabilityBand.UNKNOWN, result.band)
        assertEquals(
            EnergyAvailabilityBand.UNKNOWN,
            EnergyAvailability.forDay(3000.0, 600.0, 0.0).band
        )
    }

    // ---- Rolling -------------------------------------------------------------------------------

    @Test
    fun `the rolling figure averages the window rather than the last day`() {
        val days = List(7) { DayEnergy(intakeKcal = 3000.0, exerciseKcal = 600.0) }
        val result = EnergyAvailability.rolling(days, ffmKg = 60.0)
        assertEquals(40.0, result.kcalPerKgFfm!!, 0.001)
        assertEquals(7, result.daysCounted)
    }

    /**
     * One heavy training day should not flip the band on its own — the whole reason the rolling
     * figure exists rather than a daily one.
     */
    @Test
    fun `a single hard day does not drag the rolling band down`() {
        val days = List(6) { DayEnergy(3000.0, 600.0) } + DayEnergy(3000.0, 2200.0)
        val result = EnergyAvailability.rolling(days, ffmKg = 60.0)
        assertEquals(EnergyAvailabilityBand.REDUCED, result.band)
    }

    @Test
    fun `too few logged days in the window gives no rolling figure`() {
        val days = List(3) { DayEnergy(3000.0, 600.0) } + List(4) { DayEnergy(null, 600.0) }
        assertEquals(EnergyAvailabilityBand.UNKNOWN, EnergyAvailability.rolling(days, 60.0).band)
    }

    @Test
    fun `unlogged days are skipped, not averaged in as zeros`() {
        val days = List(5) { DayEnergy(3000.0, 600.0) } + List(2) { DayEnergy(null, 600.0) }
        val result = EnergyAvailability.rolling(days, ffmKg = 60.0)
        assertEquals(40.0, result.kcalPerKgFfm!!, 0.001)
        assertEquals(5, result.daysCounted)
    }

    @Test
    fun `only the most recent window counts`() {
        val old = List(20) { DayEnergy(1200.0, 900.0) }
        val recent = List(7) { DayEnergy(3000.0, 600.0) }
        val result = EnergyAvailability.rolling(old + recent, ffmKg = 60.0)
        assertEquals(40.0, result.kcalPerKgFfm!!, 0.001)
    }
}
