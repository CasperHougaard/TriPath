package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.model.BiologicalSex
import kotlin.math.abs

/**
 * Robust, derived analytics for a period of body-composition logs.
 *
 * Smart-scale body-fat and lean-mass readings are noisy, so this layer deliberately favours
 * trend and confidence over single-scan precision:
 *  - trends use the Theil–Sen estimator (median of pairwise slopes), which shrugs off outliers;
 *  - the smoothed series uses a rolling median rather than a mean;
 *  - changes compare a baseline to the smoothed endpoint, not raw first-vs-last;
 *  - outliers are flagged (never auto-removed) and surfaced to the user.
 *
 * The layer is pure Kotlin (no Android/Compose deps) so it is unit-testable, and it recomputes
 * fat / fat-free mass internally rather than depending on the UI-layer derivations.
 *
 * All inputs are assumed to be non-ignored logs; ignoring is handled upstream by the repository.
 */
object BodyCompositionAnalytics {

    private const val MILLIS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0

    // Thresholds (kg) for classifying meaningful change. Conservative on purpose.
    private const val MEANINGFUL_KG = 0.7
    private const val MAINTAIN_BAND_KG = 0.5

    enum class Confidence { LOW, MEDIUM, HIGH }

    enum class BodyMetric { WEIGHT, BODY_FAT, FAT_FREE_MASS, BONE }

    enum class RecompositionVerdict {
        FAT_LOSS_LEAN_MAINTAINED,
        WEIGHT_LOSS_LEAN_DOWN,
        WEIGHT_GAIN_LEAN_UP,
        MOSTLY_STABLE,
        INSUFFICIENT_DATA
    }

    enum class InsightTone { NEUTRAL, POSITIVE, CAUTION }

    /** Pre-built, neutral copy for an insight card. Tone drives colour only, never the wording. */
    data class Insight(val title: String, val detail: String, val tone: InsightTone)

    data class BodyCompositionStats(
        val periodDays: Long,
        val validCount: Int,
        val latest: BodyCompositionLog?,
        val baseline: BodyCompositionLog?,
        val avgGapDays: Double?,
        val scansPerWeek: Double?,

        val weightChangeKg: Double?,
        val weightChangePct: Double?,
        val weightTrendKgPerWeek: Double?,
        val smoothedLatestWeight: Double?,

        val bodyFatPointChange: Double?,
        val fatMassChangeKg: Double?,
        val fatMassTrendKgPerWeek: Double?,

        val fatFreeMassChangeKg: Double?,
        val fatFreeMassTrendKgPerWeek: Double?,

        val bmi: Double?,
        val bmiTrendPerWeek: Double?,
        val ffmi: Double?,

        val confidence: Confidence,
        val missingMetric: Map<BodyMetric, Int>,
        val outlierIds: Set<String>,
        val recomposition: RecompositionVerdict,
        val insights: List<Insight>
    )

    /**
     * Analyse a period of logs. [periodLogs] must be non-ignored and sorted ascending by
     * timestamp. Demographics ([sex], [age], [heightCm]) may be null; dependent fields (BMI,
     * FFMI) then return null. Never throws on empty / partial / single-scan input.
     */
    fun analyze(
        periodLogs: List<BodyCompositionLog>,
        periodDays: Long,
        sex: BiologicalSex?,
        age: Int?,
        heightCm: Int?
    ): BodyCompositionStats {
        val logs = periodLogs.sortedBy { it.timestamp }
        val latest = logs.lastOrNull()
        val baseline = logs.firstOrNull()

        // --- Per-metric raw series (timestamp -> value), only where the metric is present. ---
        val weightSeries = logs.mapNotNull { l -> l.weightKg?.let { l.timestamp to it } }
        val fatPctSeries = logs.mapNotNull { l -> l.bodyFatPercent?.let { l.timestamp to it } }
        val fatMassSeries = logs.mapNotNull { l -> fatMass(l)?.let { l.timestamp to it } }
        val fatFreeSeries = logs.mapNotNull { l -> fatFreeMass(l)?.let { l.timestamp to it } }
        val boneSeries = logs.mapNotNull { l -> l.boneMassKg?.let { l.timestamp to it } }

        val weight = analyzeSeries(weightSeries)
        val fatPct = analyzeSeries(fatPctSeries)
        val fatMass = analyzeSeries(fatMassSeries)
        val fatFree = analyzeSeries(fatFreeSeries)

        // --- Spacing / frequency. ---
        val spanDays = if (logs.size >= 2) {
            (logs.last().timestamp - logs.first().timestamp) / MILLIS_PER_DAY
        } else null
        val avgGapDays = if (spanDays != null && logs.size >= 2) spanDays / (logs.size - 1) else null
        val scansPerWeek = if (spanDays != null && spanDays > 0) (logs.size - 1) / spanDays * 7.0 else null

        // --- BMI / FFMI (need height). ---
        val bmi = HealthReference.bmi(latest?.weightKg, heightCm)
        val bmiSeries = logs.mapNotNull { l -> HealthReference.bmi(l.weightKg, heightCm)?.let { l.timestamp to it } }
        val bmiTrendPerWeek = theilSenSlopePerDay(bmiSeries)?.times(7.0)
        val ffmi = HealthReference.ffmi(fatFreeMass(latest), heightCm)

        // --- Outliers (flagged on the reliable weight anchor, never auto-removed). ---
        val outlierTimestamps = madOutlierTimestamps(weightSeries)
        val outlierIds = logs.filter { it.timestamp in outlierTimestamps }.map { it.id }.toSet()

        // --- Missing-metric counts across the period. ---
        val n = logs.size
        val missingMetric = mapOf(
            BodyMetric.WEIGHT to logs.count { it.weightKg == null },
            BodyMetric.BODY_FAT to logs.count { it.bodyFatPercent == null },
            BodyMetric.FAT_FREE_MASS to logs.count { fatFreeMass(it) == null },
            BodyMetric.BONE to logs.count { it.boneMassKg == null }
        )

        // --- Confidence. ---
        val fatCompleteness = if (n > 0) fatPctSeries.size.toDouble() / n else 0.0
        val outlierFraction = if (n > 0) outlierIds.size.toDouble() / n else 0.0
        val confidence = confidenceOf(
            weightCount = weightSeries.size,
            avgGapDays = avgGapDays,
            fatCompleteness = fatCompleteness,
            outlierFraction = outlierFraction
        )

        val weightChangePct = if (weight.change != null && baseline?.weightKg != null && baseline.weightKg != 0.0) {
            weight.change / baseline.weightKg * 100.0
        } else null

        val recomposition = classifyRecomposition(
            confidence = confidence,
            weightChangeKg = weight.change,
            fatMassChangeKg = fatMass.change,
            fatFreeChangeKg = fatFree.change
        )

        val insights = buildInsights(
            periodDays = periodDays,
            confidence = confidence,
            weight = weight,
            fatMass = fatMass,
            fatFree = fatFree,
            latestWeight = latest?.weightKg,
            fatCompleteness = fatCompleteness
        )

        return BodyCompositionStats(
            periodDays = periodDays,
            validCount = n,
            latest = latest,
            baseline = baseline,
            avgGapDays = avgGapDays,
            scansPerWeek = scansPerWeek,
            weightChangeKg = weight.change,
            weightChangePct = weightChangePct,
            weightTrendKgPerWeek = weight.trendPerWeek,
            smoothedLatestWeight = weight.smoothedLatest,
            bodyFatPointChange = fatPct.change,
            fatMassChangeKg = fatMass.change,
            fatMassTrendKgPerWeek = fatMass.trendPerWeek,
            fatFreeMassChangeKg = fatFree.change,
            fatFreeMassTrendKgPerWeek = fatFree.trendPerWeek,
            bmi = bmi,
            bmiTrendPerWeek = bmiTrendPerWeek,
            ffmi = ffmi,
            confidence = confidence,
            missingMetric = missingMetric,
            outlierIds = outlierIds,
            recomposition = recomposition,
            insights = insights
        )
    }

    // ---------------------------------------------------------------------------------------
    // Series analysis
    // ---------------------------------------------------------------------------------------

    private data class SeriesAnalysis(
        val change: Double?,
        val trendPerWeek: Double?,
        val smoothedLatest: Double?
    )

    private fun analyzeSeries(points: List<Pair<Long, Double>>): SeriesAnalysis {
        if (points.isEmpty()) return SeriesAnalysis(null, null, null)
        if (points.size == 1) return SeriesAnalysis(null, null, points.first().second)

        val smoothed = rollingMedian(points, windowFor(points.size))
        val smoothedLatest = smoothed.last().second
        // Baseline -> smoothed endpoint once we can smooth (>=3); else raw first-vs-last.
        val change = if (points.size >= 3) {
            smoothed.last().second - smoothed.first().second
        } else {
            points.last().second - points.first().second
        }
        val trendPerWeek = theilSenSlopePerDay(points)?.times(7.0)
        return SeriesAnalysis(change, trendPerWeek, smoothedLatest)
    }

    private fun windowFor(size: Int): Int = if (size >= 5) 5 else 3

    /** Rolling-median smoothing with the same auto-chosen window that [analyze] uses. */
    fun smoothed(points: List<Pair<Long, Double>>): List<Pair<Long, Double>> =
        rollingMedian(points, windowFor(points.size))

    /**
     * Centred rolling median. Keeps each point's timestamp; smooths its value using the window
     * of neighbours clamped to the series bounds. Returns the input unchanged for < 3 points.
     */
    fun rollingMedian(points: List<Pair<Long, Double>>, window: Int = 3): List<Pair<Long, Double>> {
        if (points.size < 3) return points
        val half = window / 2
        return points.mapIndexed { i, (ts, _) ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(points.lastIndex)
            val slice = points.subList(from, to + 1).map { it.second }
            ts to median(slice)
        }
    }

    /**
     * Theil–Sen slope (value units per day): the median of all pairwise slopes. Robust to
     * outliers. Null when fewer than 3 points or all timestamps collapse to one instant.
     */
    private fun theilSenSlopePerDay(points: List<Pair<Long, Double>>): Double? {
        if (points.size < 3) return null
        val slopes = ArrayList<Double>()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dtDays = (points[j].first - points[i].first) / MILLIS_PER_DAY
                if (dtDays <= 0.0) continue
                slopes += (points[j].second - points[i].second) / dtDays
            }
        }
        if (slopes.isEmpty()) return null
        return median(slopes)
    }

    /**
     * Timestamps whose value is a likely outlier by the median-absolute-deviation rule
     * (|x − median| > 3 × 1.4826 × MAD). Needs >= 4 points and non-zero spread.
     */
    private fun madOutlierTimestamps(points: List<Pair<Long, Double>>): Set<Long> {
        if (points.size < 4) return emptySet()
        val values = points.map { it.second }
        val med = median(values)
        val mad = median(values.map { abs(it - med) })
        if (mad <= 0.0) return emptySet()
        val threshold = 3.0 * 1.4826 * mad
        return points.filter { abs(it.second - med) > threshold }.map { it.first }.toSet()
    }

    // ---------------------------------------------------------------------------------------
    // Confidence & classification
    // ---------------------------------------------------------------------------------------

    private fun confidenceOf(
        weightCount: Int,
        avgGapDays: Double?,
        fatCompleteness: Double,
        outlierFraction: Double
    ): Confidence {
        if (weightCount < 3) return Confidence.LOW
        val spacingOk = avgGapDays != null && avgGapDays <= 10.0
        val enough = weightCount >= 8
        val complete = fatCompleteness >= 0.5
        val cleanish = outlierFraction <= 0.15
        return if (enough && spacingOk && complete && cleanish) Confidence.HIGH else Confidence.MEDIUM
    }

    private fun classifyRecomposition(
        confidence: Confidence,
        weightChangeKg: Double?,
        fatMassChangeKg: Double?,
        fatFreeChangeKg: Double?
    ): RecompositionVerdict {
        if (confidence == Confidence.LOW || fatMassChangeKg == null || fatFreeChangeKg == null) {
            return RecompositionVerdict.INSUFFICIENT_DATA
        }
        return when {
            fatMassChangeKg <= -MEANINGFUL_KG && abs(fatFreeChangeKg) <= MAINTAIN_BAND_KG ->
                RecompositionVerdict.FAT_LOSS_LEAN_MAINTAINED
            weightChangeKg != null && weightChangeKg >= MEANINGFUL_KG && fatFreeChangeKg >= MEANINGFUL_KG ->
                RecompositionVerdict.WEIGHT_GAIN_LEAN_UP
            weightChangeKg != null && weightChangeKg <= -MEANINGFUL_KG && fatFreeChangeKg <= -MEANINGFUL_KG ->
                RecompositionVerdict.WEIGHT_LOSS_LEAN_DOWN
            else -> RecompositionVerdict.MOSTLY_STABLE
        }
    }

    // ---------------------------------------------------------------------------------------
    // Insight copy (neutral, non-judgmental)
    // ---------------------------------------------------------------------------------------

    private fun buildInsights(
        periodDays: Long,
        confidence: Confidence,
        weight: SeriesAnalysis,
        fatMass: SeriesAnalysis,
        fatFree: SeriesAnalysis,
        latestWeight: Double?,
        fatCompleteness: Double
    ): List<Insight> {
        val insights = ArrayList<Insight>()

        weight.trendPerWeek?.let { trend ->
            insights += Insight(
                title = "Weight trend",
                detail = "%+.2f kg/week".format(trend),
                tone = InsightTone.NEUTRAL
            )
        }

        fatMass.change?.let { change ->
            val tone = if (change <= -MAINTAIN_BAND_KG) InsightTone.POSITIVE else InsightTone.NEUTRAL
            insights += Insight(
                title = "Estimated fat mass",
                detail = "%+.1f kg over %d days".format(change, periodDays),
                tone = tone
            )
        }

        fatFree.change?.let { change ->
            val (detail, tone) = when {
                abs(change) <= MAINTAIN_BAND_KG -> "Appears stable" to InsightTone.POSITIVE
                change > 0 -> "%+.1f kg over %d days".format(change, periodDays) to InsightTone.POSITIVE
                else -> "%+.1f kg over %d days".format(change, periodDays) to InsightTone.NEUTRAL
            }
            insights += Insight(title = "Fat-free mass", detail = detail, tone = tone)
        }

        val confidenceDetail = when (confidence) {
            Confidence.HIGH -> "High — plenty of consistent scans this period"
            Confidence.MEDIUM ->
                if (fatCompleteness < 0.5) "Medium — body-fat readings are sparse this period"
                else "Medium — a few more scans will firm up the trend"
            Confidence.LOW -> "Low — not enough consistent scans yet"
        }
        insights += Insight(
            title = "Confidence",
            detail = confidenceDetail,
            tone = if (confidence == Confidence.HIGH) InsightTone.NEUTRAL else InsightTone.CAUTION
        )

        if (latestWeight != null && weight.smoothedLatest != null) {
            val diff = latestWeight - weight.smoothedLatest
            if (abs(diff) >= MEANINGFUL_KG) {
                val where = if (diff > 0) "above" else "below"
                insights += Insight(
                    title = "Latest scan",
                    detail = "$where the trend — one more reading will confirm",
                    tone = InsightTone.CAUTION
                )
            }
        }

        return insights
    }

    // ---------------------------------------------------------------------------------------
    // Derivations & math helpers
    // ---------------------------------------------------------------------------------------

    /** Estimated fat mass (kg) = weight × body-fat% / 100. Null when either input is missing. */
    private fun fatMass(log: BodyCompositionLog?): Double? {
        val w = log?.weightKg ?: return null
        val fat = log.bodyFatPercent ?: return null
        return w * fat / 100.0
    }

    /** Estimated fat-free mass (kg) = weight − fat mass. Null when either input is missing. */
    private fun fatFreeMass(log: BodyCompositionLog?): Double? {
        val w = log?.weightKg ?: return null
        val fm = fatMass(log) ?: return null
        return w - fm
    }

    /** Median of a non-empty list; average of the two middle elements for even sizes. */
    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
