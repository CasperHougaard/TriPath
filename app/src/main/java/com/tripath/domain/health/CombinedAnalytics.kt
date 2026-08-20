package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.UserProfile
import com.tripath.domain.PerformanceMetrics
import com.tripath.domain.TrainingMetricsCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The four metrics overlaid on the combined-analysis timeline. */
enum class AnalysisMetric { LOAD, INTAKE, WEIGHT, SLEEP }

/** Per-day joined metrics for the analysis window (one entry per calendar day in range). */
data class AnalysisDay(
    val date: LocalDate,
    val tss: Int,
    val ctl: Double,
    /** Banister Acute Training Load (fatigue) on this day. */
    val atl: Double,
    /** Training Stress Balance (form) = ctl − atl. */
    val tsb: Double,
    val intakeKcal: Double?,
    val proteinG: Double?,
    /** Forward-filled last known body weight on/before this day. */
    val weightKg: Double?,
    /** TDEE = resting baseline + training burn; null when demographics/weight are incomplete. */
    val expenditureKcal: Double?,
    /** intake − expenditure; null when either side is missing. */
    val balanceKcal: Double?,
    val sleepMinutes: Int?,
    val sleepScore: Int?,
    /** `rmr x neatFactor + exercise`, after the adaptive correction. The spine of the fuel model. */
    val nonTefExpenditureKcal: Double? = null,
    /** Which equation produced the resting rate behind [nonTefExpenditureKcal]. */
    val rmrSource: RmrSource = RmrSource.UNAVAILABLE,
    /** Non-exercise steps that set the day's NEAT multiplier, or null when none were recorded. */
    val neatSteps: Int? = null,
    val targetKcal: Double? = null,
    val targetProteinG: Double? = null,
    val targetCarbsG: Double? = null,
    val targetFatG: Double? = null,
    /** kcal/kg fat-free mass. A screening signal — see [EnergyAvailability]. */
    val energyAvailability: Double? = null
)

/**
 * Result of [CombinedAnalytics.build]: the normalized overlay [series] (for shape comparison)
 * plus absolute summary stats for the fuel / protein / sleep / weight insight cards.
 * Contains no UI types — the UI layer assigns colours and labels per [AnalysisMetric].
 */
data class CombinedAnalysis(
    val series: Map<AnalysisMetric, List<Pair<Long, Double>>> = emptyMap(),
    /**
     * The per-day rows the summary stats were derived from, oldest first. The UI only needs the
     * aggregates; this is here so consumers that want the raw join (the LiftPath share provider)
     * do not have to reimplement it.
     */
    val days: List<AnalysisDay> = emptyList(),
    val hasData: Boolean = false,
    // Fuel balance
    val avgIntakeKcal: Double? = null,
    val avgExpenditureKcal: Double? = null,
    val avgBalanceKcal: Double? = null,
    val trainingDays: Int = 0,
    val underFueledDays: Int = 0,
    val balanceDaysCounted: Int = 0,
    /** True when a load-adjusted energy balance can be computed (BMR + weight available). */
    val canComputeBalance: Boolean = false,
    // Protein
    val avgProteinG: Double? = null,
    val proteinTargetG: Double? = null,
    val proteinDaysLogged: Int = 0,
    val proteinDaysMet: Int = 0,
    // Sleep
    val avgSleepMinutes: Double? = null,
    val avgSleepScore: Double? = null,
    val nightsLogged: Int = 0,
    // Weight
    val weightDeltaKg: Double? = null,
    val latestWeightKg: Double? = null,
    // Load
    val avgTss: Double? = null,
    val latestCtl: Double? = null,
    /**
     * The full fuel model behind the per-day figures above. Held whole rather than flattened so
     * the Fuel screens can show *why* a number is what it is — which equation produced the resting
     * rate, how settled the adaptive estimate is, what the warnings were.
     */
    val fuel: FuelAnalysis = FuelAnalysis()
)

/**
 * Pure aggregation that joins training load, nutrition, sleep and body-weight by calendar day
 * over a selected window, and derives the fueling / recovery stats. No Android or coroutine
 * dependencies, so it is cheap to unit-test and safe to run off the main thread.
 */
object CombinedAnalytics {

    /** kcal below expenditure that flags a day as meaningfully under-fuelled. */
    private const val UNDER_FUELED_KCAL = 300.0

    fun build(
        allWorkouts: List<WorkoutLog>,
        nutrition: List<NutritionLog>,
        sleep: List<SleepLog>,
        bodyComposition: List<BodyCompositionLog>,
        profile: UserProfile?,
        periodDays: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        /** Whole-day steps, calories and HRV. Empty is fine — NEAT falls back to the profile. */
        dailyActivity: List<DailyActivityLog> = emptyList(),
        /** Future load from [com.tripath.domain.ProjectionSource]. Feeds tomorrow-aware carb targets. */
        plannedTssByDate: Map<LocalDate, Int> = emptyMap()
    ): CombinedAnalysis {
        val workouts = allWorkouts.filter { !it.isIgnored }
        val windowStart = today.minusDays(periodDays)

        // CTL time series across the window, seeded from full history so values are consistent
        // with the Progress/Coach screens (see calculatePerformanceSeries KDoc).
        val perfSeries: List<Pair<LocalDate, PerformanceMetrics>> =
            TrainingMetricsCalculator.calculatePerformanceSeries(
                logs = workouts,
                plannedTssByDate = emptyMap(),
                seriesStart = windowStart,
                seriesEnd = today,
                actualUntil = today
            )
        val metricsByDate: Map<LocalDate, PerformanceMetrics> = perfSeries.toMap()

        val workoutsByDate: Map<LocalDate, List<WorkoutLog>> = workouts.groupBy { it.date }
        val tssByDate: Map<LocalDate, Int> = workoutsByDate.mapValues { (_, logs) ->
            logs.sumOf { it.computedTSS ?: 0 }
        }
        val nutritionByDate: Map<LocalDate, NutritionLog> = nutrition.associateBy { it.date }

        // Latest (longest) sleep session per date, ignoring excluded sessions.
        val sleepByDate: Map<LocalDate, SleepLog> = sleep
            .filter { !it.isIgnored }
            .groupBy { it.date }
            .mapValues { (_, s) -> s.maxByOrNull { it.durationMinutes } ?: s.first() }

        // Last weight reading of each day (timestamp is epoch millis, so bucket to LocalDate).
        val weightByDate: Map<LocalDate, Double> = bodyComposition
            .filter { it.weightKg != null }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .mapValues { (_, logs) -> logs.maxByOrNull { it.timestamp }!!.weightKg!! }

        val userProteinTarget = profile?.proteinTargetG?.toDouble()

        // The whole energy model — resting rate, NEAT, adaptive correction, targets — lives in
        // FuelAnalytics so that the Health tab, the Coach tab and the LiftPath bridge cannot each
        // arrive at a slightly different TDEE.
        val fuel = FuelAnalytics.build(
            workouts = workouts,
            nutritionByDate = nutritionByDate.mapValues { (_, log) -> log.energyKcal to log.proteinG },
            bodyComposition = bodyComposition,
            dailyActivity = dailyActivity,
            profile = profile,
            plannedTssByDate = plannedTssByDate,
            windowStart = windowStart,
            today = today,
            weightByDate = weightByDate
        )
        val fuelByDate = fuel.days.associateBy { it.date }

        // Seed the forward-filled weight with the most recent reading on/before the window start.
        var lastWeight: Double? = weightByDate
            .filterKeys { !it.isAfter(windowStart) }
            .maxByOrNull { it.key }
            ?.value

        val days = mutableListOf<AnalysisDay>()
        var cursor = windowStart
        while (!cursor.isAfter(today)) {
            weightByDate[cursor]?.let { lastWeight = it }
            val weight = lastWeight
            val nut = nutritionByDate[cursor]
            val intake = nut?.energyKcal
            val protein = nut?.proteinG
            val tss = tssByDate[cursor] ?: 0
            val metrics = metricsByDate[cursor]
            val fuelDay = fuelByDate[cursor]
            val expenditure = fuelDay?.tdeeKcal
            val balance = if (intake != null && expenditure != null) intake - expenditure else null
            val night = sleepByDate[cursor]
            days += AnalysisDay(
                date = cursor,
                tss = tss,
                ctl = metrics?.ctl ?: 0.0,
                atl = metrics?.atl ?: 0.0,
                tsb = metrics?.tsb ?: 0.0,
                intakeKcal = intake,
                proteinG = protein,
                weightKg = weight,
                expenditureKcal = expenditure,
                balanceKcal = balance,
                sleepMinutes = night?.durationMinutes,
                sleepScore = night?.sleepScore,
                nonTefExpenditureKcal = fuelDay?.nonTefExpenditureKcal,
                rmrSource = fuelDay?.rmrSource ?: RmrSource.UNAVAILABLE,
                neatSteps = fuelDay?.nonExerciseSteps,
                targetKcal = fuelDay?.target?.kcal,
                targetProteinG = fuelDay?.target?.proteinG,
                targetCarbsG = fuelDay?.target?.carbsG,
                targetFatG = fuelDay?.target?.fatG,
                energyAvailability = fuelDay?.energyAvailability?.kcalPerKgFfm
            )
            cursor = cursor.plusDays(1)
        }

        fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

        val loadPts = days.map { millis(it.date) to it.ctl }
        val intakePts = days.mapNotNull { day -> day.intakeKcal?.let { millis(day.date) to it } }
        // Weight overlay uses actual readings only (no forward-fill) so the line reflects real data.
        val weightPts = days.mapNotNull { day ->
            weightByDate[day.date]?.let { millis(day.date) to it }
        }
        val sleepPts = days.mapNotNull { day ->
            day.sleepMinutes?.let { millis(day.date) to it / 60.0 }
        }

        val series = buildMap<AnalysisMetric, List<Pair<Long, Double>>> {
            if (loadPts.any { it.second > 0.0 }) put(AnalysisMetric.LOAD, loadPts)
            if (intakePts.isNotEmpty()) put(AnalysisMetric.INTAKE, intakePts)
            if (weightPts.isNotEmpty()) put(AnalysisMetric.WEIGHT, weightPts)
            if (sleepPts.isNotEmpty()) put(AnalysisMetric.SLEEP, sleepPts)
        }

        // ---- Aggregate stats ----
        val intakeDays = days.mapNotNull { it.intakeKcal }
        val expenditureDays = days.mapNotNull { it.expenditureKcal }
        val balanceDays = days.mapNotNull { it.balanceKcal }
        val proteinLogged = days.mapNotNull { it.proteinG }
        val nights = days.mapNotNull { it.sleepMinutes }
        val scores = days.mapNotNull { it.sleepScore }
        val trainingTss = days.filter { it.tss > 0 }.map { it.tss.toDouble() }
        val windowWeights = days.mapNotNull { weightByDate[it.date] } // ascending by date

        val canComputeBalance = fuel.rmr.kcal != null
        // Precedence: an explicit user target, then the goal-aware target from the fuel model,
        // then the generic 1.6 g/kg floor for someone with no goal set and no scan.
        val proteinTarget = userProteinTarget
            ?: fuel.today?.target?.proteinG
            ?: HealthReference.proteinTargetGrams(lastWeight)?.min

        return CombinedAnalysis(
            series = series,
            days = days,
            hasData = series.isNotEmpty(),
            avgIntakeKcal = intakeDays.averageOrNull(),
            avgExpenditureKcal = expenditureDays.averageOrNull(),
            avgBalanceKcal = balanceDays.averageOrNull(),
            trainingDays = days.count { it.tss > 0 },
            underFueledDays = days.count { it.balanceKcal != null && it.balanceKcal < -UNDER_FUELED_KCAL },
            balanceDaysCounted = balanceDays.size,
            canComputeBalance = canComputeBalance,
            avgProteinG = proteinLogged.averageOrNull(),
            proteinTargetG = proteinTarget,
            proteinDaysLogged = proteinLogged.size,
            proteinDaysMet = if (proteinTarget != null) proteinLogged.count { it >= proteinTarget } else 0,
            avgSleepMinutes = nights.map { it.toDouble() }.averageOrNull(),
            avgSleepScore = scores.map { it.toDouble() }.averageOrNull(),
            nightsLogged = nights.size,
            weightDeltaKg = if (windowWeights.size >= 2) windowWeights.last() - windowWeights.first() else null,
            latestWeightKg = lastWeight,
            avgTss = trainingTss.averageOrNull(),
            latestCtl = perfSeries.lastOrNull()?.second?.ctl,
            fuel = fuel
        )
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
