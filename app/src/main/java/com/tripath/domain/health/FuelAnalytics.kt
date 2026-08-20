package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.NutritionGoal
import com.tripath.data.model.UserProfile
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** One day's fuel picture: what was spent, what to eat, and how well fuelled it left the athlete. */
data class FuelDay(
    val date: LocalDate,
    /** `rmr × neatFactor + exercise`, after any adaptive correction. */
    val nonTefExpenditureKcal: Double?,
    /** Total expenditure. Realized from logged intake for past days, predicted for future ones. */
    val tdeeKcal: Double?,
    val rmrKcal: Double?,
    val rmrSource: RmrSource,
    val neatFactor: Double,
    val exerciseKcal: Double,
    val nonExerciseSteps: Int?,
    val target: DailyNutritionTarget?,
    val energyAvailability: EnergyAvailabilityResult
)

/** Everything the fuel screens need, derived in one place so no two of them can disagree. */
data class FuelAnalysis(
    val days: List<FuelDay> = emptyList(),
    val rmr: RmrEstimate = RmrEstimate.UNAVAILABLE,
    val nonTef: NonTefEstimate = NonTefEstimate.UNAVAILABLE,
    val goal: NutritionGoal = NutritionGoal.DEFAULT,
    val goalRatePctPerWeek: Double = 0.0,
    val latestWeightKg: Double? = null,
    val latestFfmKg: Double? = null,
    /** 7-day rolling energy availability as of the last day in the window. */
    val rollingEnergyAvailability: EnergyAvailabilityResult = EnergyAvailabilityResult.UNKNOWN,
    /** Watch-reported total calories for the latest day that has one. Cross-check only. */
    val watchTotalCaloriesKcal: Double? = null
) {
    val today: FuelDay? get() = days.lastOrNull()
    val canComputeTargets: Boolean get() = days.any { it.target != null }
}

/**
 * Joins expenditure, intake, body composition and training into a day-by-day fuel picture.
 *
 * Pure — no Android, no coroutines — so the whole model is unit-testable and cheap to run off the
 * main thread. It is the single place the pieces are assembled, which is what stops the Health tab,
 * the Coach tab and the LiftPath bridge each computing a slightly different TDEE.
 *
 * ## Order of operations
 * 1. Forward-fill body weight and body composition, so a day between scans still has figures.
 * 2. Per day: resting rate → NEAT from steps → exercise → **uncorrected** non-TEF expenditure.
 * 3. Blend in what the scale and food log imply ([AdaptiveExpenditure]), producing one correction
 *    ratio for the athlete rather than a per-day fudge.
 * 4. Apply that ratio, then size targets a week at a time so hard days can borrow from easy ones.
 *
 * Step 3 must always be fed the *uncorrected* figures. Feeding corrected ones back in would make
 * the model chase its own output.
 */
object FuelAnalytics {

    fun build(
        workouts: List<WorkoutLog>,
        nutritionByDate: Map<LocalDate, Pair<Double?, Double?>>,
        bodyComposition: List<BodyCompositionLog>,
        dailyActivity: List<DailyActivityLog>,
        profile: UserProfile?,
        plannedTssByDate: Map<LocalDate, Int>,
        windowStart: LocalDate,
        today: LocalDate,
        weightByDate: Map<LocalDate, Double>
    ): FuelAnalysis {
        val goal = profile?.effectiveGoal ?: NutritionGoal.DEFAULT
        val rate = profile?.effectiveGoalRatePctPerWeek ?: 0.0
        val activityLevel = profile?.effectiveActivityLevel ?: com.tripath.data.model.ActivityLevel.DEFAULT

        val activityByDate = dailyActivity.associateBy { it.date }
        val workoutsByDate = workouts.groupBy { it.date }
        val scansByDate = bodyComposition
            .filter { !it.isIgnored }
            .sortedBy { it.timestamp }

        // Seed forward-fill from the most recent reading on or before the window start, so day one
        // is not blank just because the last weigh-in was the week before.
        var lastWeight: Double? = weightByDate.filterKeys { !it.isAfter(windowStart) }
            .maxByOrNull { it.key }?.value

        data class Raw(
            val date: LocalDate,
            val rmr: RmrEstimate,
            val neat: Double,
            val exerciseKcal: Double,
            val formulaNonTef: Double?,
            val weightKg: Double?,
            val ffmKg: Double?,
            val load: NutritionTargets.DayLoad
        )

        val raws = mutableListOf<Raw>()
        var cursor = windowStart
        while (!cursor.isAfter(today)) {
            weightByDate[cursor]?.let { lastWeight = it }
            val weight = lastWeight
            val scan = scansByDate.lastOrNull {
                !java.time.Instant.ofEpochMilli(it.timestamp)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(cursor)
            }
            val rmr = MetabolicModel.restingMetabolicRate(
                profile = profile,
                scan = scan,
                fallbackWeightKg = weight,
                today = cursor
            )
            val activity = activityByDate[cursor]
            val neat = MetabolicModel.neatFactor(activity?.nonExerciseSteps, activityLevel)

            val dayWorkouts = workoutsByDate[cursor] ?: emptyList()
            val exerciseKcal = dayWorkouts
                .sumOf { EnergyBalanceCalculator.workoutActiveCalories(it, weight) ?: 0.0 }

            val actualTss = dayWorkouts.sumOf { it.computedTSS ?: 0 }
            val tss = if (cursor.isAfter(today) || (actualTss == 0 && dayWorkouts.isEmpty())) {
                maxOf(actualTss, plannedTssByDate[cursor] ?: 0)
            } else {
                actualTss
            }

            raws += Raw(
                date = cursor,
                rmr = rmr,
                neat = neat,
                exerciseKcal = exerciseKcal,
                formulaNonTef = MetabolicModel.nonTefExpenditure(rmr.kcal, neat, exerciseKcal),
                weightKg = weight,
                ffmKg = MetabolicModel.fatFreeMassKg(scan),
                load = NutritionTargets.DayLoad(
                    date = cursor,
                    tss = tss,
                    durationMinutes = dayWorkouts.sumOf { it.durationMinutes },
                    hardShare = hardShare(dayWorkouts)
                )
            )
            cursor = cursor.plusDays(1)
        }

        if (raws.isEmpty()) return FuelAnalysis(goal = goal, goalRatePctPerWeek = rate)

        // --- Adaptive correction, from uncorrected inputs only ---
        val energyDays = raws.map { raw ->
            EnergyDay(
                date = raw.date,
                intakeKcal = nutritionByDate[raw.date]?.first,
                weightKg = weightByDate[raw.date],
                formulaNonTefKcal = raw.formulaNonTef
            )
        }
        val nonTefEstimate = AdaptiveExpenditure.estimate(energyDays, today)
        val correction = nonTefEstimate.let { e ->
            val f = e.formulaKcal
            val k = e.kcal
            if (f != null && k != null && f > 0) k / f else 1.0
        }

        // --- Targets, reconciled a week at a time ---
        val latestWeight = raws.lastOrNull { it.weightKg != null }?.weightKg
        val latestFfm = raws.lastOrNull { it.ffmKg != null }?.ffmKg
        val weekFields = WeekFields.of(Locale.getDefault())

        val targetsByDate = raws
            .groupBy { it.date.get(weekFields.weekBasedYear()) to it.date.get(weekFields.weekOfWeekBasedYear()) }
            .values
            .flatMap { weekRaws ->
                NutritionTargets.forWeek(
                    days = weekRaws.map {
                        NutritionTargets.DayTargetInput(
                            date = it.date,
                            nonTefExpenditureKcal = it.formulaNonTef?.times(correction),
                            load = it.load
                        )
                    },
                    goal = goal,
                    ratePctPerWeek = rate,
                    bodyMassKg = latestWeight,
                    ffmKg = latestFfm
                )
            }
            .associateBy { it.date }

        val days = raws.map { raw ->
            val correctedNonTef = raw.formulaNonTef?.times(correction)
            val intake = nutritionByDate[raw.date]?.first
            FuelDay(
                date = raw.date,
                nonTefExpenditureKcal = correctedNonTef,
                // Past and present days report what actually happened, including the thermic effect
                // of what was actually eaten. Targets never read this — see MetabolicModel.
                tdeeKcal = MetabolicModel.realizedTdeeForDay(correctedNonTef, intake),
                rmrKcal = raw.rmr.kcal,
                rmrSource = raw.rmr.source,
                neatFactor = raw.neat,
                exerciseKcal = raw.exerciseKcal,
                nonExerciseSteps = activityByDate[raw.date]?.nonExerciseSteps,
                target = targetsByDate[raw.date],
                energyAvailability = EnergyAvailability.forDay(intake, raw.exerciseKcal, raw.ffmKg)
            )
        }

        val rolling = EnergyAvailability.rolling(
            days = raws.map {
                EnergyAvailability.DayEnergy(nutritionByDate[it.date]?.first, it.exerciseKcal)
            },
            ffmKg = latestFfm
        )

        return FuelAnalysis(
            days = days,
            rmr = raws.last().rmr,
            nonTef = nonTefEstimate,
            goal = goal,
            goalRatePctPerWeek = rate,
            latestWeightKg = latestWeight,
            latestFfmKg = latestFfm,
            rollingEnergyAvailability = rolling,
            watchTotalCaloriesKcal = dailyActivity
                .filter { !it.date.isAfter(today) }
                .maxByOrNull { it.date }
                ?.totalCaloriesKcal
        )
    }

    /**
     * Share of the day's recorded training time spent at or above threshold, or null when no
     * session carried zone data. Positions the carbohydrate target inside its band: an easy long
     * ride and a threshold session can score the same TSS and cost very different glycogen.
     */
    internal fun hardShare(workouts: List<WorkoutLog>): Double? {
        var total = 0L
        var hard = 0L
        workouts.forEach { log ->
            log.hrZoneDistribution?.forEach { (zone, seconds) ->
                total += seconds
                if (zone.contains("4") || zone.contains("5")) hard += seconds
            }
        }
        return if (total <= 0L) null else hard.toDouble() / total
    }
}
