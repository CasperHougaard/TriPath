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
    /**
     * `rmr × neatFactor + exercise`, after any adaptive correction, plus [plannedExerciseKcal].
     *
     * For today and later that makes it a *forecast* of the day's cost rather than a record of it,
     * which is the point: the carbohydrate band already looks forward, and an energy budget that
     * looked only backward clamped it straight back down again.
     */
    val nonTefExpenditureKcal: Double?,
    /** Total expenditure. Realized from logged intake for past days, predicted for future ones. */
    val tdeeKcal: Double?,
    val rmrKcal: Double?,
    val rmrSource: RmrSource,
    /** Energy from sessions actually logged on the day. Never an estimate. */
    val exerciseKcal: Double,
    val neatFactor: Double,
    /**
     * Estimated energy for the part of the day's plan not yet logged — zero for past days, and for
     * any day whose sessions are all done. Kept apart from [exerciseKcal] so that everything reading
     * "what did this day cost" gets a measurement, and only the target gets the forecast.
     */
    val plannedExerciseKcal: Double = 0.0,
    val nonExerciseSteps: Int?,
    val target: DailyNutritionTarget?,
    val energyAvailability: EnergyAvailabilityResult,
    /** What was actually logged, or null on a day with no entries. Never conflated with zero. */
    val intakeKcal: Double? = null
) {
    /**
     * Signed energy balance for the day: intake minus what it cost. Null unless both are known.
     *
     * Positive is a surplus. This is the figure the readiness model reads, which is why it lives
     * here rather than being re-derived — there is exactly one definition of the gap.
     */
    val balanceKcal: Double?
        get() {
            val intake = intakeKcal ?: return null
            val tdee = tdeeKcal ?: return null
            return intake - tdee
        }
}

/** Everything the fuel screens need, derived in one place so no two of them can disagree. */
data class FuelAnalysis(
    /** Past days and today. Never a forecast — see [forecast]. */
    val days: List<FuelDay> = emptyList(),
    /**
     * Days after today, sized from planned training.
     *
     * Kept out of [days] deliberately. Everything that averages, accumulates or "takes the last
     * one" — rolling energy availability, the adaptive expenditure estimate, [today] — means
     * *observed* history, and a forecast row silently joining that set would corrupt all three
     * while looking like more data.
     */
    val forecast: List<FuelDay> = emptyList(),
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

    /** Tomorrow, when it has been projected. What today's carbohydrate preload is sized for. */
    val tomorrow: FuelDay? get() = forecast.firstOrNull()

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
 *
 * ## The window runs past today
 * Carbohydrate availability is built before work as well as during it, so
 * [NutritionTargets.carbTargetG] needs to know what tomorrow holds. The window therefore extends to
 * [horizonEnd], and those extra days are returned separately as [FuelAnalysis.forecast] — a target
 * may look at them, but nothing that averages observed history may.
 */
object FuelAnalytics {

    /**
     * Energy per TSS point when the athlete has too little history to have their own figure.
     *
     * TSS 100 is an hour at threshold, which costs a trained adult somewhere near 800 kcal — so this
     * is a starting point, not a claim about anyone in particular. It is replaced by
     * [personalKcalPerTss] as soon as there is enough logged work to measure.
     */
    const val DEFAULT_KCAL_PER_TSS = 8.0

    /** Below these, the personal figure is being read off too little work to mean anything. */
    private const val MIN_SESSIONS_FOR_PERSONAL_RATE = 5
    private const val MIN_TSS_FOR_PERSONAL_RATE = 200

    /** One mis-recorded session must not drag the whole estimate somewhere absurd. */
    private const val MIN_PLAUSIBLE_KCAL_PER_TSS = 4.0
    private const val MAX_PLAUSIBLE_KCAL_PER_TSS = 15.0

    /**
     * What a TSS point actually costs *this* athlete, measured from sessions they have done.
     *
     * Used to price a session that is planned but not yet logged. Deriving it from their own history
     * rather than a constant matters because the same TSS costs a 60 kg runner and a 90 kg cyclist
     * very different amounts of energy, and this figure decides how much extra food a hard day gets.
     *
     * Null when there is too little history to measure, in which case [DEFAULT_KCAL_PER_TSS] stands
     * in — the honest fallback, since the alternative is to fuel a planned session as though it were
     * free.
     */
    internal fun personalKcalPerTss(workouts: List<WorkoutLog>, weightKg: Double?): Double? {
        var kcal = 0.0
        var tss = 0
        var sessions = 0
        workouts.forEach { log ->
            val sessionTss = log.computedTSS ?: return@forEach
            if (sessionTss <= 0) return@forEach
            val sessionKcal = EnergyBalanceCalculator.workoutActiveCalories(log, weightKg) ?: return@forEach
            if (sessionKcal <= 0.0) return@forEach
            kcal += sessionKcal
            tss += sessionTss
            sessions++
        }
        if (sessions < MIN_SESSIONS_FOR_PERSONAL_RATE || tss < MIN_TSS_FOR_PERSONAL_RATE) return null
        return (kcal / tss).coerceIn(MIN_PLAUSIBLE_KCAL_PER_TSS, MAX_PLAUSIBLE_KCAL_PER_TSS)
    }

    /**
     * Last day the window covers: at least tomorrow, and at least the end of this week.
     *
     * Tomorrow is what makes today's preload possible at all. Reaching the end of the week matters
     * for a second reason: [NutritionTargets.forWeek] reconciles a week's energy to the goal, and on
     * a Tuesday a window stopping at "today" hands it a two-day week to balance, which is not a week.
     */
    fun horizonEnd(today: LocalDate, locale: Locale = Locale.getDefault()): LocalDate =
        maxOf(today.plusDays(1), today.with(WeekFields.of(locale).dayOfWeek(), 7L))

    fun build(
        workouts: List<WorkoutLog>,
        nutritionByDate: Map<LocalDate, Pair<Double?, Double?>>,
        bodyComposition: List<BodyCompositionLog>,
        dailyActivity: List<DailyActivityLog>,
        profile: UserProfile?,
        /**
         * Planned TSS per date, for today and later only. See the `usePlanned` rule below for why a
         * past date in this map would be ignored even if it were supplied.
         */
        plannedTssByDate: Map<LocalDate, Int>,
        windowStart: LocalDate,
        today: LocalDate,
        weightByDate: Map<LocalDate, Double>,
        /** Planned session minutes per date. Breaks ties TSS alone cannot — see [NutritionTargets.classify]. */
        plannedMinutesByDate: Map<LocalDate, Int> = emptyMap()
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
            val load: NutritionTargets.DayLoad,
            val plannedExerciseKcal: Double
        )

        // Priced from the athlete's own sessions where there are enough of them.
        val kcalPerTss = personalKcalPerTss(
            workouts = workouts,
            weightKg = weightByDate.maxByOrNull { it.key }?.value ?: lastWeight
        ) ?: DEFAULT_KCAL_PER_TSS

        val horizon = horizonEnd(today)
        val raws = mutableListOf<Raw>()
        var cursor = windowStart
        while (!cursor.isAfter(horizon)) {
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
            val actualMinutes = dayWorkouts.sumOf { it.durationMinutes }

            // Today and later take whichever is larger, what has happened or what is planned. At
            // 07:00 today's session is not logged yet, and a target that ignores it under-fuels the
            // session it is supposed to fuel. Past days are actuals only: a session that was planned
            // and then skipped cost nothing, and pretending otherwise would rewrite history.
            val usePlanned = !cursor.isBefore(today)
            val tss = if (usePlanned) maxOf(actualTss, plannedTssByDate[cursor] ?: 0) else actualTss
            val durationMinutes = if (usePlanned) {
                maxOf(actualMinutes, plannedMinutesByDate[cursor] ?: 0)
            } else {
                actualMinutes
            }

            // Energy for the work that is planned but has not happened yet. Only the *unlogged*
            // remainder is priced, so a session that has already synced is counted once, from what
            // it actually cost, rather than twice.
            val unloggedTss = (tss - actualTss).coerceAtLeast(0)
            val plannedExerciseKcal = if (usePlanned) unloggedTss * kcalPerTss else 0.0

            raws += Raw(
                date = cursor,
                rmr = rmr,
                neat = neat,
                exerciseKcal = exerciseKcal,
                plannedExerciseKcal = plannedExerciseKcal,
                // Logged work only, and deliberately: this is what the adaptive estimate calibrates
                // against, and calibrating against a forecast would have it chase its own guesses.
                formulaNonTef = MetabolicModel.nonTefExpenditure(rmr.kcal, neat, exerciseKcal),
                weightKg = weight,
                ffmKg = MetabolicModel.fatFreeMassKg(scan),
                load = NutritionTargets.DayLoad(
                    date = cursor,
                    tss = tss,
                    durationMinutes = durationMinutes,
                    hardShare = hardShare(dayWorkouts)
                )
            )
            cursor = cursor.plusDays(1)
        }

        // Observed history only. Everything below that averages or accumulates reads this rather
        // than `raws`, so a forecast day can never be mistaken for a day that happened.
        val pastRaws = raws.filter { !it.date.isAfter(today) }
        if (pastRaws.isEmpty()) return FuelAnalysis(goal = goal, goalRatePctPerWeek = rate)

        // --- Adaptive correction, from uncorrected inputs only ---
        val energyDays = pastRaws.map { raw ->
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
        val latestWeight = pastRaws.lastOrNull { it.weightKg != null }?.weightKg
        val latestFfm = pastRaws.lastOrNull { it.ffmKg != null }?.ffmKg
        val weekFields = WeekFields.of(Locale.getDefault())

        // Body mass is per day rather than one figure for the window: a target for a past day should
        // reflect the weight the athlete was then, not have today's weigh-in rewrite it.
        // The corrected figure a target is sized from: measured resting and logged work, plus the
        // priced cost of whatever is still planned for the day.
        fun Raw.expenditureForTarget(): Double? =
            formulaNonTef?.times(correction)?.plus(plannedExerciseKcal)

        val targetsByDate = raws
            .groupBy { it.date.get(weekFields.weekBasedYear()) to it.date.get(weekFields.weekOfWeekBasedYear()) }
            .values
            .flatMap { weekRaws ->
                NutritionTargets.forWeek(
                    days = weekRaws.map {
                        NutritionTargets.DayTargetInput(
                            date = it.date,
                            nonTefExpenditureKcal = it.expenditureForTarget(),
                            load = it.load,
                            bodyMassKg = it.weightKg,
                            ffmKg = it.ffmKg
                        )
                    },
                    goal = goal,
                    ratePctPerWeek = rate
                )
            }
            .associateBy { it.date }

        val allDays = raws.map { raw ->
            val correctedNonTef = raw.expenditureForTarget()
            val intake = nutritionByDate[raw.date]?.first
            val target = targetsByDate[raw.date]
            FuelDay(
                date = raw.date,
                nonTefExpenditureKcal = correctedNonTef,
                // Past and present days report what actually happened, including the thermic effect
                // of what was actually eaten. Targets never read this — see MetabolicModel.
                // A future day has no intake to read, so it predicts from the target instead: eating
                // it is the assumption the whole forecast rests on anyway.
                tdeeKcal = if (raw.date.isAfter(today)) {
                    MetabolicModel.predictedTdee(correctedNonTef, target?.kcal)
                } else {
                    MetabolicModel.realizedTdeeForDay(correctedNonTef, intake)
                },
                rmrKcal = raw.rmr.kcal,
                rmrSource = raw.rmr.source,
                neatFactor = raw.neat,
                exerciseKcal = raw.exerciseKcal,
                plannedExerciseKcal = raw.plannedExerciseKcal,
                nonExerciseSteps = activityByDate[raw.date]?.nonExerciseSteps,
                target = target,
                energyAvailability = EnergyAvailability.forDay(intake, raw.exerciseKcal, raw.ffmKg),
                intakeKcal = intake
            )
        }

        val rolling = EnergyAvailability.rolling(
            days = pastRaws.map {
                EnergyAvailability.DayEnergy(nutritionByDate[it.date]?.first, it.exerciseKcal)
            },
            ffmKg = latestFfm
        )

        return FuelAnalysis(
            days = allDays.filter { !it.date.isAfter(today) },
            forecast = allDays.filter { it.date.isAfter(today) },
            rmr = pastRaws.last().rmr,
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
