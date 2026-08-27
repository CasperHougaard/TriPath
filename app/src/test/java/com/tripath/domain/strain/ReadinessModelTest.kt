package com.tripath.domain.strain

import com.tripath.data.model.WorkoutType
import com.tripath.domain.health.EnergyAvailabilityBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessModelTest {

    private fun strain(
        impact: Int = 100,
        legs: Int = 100,
        upper: Int = 100,
        systemic: Int = 100
    ): StrainState {
        fun state(channel: StrainChannel, freshness: Int) = ChannelState(
            channel = channel,
            residual = 100.0,
            baseline = 100.0,
            freshness = freshness,
            hoursToFresh = if (freshness >= 100) null else 24
        )
        return StrainState(
            channels = mapOf(
                StrainChannel.LOWER_IMPACT to state(StrainChannel.LOWER_IMPACT, impact),
                StrainChannel.LOWER_MUSCULAR to state(StrainChannel.LOWER_MUSCULAR, legs),
                StrainChannel.UPPER_MUSCULAR to state(StrainChannel.UPPER_MUSCULAR, upper),
                StrainChannel.SYSTEMIC to state(StrainChannel.SYSTEMIC, systemic)
            ),
            hasData = true
        )
    }

    private val rested = ReadinessInputs(
        strain = strain(),
        tsb = 5.0,
        sleepMinutesLastNight = 480,
        sleepDebtMinutes = 0,
        hrvRecent = 70.0,
        hrvBaseline = 68.0,
        energyBalanceKcal = 0.0,
        energyAvailability = EnergyAvailabilityBand.ADEQUATE,
        soreness = 2,
        mood = 8
    )

    // ---- Degradation ------------------------------------------------------------------------------

    /**
     * Scoring absent inputs as a neutral 50 drags every result toward the middle and makes a
     * well-evidenced 85 indistinguishable from a guess. Missing inputs must be removed instead.
     */
    @Test
    fun `a missing input is dropped, not scored as average`() {
        val full = ReadinessModel.assess(rested).score
        val withoutHrv = ReadinessModel.assess(rested.copy(hrvRecent = null, hrvBaseline = null)).score
        // Everything present is excellent, so dropping one excellent input must not lower the score.
        assertTrue("full $full vs withoutHrv $withoutHrv", withoutHrv >= full - 2)
        assertTrue(withoutHrv >= 80)
    }

    @Test
    fun `no inputs at all says so rather than inventing a number`() {
        val assessment = ReadinessModel.assess(ReadinessInputs())
        assertEquals(0, assessment.score)
        assertTrue(assessment.guidance.contains("Not enough data"))
        assertTrue(assessment.drivers.isEmpty())
    }

    @Test
    fun `a single input still produces a usable reading`() {
        val assessment = ReadinessModel.assess(ReadinessInputs(sleepMinutesLastNight = 300))
        assertTrue(assessment.score in 1..99)
        assertEquals(1, assessment.drivers.size)
    }

    @Test
    fun `HRV needs a personal baseline before it means anything`() {
        val noBaseline = ReadinessModel.assess(
            ReadinessInputs(hrvRecent = 45.0, hrvBaseline = null)
        )
        assertEquals(0, noBaseline.score)
    }

    // ---- The components move the score the right way ------------------------------------------------

    @Test
    fun `a well-rested athlete scores high and is told to train`() {
        val assessment = ReadinessModel.assess(rested)
        assertTrue("scored ${assessment.score}", assessment.score >= 80)
        assertEquals(ReadinessBand.FRESH, assessment.band)
        assertEquals(ReadinessAction.GO, assessment.action)
    }

    @Test
    fun `deep negative form lowers the score`() {
        val tired = ReadinessModel.assess(rested.copy(tsb = -35.0))
        assertTrue(tired.score < ReadinessModel.assess(rested).score)
    }

    @Test
    fun `a bad night lowers the score`() {
        val short = ReadinessModel.assess(rested.copy(sleepMinutesLastNight = 240))
        assertTrue(short.score < ReadinessModel.assess(rested).score)
    }

    @Test
    fun `accumulated sleep debt counts even after one good night`() {
        val debt = ReadinessModel.assess(rested.copy(sleepDebtMinutes = 420))
        assertTrue(debt.score < ReadinessModel.assess(rested).score)
    }

    @Test
    fun `HRV is read against the personal baseline, not an absolute number`() {
        // The same rMSSD is good for one athlete and poor for another.
        val goodForThem = ReadinessModel.assess(rested.copy(hrvRecent = 45.0, hrvBaseline = 42.0))
        val badForThem = ReadinessModel.assess(rested.copy(hrvRecent = 45.0, hrvBaseline = 70.0))
        assertTrue(goodForThem.score > badForThem.score)
    }

    @Test
    fun `chronic under-fuelling lowers the score`() {
        val underfuelled = ReadinessModel.assess(
            rested.copy(
                energyBalanceKcal = -900.0,
                energyAvailability = EnergyAvailabilityBand.LOW_SIGNAL
            )
        )
        assertTrue(underfuelled.score < ReadinessModel.assess(rested).score)
        assertTrue(underfuelled.drivers.any { it.label == "Fuelling" && !it.isPositive })
    }

    /** A deliberate modest deficit is a choice, not a readiness problem. */
    @Test
    fun `a small deficit is not penalised`() {
        val small = ReadinessModel.assess(rested.copy(energyBalanceKcal = -250.0))
        assertEquals(ReadinessModel.assess(rested).score, small.score)
    }

    // ---- The soreness override -----------------------------------------------------------------------

    /**
     * An athlete reporting 9/10 soreness is telling the model something it cannot see. A 5% weight
     * would let a flattering TSB and a good night bury it.
     */
    @Test
    fun `severe soreness caps the score however good everything else looks`() {
        val assessment = ReadinessModel.assess(rested.copy(soreness = 9))
        assertTrue("scored ${assessment.score}", assessment.score <= 45)
        assertTrue(assessment.guidance.contains("Soreness"))
    }

    @Test
    fun `moderate soreness does not trigger the cap`() {
        assertTrue(ReadinessModel.assess(rested.copy(soreness = 5)).score > 45)
    }

    // ---- Drivers explain the number --------------------------------------------------------------------

    @Test
    fun `the worst driver is listed first so the reason is the headline`() {
        val assessment = ReadinessModel.assess(
            rested.copy(tsb = -35.0, sleepMinutesLastNight = 200)
        )
        val first = assessment.drivers.first()
        assertTrue("first driver was ${first.label}", !first.isPositive)
        assertTrue(first.detail.isNotBlank())
    }

    @Test
    fun `every scored component appears as a driver`() {
        val assessment = ReadinessModel.assess(rested)
        val labels = assessment.drivers.map { it.label }.toSet()
        assertEquals(
            setOf("Form", "Regional load", "Sleep", "HRV", "Fuelling", "How you feel"),
            labels
        )
    }

    // ---- Per-discipline verdicts --------------------------------------------------------------------

    /** The payoff of splitting strain by tissue rather than reporting one number. */
    @Test
    fun `hammered legs stop a run but leave swimming alone`() {
        val assessment = ReadinessModel.assess(
            rested.copy(strain = strain(impact = 10, legs = 20, upper = 95, systemic = 90))
        )
        val run = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.RUN }
        val swim = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.SWIM }

        assertTrue(run.action == ReadinessAction.REST || run.action == ReadinessAction.EASY)
        assertEquals(ReadinessAction.GO, swim.action)
    }

    @Test
    fun `wrecked shoulders stop a swim but leave cycling alone`() {
        val assessment = ReadinessModel.assess(
            rested.copy(strain = strain(impact = 95, legs = 90, upper = 15, systemic = 90))
        )
        val swim = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.SWIM }
        val bike = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.BIKE }

        assertTrue(swim.action == ReadinessAction.REST || swim.action == ReadinessAction.EASY)
        assertEquals(ReadinessAction.GO, bike.action)
    }

    /** Impact-only fatigue is exactly the case a single leg channel could never express. */
    @Test
    fun `beaten-up tendons stop a run while the same legs can still ride`() {
        val assessment = ReadinessModel.assess(
            rested.copy(strain = strain(impact = 15, legs = 85, upper = 95, systemic = 90))
        )
        val run = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.RUN }
        val bike = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.BIKE }
        assertTrue(run.action != ReadinessAction.GO)
        assertEquals(ReadinessAction.GO, bike.action)
    }

    @Test
    fun `systemic fatigue limits everything and says so`() {
        val assessment = ReadinessModel.assess(
            rested.copy(strain = strain(impact = 95, legs = 95, upper = 95, systemic = 20))
        )
        assertTrue(assessment.disciplineVerdicts.all { it.action != ReadinessAction.GO })
        assertTrue(assessment.disciplineVerdicts.any { it.reason.contains("Systemic") })
    }

    @Test
    fun `no strain data means no per-discipline advice rather than false confidence`() {
        val assessment = ReadinessModel.assess(rested.copy(strain = StrainState()))
        assertTrue(assessment.disciplineVerdicts.isEmpty())
    }

    /**
     * Fresh tissue is a necessary condition for a hard session, not a sufficient one.
     *
     * Without a floor, a wrecked athlete with untouched muscles was told to train as planned in
     * every discipline while the headline said rest — a contradiction on one screen.
     */
    @Test
    fun `fresh tissue does not override a sleepless, under-fuelled day`() {
        val wrecked = rested.copy(
            strain = strain(),
            tsb = -28.0,
            sleepMinutesLastNight = 200,
            sleepDebtMinutes = 600,
            hrvRecent = 45.0,
            hrvBaseline = 68.0,
            energyBalanceKcal = -1000.0,
            energyAvailability = EnergyAvailabilityBand.LOW_SIGNAL,
            mood = 2
        )
        val assessment = ReadinessModel.assess(wrecked)

        assertTrue("score was ${assessment.score}", assessment.score < 50)
        assertTrue(
            "verdicts were ${assessment.disciplineVerdicts.map { it.discipline to it.action }}",
            assessment.disciplineVerdicts.none { it.action == ReadinessAction.GO }
        )
        assertTrue(assessment.disciplineVerdicts.any { it.reason.contains("the limit") })
    }

    /**
     * The other half of the same rule: the floor must not be built from regional load, or a pair of
     * wrecked legs would drag the score down far enough to veto a swim — undoing the entire reason
     * for splitting strain by tissue.
     */
    @Test
    fun `regional load never sets the floor for a discipline it does not touch`() {
        val assessment = ReadinessModel.assess(
            rested.copy(strain = strain(impact = 5, legs = 5, upper = 100, systemic = 100))
        )
        val swim = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.SWIM }
        val run = assessment.disciplineVerdicts.first { it.discipline == WorkoutType.RUN }

        assertEquals(ReadinessAction.GO, swim.action)
        assertEquals(ReadinessAction.REST, run.action)
    }

    // ---- Load ramp is descriptive only -----------------------------------------------------------------

    /**
     * The acute-to-chronic ratio is widely quoted as an injury predictor and that claim has not
     * held up. It is reported so a jump is visible, and deliberately kept out of the score.
     */
    @Test
    fun `a sharp load ramp is reported but does not change the score`() {
        val flat = ReadinessModel.assess(rested.copy(weeklyLoadRampPct = 0.0))
        val spiking = ReadinessModel.assess(rested.copy(weeklyLoadRampPct = 80.0))
        assertEquals(flat.score, spiking.score)
        assertEquals(80.0, spiking.weeklyLoadRampPct!!, 0.001)
    }

    @Test
    fun `weekly ramp needs a previous week to compare against`() {
        assertNull(ReadinessModel.weeklyRampPct(thisWeekTss = 400, lastWeekTss = 0))
        assertEquals(50.0, ReadinessModel.weeklyRampPct(600, 400)!!, 0.001)
        assertEquals(-25.0, ReadinessModel.weeklyRampPct(300, 400)!!, 0.001)
    }

    // ---- Projection -------------------------------------------------------------------------------------

    /**
     * The app does not know what Friday's sleep or HRV will be and must not imply that it does.
     */
    @Test
    fun `a projection is flagged as one and says what it assumed`() {
        val projected = ReadinessModel.projected(strain(legs = 40), tsb = -12.0)
        assertTrue(projected.isProjected)
        assertTrue(projected.guidance.contains("Projected"))
        assertTrue(projected.guidance.contains("assumed"))
    }

    @Test
    fun `a projection scores only from training load, not from absent recovery data`() {
        val projected = ReadinessModel.projected(strain(), tsb = 5.0)
        assertEquals(setOf("Form", "Regional load"), projected.drivers.map { it.label }.toSet())
    }

    // ---- Bands and actions ------------------------------------------------------------------------------

    @Test
    fun `bands follow the score`() {
        assertEquals(ReadinessBand.FRESH, ReadinessBand.forScore(85))
        assertEquals(ReadinessBand.READY, ReadinessBand.forScore(65))
        assertEquals(ReadinessBand.COMPROMISED, ReadinessBand.forScore(45))
        assertEquals(ReadinessBand.DEPLETED, ReadinessBand.forScore(20))
    }

    @Test
    fun `a thoroughly depleted athlete is told to rest`() {
        val assessment = ReadinessModel.assess(
            ReadinessInputs(
                strain = strain(impact = 5, legs = 5, upper = 5, systemic = 5),
                tsb = -40.0,
                sleepMinutesLastNight = 200,
                sleepDebtMinutes = 600,
                hrvRecent = 30.0,
                hrvBaseline = 60.0,
                energyBalanceKcal = -1200.0,
                energyAvailability = EnergyAvailabilityBand.LOW_SIGNAL,
                soreness = 7,
                mood = 2
            )
        )
        assertEquals(ReadinessAction.REST, assessment.action)
        assertEquals(ReadinessBand.DEPLETED, assessment.band)
        assertNotNull(assessment.guidance)
    }
}
