package com.tripath.domain.coach

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import com.tripath.domain.health.EnergyAvailabilityBand
import com.tripath.domain.strain.ChannelState
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.ReadinessBand
import com.tripath.domain.strain.ReadinessAction
import com.tripath.domain.strain.ReadinessDriver
import com.tripath.domain.strain.StrainChannel
import com.tripath.domain.strain.StrainState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadinessPlanRulesTest {

    private val date = LocalDate.of(2026, 8, 26)

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
            hoursToFresh = if (freshness >= 100) null else 30
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

    private fun readiness(
        state: StrainState,
        drivers: List<ReadinessDriver> = emptyList(),
        rampPct: Double? = null
    ) = ReadinessAssessment(
        score = 70,
        band = ReadinessBand.READY,
        action = ReadinessAction.MODERATE,
        strain = state,
        drivers = drivers,
        weeklyLoadRampPct = rampPct
    )

    private fun plan(type: WorkoutType, tss: Int = 90) = TrainingPlan(
        date = date,
        type = type,
        durationMinutes = 60,
        plannedTSS = tss
    )

    // ---- The generalised heavy-legs rule -----------------------------------------------------------

    /**
     * The case the old date-and-type heuristic got wrong: heavy legs have no bearing on a swim.
     */
    @Test
    fun `wrecked legs warn about a hard run and say nothing about a swim`() {
        val state = strain(impact = 20, legs = 25)
        val runWarnings = ReadinessPlanRules.evaluate(plan(WorkoutType.RUN), plannedZone = 4, readiness(state))
        val swimWarnings = ReadinessPlanRules.evaluate(plan(WorkoutType.SWIM), plannedZone = 4, readiness(state))

        assertTrue(runWarnings.isNotEmpty())
        assertTrue(swimWarnings.isEmpty())
    }

    /** And the case it missed: a long run days ago still limiting today, with no strength involved. */
    @Test
    fun `impact fatigue with no recent strength session is still caught`() {
        val warnings = ReadinessPlanRules.evaluate(
            plan(WorkoutType.RUN), plannedZone = 4, readiness(strain(impact = 20))
        )
        assertTrue(warnings.any { it.title.contains("impact", ignoreCase = true) })
    }

    @Test
    fun `beaten-up tendons warn about running but not about cycling`() {
        val state = strain(impact = 20, legs = 85)
        val run = ReadinessPlanRules.evaluate(plan(WorkoutType.RUN), 4, readiness(state))
        val bike = ReadinessPlanRules.evaluate(plan(WorkoutType.BIKE), 4, readiness(state))
        assertTrue(run.isNotEmpty())
        assertTrue(bike.isEmpty())
    }

    @Test
    fun `tired upper body warns about swimming but not about cycling`() {
        val state = strain(upper = 20, legs = 90, impact = 90)
        assertTrue(ReadinessPlanRules.evaluate(plan(WorkoutType.SWIM), 4, readiness(state)).isNotEmpty())
        assertTrue(ReadinessPlanRules.evaluate(plan(WorkoutType.BIKE), 4, readiness(state)).isEmpty())
    }

    // ---- Easy work on tired legs is often the point --------------------------------------------------

    @Test
    fun `an easy session on a loaded channel is not flagged as a risk`() {
        val warnings = ReadinessPlanRules.evaluate(
            plan(WorkoutType.RUN), plannedZone = 1, readiness(strain(impact = 20))
        )
        assertTrue(warnings.none { it.type == WarningType.INJURY_RISK })
    }

    @Test
    fun `a mildly loaded channel gets advice, not a risk warning`() {
        val warnings = ReadinessPlanRules.evaluate(
            plan(WorkoutType.RUN), plannedZone = 4, readiness(strain(impact = 50))
        )
        assertEquals(1, warnings.size)
        assertEquals(WarningType.RECOVERY_ADVICE, warnings.first().type)
    }

    @Test
    fun `a fully recovered athlete gets no warnings at all`() {
        assertTrue(ReadinessPlanRules.evaluate(plan(WorkoutType.RUN), 5, readiness(strain())).isEmpty())
    }

    // ---- Systemic fatigue overrides -----------------------------------------------------------------

    @Test
    fun `systemic fatigue is reported once rather than per channel`() {
        val warnings = ReadinessPlanRules.evaluate(
            plan(WorkoutType.RUN), plannedZone = 4, readiness(strain(impact = 20, legs = 20, systemic = 20))
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().title.contains("Systemically"))
    }

    // ---- Nothing blocks -------------------------------------------------------------------------------

    /** The planner advises; the athlete knows about the race, the travel and the training partner. */
    @Test
    fun `no readiness warning is ever a blocker`() {
        val state = strain(impact = 5, legs = 5, upper = 5, systemic = 5)
        WorkoutType.entries.forEach { type ->
            ReadinessPlanRules.evaluate(plan(type), plannedZone = 5, readiness(state)).forEach {
                assertTrue("${it.title} was a blocker", !it.isBlocker)
            }
        }
    }

    // ---- Degradation ------------------------------------------------------------------------------------

    @Test
    fun `no readiness data produces no advice rather than false confidence`() {
        assertTrue(ReadinessPlanRules.evaluate(plan(WorkoutType.RUN), 4, null).isEmpty())
        assertTrue(
            ReadinessPlanRules.evaluate(
                plan(WorkoutType.RUN), 4, readiness(StrainState())
            ).isEmpty()
        )
    }

    // ---- Fuelling ----------------------------------------------------------------------------------------

    @Test
    fun `chronic under-fuelling advises holding load flat`() {
        val warnings = ReadinessPlanRules.evaluateFuelling(
            readiness = readiness(strain(), drivers = listOf(
                ReadinessDriver("Fuelling", "averaging 800 kcal under expenditure", -5.0)
            )),
            energyAvailability = EnergyAvailabilityBand.LOW_SIGNAL,
            weeklyRampPct = 25.0
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().message.contains("hold it flat", ignoreCase = true))
        assertTrue(!warnings.first().isBlocker)
    }

    /**
     * Energy availability is a screening signal whose thresholds come largely from female athletes,
     * with the male picture less settled and probably lower. It never vetoes a session.
     */
    @Test
    fun `a low energy availability flag never blocks training`() {
        val warnings = ReadinessPlanRules.evaluateFuelling(
            readiness = readiness(strain()),
            energyAvailability = EnergyAvailabilityBand.LOW_SIGNAL,
            weeklyRampPct = null
        )
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.none { it.isBlocker })
    }

    @Test
    fun `good fuelling produces no warning`() {
        assertTrue(
            ReadinessPlanRules.evaluateFuelling(
                readiness = readiness(strain()),
                energyAvailability = EnergyAvailabilityBand.ADEQUATE,
                weeklyRampPct = 5.0
            ).isEmpty()
        )
    }

    // ---- Discipline mapping mirrors the strain model ------------------------------------------------------

    @Test
    fun `each discipline is limited by the channels it actually loads`() {
        assertEquals(
            listOf(StrainChannel.LOWER_IMPACT, StrainChannel.LOWER_MUSCULAR),
            ReadinessPlanRules.limitingChannels(WorkoutType.RUN)
        )
        assertEquals(listOf(StrainChannel.LOWER_MUSCULAR), ReadinessPlanRules.limitingChannels(WorkoutType.BIKE))
        assertEquals(listOf(StrainChannel.UPPER_MUSCULAR), ReadinessPlanRules.limitingChannels(WorkoutType.SWIM))
    }
}
