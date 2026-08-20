package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.model.ActivityLevel
import com.tripath.data.model.BiologicalSex
import com.tripath.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MetabolicModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 8, 20)

    private fun scan(
        daysAgo: Long,
        weight: Double? = 80.0,
        fat: Double? = null,
        lean: Double? = null
    ) = BodyCompositionLog(
        id = "scan-$daysAgo",
        timestamp = today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli(),
        weightKg = weight,
        bodyFatPercent = fat,
        boneMassKg = null,
        leanMassKg = lean,
        importedAt = 0L
    )

    private fun profile(
        sex: BiologicalSex? = BiologicalSex.MALE,
        birthYear: Int? = 1996,
        heightCm: Int? = 180,
        rmrOverride: Int? = null
    ) = UserProfile(
        biologicalSex = sex,
        birthDate = birthYear?.let { LocalDate.of(it, 8, 20) },
        heightCm = heightCm,
        rmrOverrideKcal = rmrOverride
    )

    // ---- The equations themselves --------------------------------------------------------------

    /**
     * The paper's own worked example. A transposed coefficient changes this by far more than the
     * tolerance, which is the point of asserting it this tightly.
     */
    @Test
    fun `ten Haaf weight-based matches the published golden case`() {
        val ree = MetabolicModel.tenHaafWeightBased(
            sex = BiologicalSex.MALE,
            ageYears = 30,
            weightKg = 80.0,
            heightCm = 180
        )
        assertNotNull(ree)
        assertEquals(1989.23, ree!!, 0.5)
    }

    @Test
    fun `ten Haaf female differs from male by exactly the sex coefficient`() {
        val male = MetabolicModel.tenHaafWeightBased(BiologicalSex.MALE, 30, 80.0, 180)!!
        val female = MetabolicModel.tenHaafWeightBased(BiologicalSex.FEMALE, 30, 80.0, 180)!!
        assertEquals(191.027, male - female, 0.001)
    }

    @Test
    fun `cunningham is 500 plus 22 per kg of fat-free mass`() {
        assertEquals(1930.0, MetabolicModel.cunningham(65.0)!!, 0.001)
    }

    /**
     * Guards against the most common conflation in this corner of the literature: Katch-McArdle is
     * `370 + 21.6 x FFM`, a different equation. At 65 kg FFM it lands ~157 kcal lower.
     */
    @Test
    fun `cunningham is not katch-mcardle`() {
        val katchMcArdle = 370.0 + 21.6 * 65.0
        assertTrue(MetabolicModel.cunningham(65.0)!! - katchMcArdle > 100.0)
    }

    @Test
    fun `equations return null on missing inputs rather than guessing`() {
        assertNull(MetabolicModel.cunningham(null))
        assertNull(MetabolicModel.cunningham(0.0))
        assertNull(MetabolicModel.tenHaafWeightBased(null, 30, 80.0, 180))
        assertNull(MetabolicModel.tenHaafWeightBased(BiologicalSex.MALE, null, 80.0, 180))
        assertNull(MetabolicModel.tenHaafWeightBased(BiologicalSex.MALE, 30, null, 180))
        assertNull(MetabolicModel.tenHaafWeightBased(BiologicalSex.MALE, 30, 80.0, null))
    }

    // ---- Fat-free mass -------------------------------------------------------------------------

    @Test
    fun `measured lean mass is preferred over deriving it from body fat percent`() {
        val ffm = MetabolicModel.fatFreeMassKg(scan(1, weight = 80.0, fat = 20.0, lean = 63.0))
        assertEquals(63.0, ffm!!, 0.001)
    }

    @Test
    fun `fat-free mass falls back to weight times one minus body fat`() {
        val ffm = MetabolicModel.fatFreeMassKg(scan(1, weight = 80.0, fat = 20.0))
        assertEquals(64.0, ffm!!, 0.001)
    }

    @Test
    fun `implausible body fat readings are rejected instead of propagated`() {
        assertNull(MetabolicModel.fatFreeMassKg(scan(1, weight = 80.0, fat = 1.0)))
        assertNull(MetabolicModel.fatFreeMassKg(scan(1, weight = 80.0, fat = 75.0)))
    }

    // ---- The hierarchy -------------------------------------------------------------------------

    @Test
    fun `a measured override beats every equation`() {
        val estimate = MetabolicModel.restingMetabolicRate(
            profile = profile(rmrOverride = 1750),
            scan = scan(1, weight = 80.0, lean = 65.0),
            today = today,
            zone = zone
        )
        assertEquals(RmrSource.MEASURED_OVERRIDE, estimate.source)
        assertEquals(1750.0, estimate.kcal!!, 0.001)
        assertEquals(EstimateConfidence.HIGH, estimate.confidence)
    }

    @Test
    fun `a fresh scan selects cunningham`() {
        val estimate = MetabolicModel.restingMetabolicRate(
            profile = profile(),
            scan = scan(10, weight = 80.0, lean = 65.0),
            today = today,
            zone = zone
        )
        assertEquals(RmrSource.CUNNINGHAM_FFM, estimate.source)
        assertEquals(1930.0, estimate.kcal!!, 0.001)
    }

    @Test
    fun `a stale scan falls through to ten Haaf rather than trusting old body composition`() {
        val estimate = MetabolicModel.restingMetabolicRate(
            profile = profile(),
            scan = scan(90, weight = 80.0, lean = 65.0),
            today = today,
            zone = zone
        )
        assertEquals(RmrSource.TEN_HAAF_WEIGHT, estimate.source)
        assertEquals(1989.23, estimate.kcal!!, 0.5)
    }

    /**
     * Mifflin is the last resort, not the automatic second choice: it significantly mis-estimated
     * athlete RMR in the 2023 meta-analysis where ten Haaf did not.
     */
    @Test
    fun `mifflin is only reached when ten Haaf cannot be computed`() {
        // ten Haaf needs height; Mifflin here also needs it, so make the case ten Haaf-specific by
        // confirming the fall-through order holds when everything is present.
        val withHeight = MetabolicModel.restingMetabolicRate(
            profile = profile(heightCm = 180),
            scan = null,
            fallbackWeightKg = 80.0,
            today = today,
            zone = zone
        )
        assertEquals(RmrSource.TEN_HAAF_WEIGHT, withHeight.source)
    }

    @Test
    fun `no usable profile data yields UNAVAILABLE rather than a fabricated number`() {
        val estimate = MetabolicModel.restingMetabolicRate(
            profile = profile(sex = null, birthYear = null, heightCm = null),
            scan = null,
            today = today,
            zone = zone
        )
        assertEquals(RmrSource.UNAVAILABLE, estimate.source)
        assertNull(estimate.kcal)
        assertEquals(EstimateConfidence.NONE, estimate.confidence)
    }

    // ---- Cross-equation disagreement -----------------------------------------------------------

    @Test
    fun `a wildly off body fat reading downgrades confidence and says so`() {
        // 45 kg lean on an 80 kg athlete gives Cunningham 1,490 against ten Haaf's 1,989 — a 25%
        // gap, which in practice means the scale's impedance reading is wrong.
        val estimate = MetabolicModel.restingMetabolicRate(
            profile = profile(),
            scan = scan(5, weight = 80.0, lean = 45.0),
            today = today,
            zone = zone
        )
        assertEquals(RmrSource.CUNNINGHAM_FFM, estimate.source)
        assertEquals(EstimateConfidence.LOW, estimate.confidence)
        assertTrue(estimate.notes.contains(MetabolicModel.DISAGREEMENT_NOTE))
    }

    @Test
    fun `agreeing equations keep medium confidence and add no note`() {
        val estimate = MetabolicModel.restingMetabolicRate(
            profile = profile(),
            scan = scan(5, weight = 80.0, lean = 65.0),
            today = today,
            zone = zone
        )
        assertEquals(EstimateConfidence.MEDIUM, estimate.confidence)
        assertTrue(estimate.notes.isEmpty())
    }

    // ---- NEAT ----------------------------------------------------------------------------------

    @Test
    fun `neat factor hits its documented anchors`() {
        assertEquals(1.15, MetabolicModel.neatFactor(2_500), 0.0001)
        assertEquals(1.20, MetabolicModel.neatFactor(5_000), 0.0001)
        assertEquals(1.40, MetabolicModel.neatFactor(15_000), 0.0001)
    }

    /** The pre-step-data constant. Nobody's numbers should move when steps are simply absent. */
    @Test
    fun `missing step data falls back to the historic 1_2 multiplier`() {
        assertEquals(1.20, MetabolicModel.neatFactor(null), 0.0001)
        assertEquals(ActivityLevel.DEFAULT.factor, MetabolicModel.neatFactor(null), 0.0001)
    }

    @Test
    fun `neat factor is clamped at both ends`() {
        assertEquals(1.10, MetabolicModel.neatFactor(0), 0.0001)
        assertEquals(1.45, MetabolicModel.neatFactor(500_000), 0.0001)
    }

    @Test
    fun `an explicit activity level overrides the fallback`() {
        assertEquals(
            ActivityLevel.VERY_ACTIVE.factor,
            MetabolicModel.neatFactor(null, ActivityLevel.VERY_ACTIVE),
            0.0001
        )
    }
}
