package com.tripath.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class AdaptiveExpenditureTest {

    private val start = LocalDate.of(2026, 1, 1)

    /**
     * A run of days with constant intake and a constant weight trend, which is the situation the
     * energy-balance identity is exactly solvable for.
     */
    private fun series(
        days: Int,
        intakeKcal: Double? = 2800.0,
        startWeightKg: Double = 80.0,
        kgPerDay: Double = 0.0,
        formulaNonTef: Double? = 2600.0,
        weightEveryNDays: Int = 1
    ): List<EnergyDay> = (0 until days).map { i ->
        EnergyDay(
            date = start.plusDays(i.toLong()),
            intakeKcal = intakeKcal,
            weightKg = if (i % weightEveryNDays == 0) startWeightKg + kgPerDay * i else null,
            formulaNonTefKcal = formulaNonTef
        )
    }

    private fun asOf(days: Int) = start.plusDays((days - 1).toLong())

    // ---- Degradation ---------------------------------------------------------------------------

    @Test
    fun `no data yields no estimate`() {
        assertEquals(NonTefEstimate.UNAVAILABLE, AdaptiveExpenditure.estimate(emptyList(), start))
    }

    @Test
    fun `below the minimum window the formula is used unchanged`() {
        val days = series(20)
        val estimate = AdaptiveExpenditure.estimate(days, asOf(20))
        assertEquals(0.0, estimate.observedWeight, 0.0)
        assertEquals(2600.0, estimate.kcal!!, 0.001)
        assertNull(estimate.observedKcal)
    }

    @Test
    fun `sparse intake logging keeps adaptation switched off however long the window`() {
        // 60 days but only every third day logged: 33% coverage, below every rung of the ladder.
        val days = (0 until 60).map { i ->
            EnergyDay(
                date = start.plusDays(i.toLong()),
                intakeKcal = if (i % 3 == 0) 2800.0 else null,
                weightKg = 80.0 - 0.01 * i,
                formulaNonTefKcal = 2600.0
            )
        }
        val estimate = AdaptiveExpenditure.estimate(days, asOf(60))
        assertEquals(0.0, estimate.observedWeight, 0.0)
        assertEquals(2600.0, estimate.kcal!!, 0.001)
    }

    // ---- The ladder ----------------------------------------------------------------------------

    @Test
    fun `the weight ladder climbs only with both window length and coverage`() {
        assertEquals(0.0, AdaptiveExpenditure.observedWeight(27, 1.0, 20), 0.0)
        assertEquals(0.15, AdaptiveExpenditure.observedWeight(28, 0.70, 8), 0.0)
        assertEquals(0.15, AdaptiveExpenditure.observedWeight(35, 0.70, 8), 0.0)
        assertEquals(0.35, AdaptiveExpenditure.observedWeight(35, 0.80, 10), 0.0)
        assertEquals(
            AdaptiveExpenditure.MAX_OBSERVED_WEIGHT,
            AdaptiveExpenditure.observedWeight(42, 0.85, 12),
            0.0
        )
    }

    @Test
    fun `dense weighing cannot compensate for thin intake logging`() {
        assertEquals(0.0, AdaptiveExpenditure.observedWeight(42, 0.50, 42), 0.0)
    }

    // ---- The observation itself ------------------------------------------------------------------

    /**
     * Weight held flat on 2,800 kcal means expenditure is 2,800. Stripping the thermic effect of
     * that intake leaves a non-TEF expenditure of 2,800 − 280 = 2,520.
     */
    @Test
    fun `stable weight implies expenditure equal to intake, less its thermic effect`() {
        val days = series(days = 60, intakeKcal = 2800.0, kgPerDay = 0.0)
        val estimate = AdaptiveExpenditure.estimate(days, asOf(60))
        assertNotNull(estimate.observedKcal)
        assertEquals(2520.0, estimate.observedKcal!!, 1.0)
    }

    @Test
    fun `losing weight implies higher expenditure than intake alone`() {
        val losing = AdaptiveExpenditure.estimate(
            series(days = 60, intakeKcal = 2800.0, kgPerDay = -0.05), asOf(60)
        )
        val stable = AdaptiveExpenditure.estimate(
            series(days = 60, intakeKcal = 2800.0, kgPerDay = 0.0), asOf(60)
        )
        assertTrue(losing.observedKcal!! > stable.observedKcal!!)
    }

    // ---- The daily budget ------------------------------------------------------------------------

    /**
     * The clamp is a *per calendar day* budget, not a per call one. Four recomputations in the same
     * minute — app open, Health Connect sync, nutrition edit, body-scan sync — must land on exactly
     * the same number as one.
     */
    @Test
    fun `recomputing repeatedly on the same day does not compound the adaptation`() {
        val days = series(days = 60, intakeKcal = 3400.0, kgPerDay = 0.0)
        val once = AdaptiveExpenditure.estimate(days, asOf(60)).kcal!!
        val again = (1..4).map { AdaptiveExpenditure.estimate(days, asOf(60)).kcal!! }
        again.forEach { assertEquals(once, it, 0.0) }
    }

    @Test
    fun `adaptation cannot outrun its daily budget`() {
        // A large gap between formula and observation, with the ladder at its top rung.
        val days = series(days = 45, intakeKcal = 3600.0, kgPerDay = 0.0, formulaNonTef = 2400.0)
        val atDay29 = AdaptiveExpenditure.estimate(days.take(29), start.plusDays(28)).kcal!!
        val atDay30 = AdaptiveExpenditure.estimate(days.take(30), start.plusDays(29)).kcal!!
        assertTrue(
            "moved ${abs(atDay30 - atDay29)} kcal in one day",
            abs(atDay30 - atDay29) <= AdaptiveExpenditure.MAX_DAILY_ADAPTATION_KCAL + 0.001
        )
    }

    @Test
    fun `the blend never strays further from the formula than the total offset allows`() {
        val formula = 2400.0
        val days = series(days = 90, intakeKcal = 4200.0, kgPerDay = 0.0, formulaNonTef = formula)
        val estimate = AdaptiveExpenditure.estimate(days, asOf(90))
        val maxOffset = formula * AdaptiveExpenditure.MAX_TOTAL_OFFSET_FRACTION
        assertTrue(abs(estimate.kcal!! - formula) <= maxOffset + 0.001)
    }

    // ---- Robustness ------------------------------------------------------------------------------

    /**
     * A hard leg session plus a carb refeed can add well over a kilo of water and glycogen in a day.
     * Read as fat, that is ~8,000 kcal of apparent error. A robust slope must shrug it off.
     */
    @Test
    fun `a glycogen and water swing does not move the estimate materially`() {
        val clean = series(days = 60, intakeKcal = 2800.0, kgPerDay = 0.0)
        val swung = clean.mapIndexed { i, day ->
            when (i) {
                40 -> day.copy(weightKg = day.weightKg!! + 1.4)
                41 -> day.copy(weightKg = day.weightKg!! + 1.1)
                else -> day
            }
        }
        val before = AdaptiveExpenditure.estimate(clean, asOf(60)).kcal!!
        val after = AdaptiveExpenditure.estimate(swung, asOf(60)).kcal!!
        assertTrue("moved ${abs(after - before)} kcal", abs(after - before) < 50.0)
    }

    @Test
    fun `a single outlier cannot set the trend direction`() {
        val flat = series(days = 60, intakeKcal = 2800.0, kgPerDay = 0.0)
        val withSpike = flat.mapIndexed { i, day ->
            if (i == 59) day.copy(weightKg = day.weightKg!! - 2.5) else day
        }
        val slope = AdaptiveExpenditure.weightSlopeKgPerDay(withSpike)!!
        assertTrue("slope was $slope", abs(slope) < 0.01)
    }

    // ---- Determinism -----------------------------------------------------------------------------

    @Test
    fun `the estimate depends only on the data, not on how the list is ordered`() {
        val days = series(days = 60)
        val forwards = AdaptiveExpenditure.estimate(days, asOf(60)).kcal!!
        val shuffled = AdaptiveExpenditure.estimate(days.reversed(), asOf(60)).kcal!!
        assertEquals(forwards, shuffled, 0.0)
    }

    @Test
    fun `future days are ignored`() {
        val days = series(days = 60)
        val truncated = AdaptiveExpenditure.estimate(days.take(30), asOf(30)).kcal!!
        val withFuture = AdaptiveExpenditure.estimate(days, asOf(30)).kcal!!
        assertEquals(truncated, withFuture, 0.0)
    }

    @Test
    fun `long histories stay fast enough to run on every screen build`() {
        val days = series(days = 1_000)
        val startedAt = System.nanoTime()
        AdaptiveExpenditure.estimate(days, asOf(1_000))
        val millis = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("took ${millis}ms", millis < 2_000)
    }
}
