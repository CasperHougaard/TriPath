package com.tripath.domain.health

import com.tripath.data.model.NutritionGoal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How hard a day is, which sets the carbohydrate band. Named after what the athlete is doing rather
 * than after a TSS number, because the bands come from the training-hours literature.
 */
enum class DayKind(val label: String, val carbGramsPerKg: ClosedFloatingPointRange<Double>) {
    /** Rest or light movement. */
    REST("Rest / light", 3.0..5.0),

    /** Around an hour of moderate work. */
    MODERATE("Moderate", 5.0..7.0),

    /** One to three hours of endurance work. */
    ENDURANCE("Endurance", 6.0..10.0),

    /** Over three hours, or a race. */
    EXTREME("Big day", 8.0..12.0)
}

/** Something the athlete should know about a target before following it. */
data class TargetWarning(val code: Code, val message: String) {
    enum class Code {
        /** The requested deficit had to be reduced to keep fat above its health floor. */
        DEFICIT_EASED_FOR_FAT_FLOOR,

        /** The requested deficit exceeded the safe fraction of expenditure and was capped. */
        DEFICIT_CAPPED,

        /** Carbohydrate had to be cut below its band to fit the energy budget. */
        CARBS_BELOW_BAND
    }
}

/** A single day's fuelling target, with the reasoning attached so the UI can explain it. */
data class DailyNutritionTarget(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val dayKind: DayKind,
    val rationale: String,
    val warnings: List<TargetWarning> = emptyList()
)

/**
 * Turns expenditure and a goal into what to eat, day by day.
 *
 * ## Fuel for the work required
 * A single daily number under-fuels the hard days and over-feeds the rest days, and the athlete
 * ends up with the worst of both. Carbohydrate is therefore banded by what the day actually
 * demands — the 3–5 / 5–7 / 6–10 / 8–12 g/kg progression is the standard sports-nutrition guidance
 * for increasing daily training loads — while protein stays near flat and fat holds a floor.
 *
 * ## The three-day window
 * Carbohydrate availability is built before, during and after work, not only during it. So the day
 * before a long session gets a preload bump and the day after a hard one gets a recovery bump.
 * Sizing today purely from today's session would leave a 06:30 long ride starting on yesterday's
 * leftovers.
 *
 * ## The week balances, not each day
 * Hard days borrow from easy ones. Day targets are reconciled so the week still hits the goal's
 * energy budget, which is what makes periodisation compatible with losing or gaining at a
 * prescribed rate.
 */
object NutritionTargets {

    /** Energy in a kilogram of body mass. See [AdaptiveExpenditure.KCAL_PER_KG]. */
    const val KCAL_PER_KG = AdaptiveExpenditure.KCAL_PER_KG

    /** Deficits beyond this fraction of expenditure cost lean mass and training quality. */
    const val MAX_DEFICIT_FRACTION = 0.25

    /** Baseline protein for a training adult, g/kg body mass. */
    const val PROTEIN_BASE_G_PER_KG = 1.8

    /** Protein while deliberately in a deficit, g/kg body mass. */
    const val PROTEIN_DEFICIT_G_PER_KG = 2.2

    /** Protein while building, g/kg body mass. */
    const val PROTEIN_BUILD_G_PER_KG = 2.0

    /** Recomposition leans on protein hardest of all — deficit-like demands without the deficit. */
    const val PROTEIN_RECOMP_G_PER_KG = 2.2

    /** Nobody needs more than this, and past it protein simply displaces carbohydrate. */
    const val PROTEIN_MAX_G_PER_KG = 2.8

    /** Upper bound expressed against fat-free mass, for lean athletes in a deficit. */
    const val PROTEIN_MAX_G_PER_KG_FFM = 3.1

    /**
     * The hard daily fat floor, g/kg body mass. This is the health constraint — essential fatty
     * acids and the absorption of fat-soluble vitamins — and nothing is allowed under it.
     */
    const val FAT_FLOOR_G_PER_KG = 0.8

    /**
     * Fat *aims* for at least this share of energy, but yields to carbohydrate when the day's
     * demand needs it, never going below [FAT_FLOOR_G_PER_KG].
     *
     * The 20% figure is a guideline about habitual dietary pattern, not a daily physiological
     * limit — on a five-hour day the carbohydrate that keeps the athlete upright matters more, and
     * enforcing 20% every single day would make big days mathematically impossible to fuel.
     */
    const val FAT_PREFERRED_ENERGY_FRACTION = 0.20

    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARB = 4.0
    const val KCAL_PER_G_FAT = 9.0

    /** Extra carbohydrate the day before a big session, as a fraction of that session's demand. */
    private const val PRELOAD_FRACTION = 0.25

    /** Extra carbohydrate the day after a big session, to refill what it emptied. */
    private const val RECOVERY_FRACTION = 0.20

    /** One day of training load, from either actuals or [com.tripath.domain.ProjectionSource]. */
    data class DayLoad(
        val date: LocalDate,
        val tss: Int,
        val durationMinutes: Int = 0,
        /** 0..1 share of the day's work at or above threshold, when known. Positions within the band. */
        val hardShare: Double? = null
    )

    /**
     * Classifies a day. TSS is the primary signal because it is available for every session;
     * duration breaks ties, since three easy hours and one hard hour can score alike but do not
     * empty the same amount of glycogen.
     */
    fun classify(load: DayLoad): DayKind = when {
        load.tss >= 200 || load.durationMinutes >= 180 -> DayKind.EXTREME
        load.tss >= 90 || load.durationMinutes >= 90 -> DayKind.ENDURANCE
        load.tss >= 35 || load.durationMinutes >= 45 -> DayKind.MODERATE
        else -> DayKind.REST
    }

    /**
     * Daily energy balance implied by a goal rate, in kcal/day. Negative to lose.
     *
     * Expressed against body mass because that is how the evidence is framed — roughly 0.5–0.7% of
     * body mass per week preserves lean mass and performance far better than double that.
     */
    fun dailyEnergyBalanceKcal(goal: NutritionGoal, ratePctPerWeek: Double, bodyMassKg: Double?): Double {
        val mass = bodyMassKg ?: return 0.0
        val clamped = goal.clampRate(ratePctPerWeek)
        return clamped / 100.0 * mass * KCAL_PER_KG / 7.0
    }

    /**
     * The largest (most negative) daily energy balance permitted, given a non-TEF expenditure.
     *
     * "25% of expenditure" is circular in the same way the main target is: expenditure itself falls
     * as intake falls, because there is less food to digest. Solving rather than approximating it,
     * with `f` = [MAX_DEFICIT_FRACTION], `B` = non-TEF expenditure and `t` = [MetabolicModel.TEF_RATE]:
     *
     * ```
     *   balance = −f·TDEE,  I = (1 − f)·TDEE,  TDEE = B + t·I
     *   ⇒ TDEE = B / (1 − t(1 − f))
     *   ⇒ balance = −f·B / (1 − t(1 − f))
     * ```
     *
     * Using the *maintenance* TDEE instead would set a cap a few tens of kcal too deep — small, but
     * it would mean the number the warning quotes is not the number that was applied.
     */
    fun maxDeficitKcal(nonTefExpenditureKcal: Double): Double {
        val f = MAX_DEFICIT_FRACTION
        val t = MetabolicModel.TEF_RATE
        return -f * nonTefExpenditureKcal / (1.0 - t * (1.0 - f))
    }

    /**
     * Protein for the day, in grams.
     *
     * The band is 1.6–2.2 g/kg for a training adult, rising in a deficit where protein turnover
     * works against retained muscle. The upper bound is cross-checked against the fat-free-mass
     * framing used for lean athletes cutting, and the **lower** of the two ceilings wins — the
     * body-mass and FFM recommendations disagree most for the leanest athletes, and over-prescribing
     * protein just crowds out the carbohydrate that fuels the session.
     */
    fun proteinTargetG(goal: NutritionGoal, bodyMassKg: Double?, ffmKg: Double?): Double? {
        val mass = bodyMassKg ?: return null
        val perKg = when (goal) {
            NutritionGoal.LOSE_FAT -> PROTEIN_DEFICIT_G_PER_KG
            NutritionGoal.BUILD_MUSCLE -> PROTEIN_BUILD_G_PER_KG
            NutritionGoal.RECOMPOSITION -> PROTEIN_RECOMP_G_PER_KG
            NutritionGoal.MAINTAIN -> PROTEIN_BASE_G_PER_KG
        }
        val fromBodyMass = mass * perKg
        val bodyMassCeiling = mass * PROTEIN_MAX_G_PER_KG
        val ffmCeiling = ffmKg?.let { it * PROTEIN_MAX_G_PER_KG_FFM } ?: Double.MAX_VALUE
        return fromBodyMass.coerceAtMost(minOf(bodyMassCeiling, ffmCeiling))
    }

    /**
     * Carbohydrate for the day, in grams, from its own band plus what sits on either side of it.
     *
     * [previousDay] and [nextDay] may be null at the edges of a window, in which case only the
     * day's own demand applies.
     */
    fun carbTargetG(
        today: DayLoad,
        previousDay: DayLoad?,
        nextDay: DayLoad?,
        bodyMassKg: Double?
    ): Double? {
        val mass = bodyMassKg ?: return null
        val kind = classify(today)
        val band = kind.carbGramsPerKg

        // Where in the band to sit: an easy long ride and a threshold session can share a TSS but
        // not a glycogen cost, so intensity decides the position when it is known.
        val position = today.hardShare?.coerceIn(0.0, 1.0) ?: 0.5
        val base = (band.start + (band.endInclusive - band.start) * position) * mass

        // Tomorrow's big session is fuelled starting today.
        val preload = nextDay?.let {
            val nextKind = classify(it)
            if (nextKind >= DayKind.ENDURANCE) {
                (nextKind.carbGramsPerKg.start - band.start).coerceAtLeast(0.0) * mass * PRELOAD_FRACTION
            } else 0.0
        } ?: 0.0

        // Yesterday's big session left a hole.
        val recovery = previousDay?.let {
            val prevKind = classify(it)
            if (prevKind >= DayKind.ENDURANCE) {
                (prevKind.carbGramsPerKg.start - band.start).coerceAtLeast(0.0) * mass * RECOVERY_FRACTION
            } else 0.0
        } ?: 0.0

        return base + preload + recovery
    }

    /**
     * The full target for one day.
     *
     * ## Priority order, and why
     * 1. **Energy** is set by the goal, solved through the thermic effect — see
     *    [MetabolicModel.targetIntake]. The athlete asked to lose or gain at a rate; that is the
     *    one number this function is not entitled to quietly renegotiate.
     * 2. **Protein** is fixed. In a deficit it is what stands between the athlete and losing
     *    muscle, so it is never the macro that gives way.
     * 3. **Fat** holds [FAT_FLOOR_G_PER_KG] as a hard floor and aims for
     *    [FAT_PREFERRED_ENERGY_FRACTION] of energy above it.
     * 4. **Carbohydrate** takes what is left, capped at the day's band.
     *
     * Carbohydrate flexes because it is the performance macro, not a health one — dieting on lower
     * carbohydrate availability is a known cost of dieting, whereas chronic fat or protein
     * deficiency is a different kind of problem. When the budget cannot cover the day's band, the
     * honest answer is a smaller carbohydrate target and a warning, **not** silently abandoning the
     * deficit the athlete asked for. Only the genuinely impossible case — protein plus the fat
     * floor exceeding the whole budget — eases the energy target.
     */
    fun forDay(
        date: LocalDate,
        nonTefExpenditureKcal: Double?,
        goal: NutritionGoal,
        ratePctPerWeek: Double,
        bodyMassKg: Double?,
        ffmKg: Double?,
        today: DayLoad,
        previousDay: DayLoad?,
        nextDay: DayLoad?
    ): DailyNutritionTarget? {
        val nonTef = nonTefExpenditureKcal ?: return null
        val mass = bodyMassKg ?: return null
        val warnings = mutableListOf<TargetWarning>()
        val kind = classify(today)

        var balance = dailyEnergyBalanceKcal(goal, ratePctPerWeek, mass)

        val maxDeficit = maxDeficitKcal(nonTef)
        if (balance < maxDeficit) {
            balance = maxDeficit
            warnings += TargetWarning(
                TargetWarning.Code.DEFICIT_CAPPED,
                "Deficit capped at ${(MAX_DEFICIT_FRACTION * 100).roundToInt()}% of expenditure " +
                    "to protect muscle and training quality"
            )
        }

        var kcal = MetabolicModel.targetIntake(nonTef, balance)!!
        val protein = proteinTargetG(goal, mass, ffmKg) ?: return null
        val wantedCarbs = carbTargetG(today, previousDay, nextDay, mass) ?: return null

        val fatFloorG = mass * FAT_FLOOR_G_PER_KG
        val proteinKcal = protein * KCAL_PER_G_PROTEIN

        // The impossible case: protein plus the fat floor alone exceed the budget. This is the only
        // situation that is allowed to move the energy target, because there is no macro left to
        // take from.
        val nonNegotiableKcal = proteinKcal + fatFloorG * KCAL_PER_G_FAT
        if (kcal < nonNegotiableKcal) {
            val shortfall = nonNegotiableKcal - kcal
            kcal = nonNegotiableKcal
            warnings += TargetWarning(
                TargetWarning.Code.DEFICIT_EASED_FOR_FAT_FLOOR,
                "Eased the target by ${shortfall.roundToInt()} kcal — protein and the fat floor " +
                    "alone need more than the deficit allowed"
            )
        }

        // Fat aims for its preferred share, then gives way toward the hard floor if the day's
        // carbohydrate demand needs the room.
        val fatPreferredG = maxOf(fatFloorG, kcal * FAT_PREFERRED_ENERGY_FRACTION / KCAL_PER_G_FAT)
        val carbsAtPreferredFat = (kcal - proteinKcal - fatPreferredG * KCAL_PER_G_FAT) / KCAL_PER_G_CARB
        val carbsAtFloorFat = (kcal - proteinKcal - fatFloorG * KCAL_PER_G_FAT) / KCAL_PER_G_CARB

        val carbs = when {
            wantedCarbs <= carbsAtPreferredFat -> wantedCarbs
            else -> minOf(wantedCarbs, carbsAtFloorFat).coerceAtLeast(0.0)
        }
        val fat = (kcal - proteinKcal - carbs * KCAL_PER_G_CARB) / KCAL_PER_G_FAT

        if (carbs < kind.carbGramsPerKg.start * mass - 0.001) {
            warnings += TargetWarning(
                TargetWarning.Code.CARBS_BELOW_BAND,
                "Carbohydrate is below what a ${kind.label.lowercase()} day usually needs — the " +
                    "energy budget cannot cover both the session and the goal"
            )
        }

        return DailyNutritionTarget(
            date = date,
            kcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            dayKind = kind,
            rationale = rationale(kind, balance, previousDay, nextDay),
            warnings = warnings
        )
    }

    /**
     * A week of targets whose energy sums to the week's budget.
     *
     * Periodising day by day would otherwise drift: the carbohydrate bands do not care what the
     * goal is, so a week of hard days would quietly overshoot and a week of rest days undershoot.
     * Scaling the surplus or shortfall back across the days keeps the rate the athlete asked for
     * while leaving the *shape* of the week intact.
     */
    fun forWeek(
        days: List<DayTargetInput>,
        goal: NutritionGoal,
        ratePctPerWeek: Double,
        bodyMassKg: Double?,
        ffmKg: Double?
    ): List<DailyNutritionTarget> {
        val raw = days.mapIndexedNotNull { i, input ->
            forDay(
                date = input.date,
                nonTefExpenditureKcal = input.nonTefExpenditureKcal,
                goal = goal,
                ratePctPerWeek = ratePctPerWeek,
                bodyMassKg = bodyMassKg,
                ffmKg = ffmKg,
                today = input.load,
                previousDay = days.getOrNull(i - 1)?.load,
                nextDay = days.getOrNull(i + 1)?.load
            )
        }
        if (raw.isEmpty()) return raw

        val budget = days.sumOf { input ->
            val nonTef = input.nonTefExpenditureKcal ?: return@sumOf 0.0
            MetabolicModel.targetIntake(
                nonTef,
                dailyEnergyBalanceKcal(goal, ratePctPerWeek, bodyMassKg)
            ) ?: 0.0
        }
        val planned = raw.sumOf { it.kcal }
        if (budget <= 0.0 || planned <= 0.0 || abs(planned - budget) < 1.0) return raw

        // Reconcile through carbohydrate only: protein is a floor and fat is at or above one, so
        // carbohydrate is the macro that is *meant* to flex with the work.
        val deltaKcal = budget - planned
        val flexibleCarbG = raw.sumOf { target ->
            // Days already at their band floor cannot give any more back.
            target.carbsG
        }
        if (flexibleCarbG <= 0.0) return raw

        val bodyMass = bodyMassKg ?: return raw
        return raw.map { target ->
            val share = target.carbsG / flexibleCarbG
            val adjustG = deltaKcal * share / KCAL_PER_G_CARB
            val carbs = (target.carbsG + adjustG).coerceAtLeast(0.0)
            // Reconciling can push a day under its band even though the day alone was fine. Say so
            // rather than letting the week silently under-fuel its hardest sessions.
            val bandFloor = target.dayKind.carbGramsPerKg.start * bodyMass
            val warnings = if (
                carbs < bandFloor &&
                target.warnings.none { it.code == TargetWarning.Code.CARBS_BELOW_BAND }
            ) {
                target.warnings + TargetWarning(
                    TargetWarning.Code.CARBS_BELOW_BAND,
                    "This week's energy budget puts carbohydrate below what a ${target.dayKind.label} " +
                        "day normally needs — consider a smaller deficit or an easier week"
                )
            } else {
                target.warnings
            }
            target.copy(
                carbsG = carbs,
                kcal = target.proteinG * KCAL_PER_G_PROTEIN +
                    carbs * KCAL_PER_G_CARB +
                    target.fatG * KCAL_PER_G_FAT,
                warnings = warnings
            )
        }
    }

    /** One day's inputs to [forWeek]. */
    data class DayTargetInput(
        val date: LocalDate,
        val nonTefExpenditureKcal: Double?,
        val load: DayLoad
    )

    private fun rationale(
        kind: DayKind,
        balanceKcal: Double,
        previousDay: DayLoad?,
        nextDay: DayLoad?
    ): String {
        val parts = mutableListOf<String>()
        parts += "${kind.label} day"
        when {
            balanceKcal < -50 -> parts += "${-balanceKcal.roundToInt()} kcal under expenditure"
            balanceKcal > 50 -> parts += "${balanceKcal.roundToInt()} kcal over expenditure"
            else -> parts += "fuelling to match"
        }
        if (nextDay != null && classify(nextDay) >= DayKind.ENDURANCE) {
            parts += "carbs raised to preload tomorrow"
        }
        if (previousDay != null && classify(previousDay) >= DayKind.ENDURANCE) {
            parts += "carbs raised to refill after yesterday"
        }
        return parts.joinToString(" · ")
    }
}
