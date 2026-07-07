package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.domain.health.BodyCompositionAnalytics.Confidence
import com.tripath.domain.health.BodyCompositionAnalytics.RecompositionVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BodyCompositionAnalyticsTest {

    private val DAY = 24L * 60 * 60 * 1000
    private val BASE = 1_700_000_000_000L

    private fun log(
        id: String,
        dayOffset: Long,
        weight: Double? = null,
        fat: Double? = null,
        lean: Double? = null,
        bone: Double? = null
    ) = BodyCompositionLog(
        id = id,
        timestamp = BASE + dayOffset * DAY,
        weightKg = weight,
        bodyFatPercent = fat,
        boneMassKg = bone,
        leanMassKg = lean,
        importedAt = 0L,
        isIgnored = false
    )

    /** 8 scans across 28 days where fat mass falls but fat-free mass is held constant at 64 kg. */
    private fun recompSeries(): List<BodyCompositionLog> = (0 until 8).map { i ->
        val fat = 20.0 - i * 0.4                 // 20% -> 17.2%
        val weight = 64.0 / (1 - fat / 100.0)    // keeps fat-free mass == 64 kg exactly
        log("d$i", dayOffset = i * 4L, weight = weight, fat = fat, lean = 64.0, bone = 3.0)
    }

    @Test
    fun `empty input does not crash and reports insufficient data`() {
        val stats = BodyCompositionAnalytics.analyze(emptyList(), 30, null, null, null)
        assertEquals(0, stats.validCount)
        assertNull(stats.weightTrendKgPerWeek)
        assertEquals(Confidence.LOW, stats.confidence)
        assertEquals(RecompositionVerdict.INSUFFICIENT_DATA, stats.recomposition)
    }

    @Test
    fun `single scan has no trend and low confidence`() {
        val stats = BodyCompositionAnalytics.analyze(
            listOf(log("a", 0, weight = 80.0, fat = 20.0, lean = 64.0)),
            30, null, null, null
        )
        assertEquals(1, stats.validCount)
        assertNull(stats.weightTrendKgPerWeek)
        assertNull(stats.weightChangeKg)
        assertEquals(80.0, stats.smoothedLatestWeight!!, 1e-6)
        assertEquals(Confidence.LOW, stats.confidence)
        assertEquals(RecompositionVerdict.INSUFFICIENT_DATA, stats.recomposition)
    }

    @Test
    fun `weight-only series still computes a weight trend but no fat trend`() {
        val logs = (0 until 6).map { i -> log("w$i", i * 4L, weight = 82.0 - i * 0.5) }
        val stats = BodyCompositionAnalytics.analyze(logs, 30, null, null, null)
        assertNotNull(stats.weightTrendKgPerWeek)
        assertTrue("weight should trend down", stats.weightTrendKgPerWeek!! < 0)
        assertNull(stats.fatMassChangeKg)
        assertNull(stats.fatFreeMassChangeKg)
    }

    @Test
    fun `fat loss with maintained fat-free mass is classified as recomposition`() {
        val stats = BodyCompositionAnalytics.analyze(recompSeries(), 28, null, null, 180)
        assertEquals(Confidence.HIGH, stats.confidence)
        assertTrue("fat mass should fall", stats.fatMassChangeKg!! < -0.7)
        assertTrue("fat-free maintained", abs(stats.fatFreeMassChangeKg!!) <= 0.5)
        assertEquals(RecompositionVerdict.FAT_LOSS_LEAN_MAINTAINED, stats.recomposition)
        assertTrue(stats.weightTrendKgPerWeek!! < 0)
    }

    @Test
    fun `an injected outlier is flagged but barely moves the robust trend`() {
        val clean = recompSeries()
        val cleanSlope = BodyCompositionAnalytics.analyze(clean, 28, null, null, null).weightTrendKgPerWeek!!

        val withOutlier = clean.toMutableList().apply {
            val mid = this[4]
            this[4] = mid.copy(weightKg = mid.weightKg!! + 15.0)  // wild spike
        }
        val stats = BodyCompositionAnalytics.analyze(withOutlier, 28, null, null, null)

        assertTrue("outlier id captured", "d4" in stats.outlierIds)
        assertTrue(
            "Theil–Sen slope should resist the spike",
            abs(stats.weightTrendKgPerWeek!! - cleanSlope) < 0.2
        )
    }

    @Test
    fun `missing height yields null bmi and ffmi without crashing`() {
        val stats = BodyCompositionAnalytics.analyze(recompSeries(), 28, null, null, null)
        assertNull(stats.bmi)
        assertNull(stats.ffmi)
        assertNull(stats.bmiTrendPerWeek)
    }

    @Test
    fun `height present yields bmi and ffmi`() {
        val stats = BodyCompositionAnalytics.analyze(recompSeries(), 28, null, null, 180)
        assertNotNull(stats.bmi)
        assertNotNull(stats.ffmi)
        assertTrue(stats.ffmi!! > 0)
    }

    @Test
    fun `two scans is low confidence, several scans is at least medium`() {
        val two = listOf(
            log("a", 0, weight = 80.0, fat = 20.0, lean = 64.0),
            log("b", 5, weight = 79.0, fat = 19.5, lean = 63.8)
        )
        assertEquals(Confidence.LOW, BodyCompositionAnalytics.analyze(two, 30, null, null, null).confidence)

        val four = (0 until 4).map { i -> log("c$i", i * 6L, weight = 80.0 - i * 0.3, fat = 20.0 - i * 0.2, lean = 64.0) }
        val c = BodyCompositionAnalytics.analyze(four, 30, null, null, null).confidence
        assertFalse("4 scans should not be LOW", c == Confidence.LOW)
    }

    @Test
    fun `rolling median smooths a single spike`() {
        val points = listOf(0L to 10.0, 1L to 10.0, 2L to 30.0, 3L to 10.0, 4L to 10.0)
        val smoothed = BodyCompositionAnalytics.rollingMedian(points, window = 3)
        // The spike at index 2 should be pulled back toward its neighbours.
        assertEquals(10.0, smoothed[2].second, 1e-6)
    }
}
