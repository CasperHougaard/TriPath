package com.tripath.domain.health

import com.tripath.data.model.NutritionGoal
import com.tripath.domain.health.NutritionTargets.DayLoad
import com.tripath.domain.health.NutritionTargets.DayTargetInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class NutritionTargetsTest {

    private val date = LocalDate.of(2026, 8, 20)
    private val mass = 80.0
    private val ffm = 65.0
    private val nonTef = 2700.0

    private fun load(tss: Int, minutes: Int = 0, hardShare: Double? = null, day: LocalDate = date) =
        DayLoad(day, tss, minutes, hardShare)

    /**
     * Resting rate plus daily movement, before any training. A real caller adds the day's exercise
     * to this — see [expenditureFor].
     */
    private val restingAndNeat = 2280.0

    /**
     * Expenditure that moves with the day's training, which is what any real caller passes.
     *
     * Holding expenditure fixed while varying the load would ask the model to fuel a five-hour ride
     * out of a rest day's energy — it would correctly refuse, and the test would be measuring the
     * fixture rather than the model. ~8 kcal per TSS point is a reasonable stand-in.
     */
    private fun expenditureFor(l: DayLoad): Double = restingAndNeat + l.tss * 8.0

    private fun target(
        goal: NutritionGoal = NutritionGoal.MAINTAIN,
        rate: Double = 0.0,
        today: DayLoad = load(60, 60),
        previous: DayLoad? = null,
        next: DayLoad? = null,
        ffmKg: Double? = ffm,
        expenditure: Double? = null
    ) = NutritionTargets.forDay(
        date = date,
        nonTefExpenditureKcal = expenditure ?: expenditureFor(today),
        goal = goal,
        ratePctPerWeek = rate,
        bodyMassKg = mass,
        ffmKg = ffmKg,
        today = today,
        previousDay = previous,
        nextDay = next
    )

    // ---- Day classification --------------------------------------------------------------------

    @Test
    fun `days are classified by load, with duration breaking ties`() {
        assertEquals(DayKind.REST, NutritionTargets.classify(load(0)))
        assertEquals(DayKind.REST, NutritionTargets.classify(load(20, 20)))
        assertEquals(DayKind.MODERATE, NutritionTargets.classify(load(50, 60)))
        assertEquals(DayKind.ENDURANCE, NutritionTargets.classify(load(120, 120)))
        assertEquals(DayKind.EXTREME, NutritionTargets.classify(load(250, 240)))
    }

    /** Three easy hours score modestly but still empty a lot of glycogen. */
    @Test
    fun `a long easy session is not treated as a rest day`() {
        assertEquals(DayKind.EXTREME, NutritionTargets.classify(load(tss = 80, minutes = 200)))
    }

    // ---- Energy --------------------------------------------------------------------------------

    @Test
    fun `goal rate converts to a daily energy balance against body mass`() {
        // 0.5% of 80 kg = 0.4 kg/week = 3,080 kcal/week = 440 kcal/day.
        val balance = NutritionTargets.dailyEnergyBalanceKcal(NutritionGoal.LOSE_FAT, -0.5, mass)
        assertEquals(-440.0, balance, 0.5)
    }

    @Test
    fun `a goal that does not move body mass yields no energy adjustment`() {
        assertEquals(0.0, NutritionTargets.dailyEnergyBalanceKcal(NutritionGoal.MAINTAIN, -1.0, mass), 0.0)
        assertEquals(0.0, NutritionTargets.dailyEnergyBalanceKcal(NutritionGoal.RECOMPOSITION, 0.5, mass), 0.0)
    }

    @Test
    fun `a rate pointing the wrong way for the goal is clamped out`() {
        // Asking to gain 0.5%/week while cutting must not produce a surplus.
        assertEquals(0.0, NutritionTargets.dailyEnergyBalanceKcal(NutritionGoal.LOSE_FAT, 0.5, mass), 0.0)
    }

    /**
     * The goal is the one number this must not renegotiate. Asserted across every day kind,
     * because it is the hard days where the macro maths is most tempted to give the deficit back.
     */
    @Test
    fun `the deficit delivered equals the deficit requested, on every kind of day`() {
        val requested = NutritionTargets.dailyEnergyBalanceKcal(NutritionGoal.LOSE_FAT, -0.5, mass)
        listOf(load(0), load(60, 60), load(140, 120), load(250, 240)).forEach { l ->
            val result = target(goal = NutritionGoal.LOSE_FAT, rate = -0.5, today = l)!!
            val tdee = MetabolicModel.predictedTdee(expenditureFor(l), result.kcal)!!
            assertEquals("on ${l.tss} TSS", requested, result.kcal - tdee, 1.0)
        }
    }

    @Test
    fun `an over-aggressive deficit is capped and says so`() {
        // 1%/week on 80 kg is ~1,257 kcal/day, well past 25% of a ~3,000 kcal expenditure.
        val rest = load(0)
        val result = target(goal = NutritionGoal.LOSE_FAT, rate = -1.0, today = rest)!!
        assertTrue(result.warnings.any { it.code == TargetWarning.Code.DEFICIT_CAPPED })

        // The cap must be exactly 25% of the expenditure that actually results, not of maintenance.
        val tdee = MetabolicModel.predictedTdee(expenditureFor(rest), result.kcal)!!
        assertEquals(-NutritionTargets.MAX_DEFICIT_FRACTION, (result.kcal - tdee) / tdee, 0.0001)
    }

    // ---- Protein -------------------------------------------------------------------------------

    @Test
    fun `protein rises in a deficit and stays inside the ceiling`() {
        val maintain = NutritionTargets.proteinTargetG(NutritionGoal.MAINTAIN, mass, ffm)!!
        val cutting = NutritionTargets.proteinTargetG(NutritionGoal.LOSE_FAT, mass, ffm)!!
        assertTrue(cutting > maintain)
        assertTrue(cutting <= mass * NutritionTargets.PROTEIN_MAX_G_PER_KG)
    }

    /**
     * The body-mass and fat-free-mass recommendations disagree most for the leanest athletes, and
     * over-prescribing protein displaces the carbohydrate that fuels the session.
     */
    @Test
    fun `the fat-free-mass ceiling binds for a very lean athlete`() {
        val leanFfm = 45.0
        val capped = NutritionTargets.proteinTargetG(NutritionGoal.LOSE_FAT, 100.0, leanFfm)!!
        assertTrue(capped <= leanFfm * NutritionTargets.PROTEIN_MAX_G_PER_KG_FFM + 0.001)
    }

    @Test
    fun `protein still works without a body composition scan`() {
        assertNotNull(NutritionTargets.proteinTargetG(NutritionGoal.MAINTAIN, mass, null))
        assertNull(NutritionTargets.proteinTargetG(NutritionGoal.MAINTAIN, null, ffm))
    }

    // ---- Carbohydrate periodisation ------------------------------------------------------------

    @Test
    fun `carbohydrate scales with the day's demand`() {
        val rest = target(today = load(0))!!
        val moderate = target(today = load(60, 60))!!
        val big = target(today = load(250, 240))!!
        assertTrue(rest.carbsG < moderate.carbsG)
        assertTrue(moderate.carbsG < big.carbsG)
    }

    @Test
    fun `carbohydrate lands inside the published band for the day kind`() {
        val moderate = target(today = load(60, 60))!!
        assertTrue(moderate.carbsG >= DayKind.MODERATE.carbGramsPerKg.start * mass - 0.001)
        assertTrue(moderate.carbsG <= DayKind.MODERATE.carbGramsPerKg.endInclusive * mass + 0.001)
    }

    @Test
    fun `intensity positions the target inside the band`() {
        val easy = target(today = load(60, 60, hardShare = 0.0))!!
        val hard = target(today = load(60, 60, hardShare = 1.0))!!
        assertTrue(hard.carbsG > easy.carbsG)
    }

    /** Fuel is built before the work, not only during it. */
    @Test
    fun `tomorrow's long session raises today's carbohydrate`() {
        val plain = target(today = load(0))!!
        val preloading = target(today = load(0), next = load(220, 240))!!
        assertTrue(preloading.carbsG > plain.carbsG)
        assertTrue(preloading.rationale.contains("preload"))
    }

    @Test
    fun `yesterday's long session raises today's carbohydrate`() {
        val plain = target(today = load(0))!!
        val refilling = target(today = load(0), previous = load(220, 240))!!
        assertTrue(refilling.carbsG > plain.carbsG)
        assertTrue(refilling.rationale.contains("refill"))
    }

    @Test
    fun `an easy day either side changes nothing`() {
        val plain = target(today = load(60, 60))!!
        val flanked = target(today = load(60, 60), previous = load(10), next = load(10))!!
        assertEquals(plain.carbsG, flanked.carbsG, 0.001)
    }

    // ---- Fat floor -----------------------------------------------------------------------------

    @Test
    fun `fat never falls below its hard floor, whatever the day demands`() {
        val floor = mass * NutritionTargets.FAT_FLOOR_G_PER_KG
        listOf(load(0), load(60, 60), load(140, 120), load(250, 240)).forEach { l ->
            listOf(NutritionGoal.MAINTAIN to 0.0, NutritionGoal.LOSE_FAT to -1.0).forEach { (g, r) ->
                val result = target(goal = g, rate = r, today = l)!!
                assertTrue("$g on ${l.tss} TSS: fat ${result.fatG}", result.fatG >= floor - 0.001)
            }
        }
    }

    @Test
    fun `fat sits at its preferred share when the day leaves room for it`() {
        val result = target(today = load(0))!!
        val preferred = result.kcal * NutritionTargets.FAT_PREFERRED_ENERGY_FRACTION /
            NutritionTargets.KCAL_PER_G_FAT
        assertTrue("fat ${result.fatG} vs preferred $preferred", result.fatG >= preferred - 0.001)
    }

    /**
     * The athlete asked to lose weight at a rate. A big session on that day is a reason to warn
     * about carbohydrate, not a licence to quietly hand back the deficit they asked for.
     */
    @Test
    fun `a big day inside a deficit trims carbohydrate and warns, it does not abandon the goal`() {
        val big = load(250, 240)
        val result = target(goal = NutritionGoal.LOSE_FAT, rate = -0.5, today = big)!!
        assertTrue(result.warnings.none { it.code == TargetWarning.Code.DEFICIT_EASED_FOR_FAT_FLOOR })

        val tdee = MetabolicModel.predictedTdee(expenditureFor(big), result.kcal)!!
        val requested = NutritionTargets.dailyEnergyBalanceKcal(NutritionGoal.LOSE_FAT, -0.5, mass)
        assertEquals(requested, result.kcal - tdee, 1.0)
    }

    /** The only case allowed to move the energy target: nothing is left to take from. */
    @Test
    fun `an impossible budget eases the target and says exactly why`() {
        // A tiny expenditure against an adult's protein and fat floor cannot be reconciled.
        val result = NutritionTargets.forDay(
            date = date,
            nonTefExpenditureKcal = 900.0,
            goal = NutritionGoal.LOSE_FAT,
            ratePctPerWeek = -1.0,
            bodyMassKg = mass,
            ffmKg = ffm,
            today = load(0),
            previousDay = null,
            nextDay = null
        )!!
        assertTrue(result.warnings.any { it.code == TargetWarning.Code.DEFICIT_EASED_FOR_FAT_FLOOR })
        assertTrue(result.fatG >= mass * NutritionTargets.FAT_FLOOR_G_PER_KG - 0.001)
        assertTrue(result.carbsG >= -0.001)
    }

    @Test
    fun `macros always add up to the stated energy`() {
        listOf(
            target(today = load(0)),
            target(today = load(60, 60)),
            target(goal = NutritionGoal.LOSE_FAT, rate = -0.5, today = load(120, 120)),
            target(goal = NutritionGoal.BUILD_MUSCLE, rate = 0.25, today = load(250, 240))
        ).forEach { t ->
            val fromMacros = t!!.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
            assertEquals(t.kcal, fromMacros, 1.0)
        }
    }

    // ---- Degradation ---------------------------------------------------------------------------

    @Test
    fun `no expenditure or no body mass means no target rather than a guess`() {
        assertNull(
            NutritionTargets.forDay(date, null, NutritionGoal.MAINTAIN, 0.0, mass, ffm, load(60), null, null)
        )
        assertNull(
            NutritionTargets.forDay(date, nonTef, NutritionGoal.MAINTAIN, 0.0, null, ffm, load(60), null, null)
        )
    }

    // ---- Weekly reconciliation -------------------------------------------------------------------

    private val trainingWeek = listOf(
        load(0), load(60, 60), load(140, 120), load(0),
        load(90, 90), load(240, 230), load(30, 30)
    )

    private fun week(loads: List<DayLoad>) = loads.mapIndexed { i, l ->
        val d = date.plusDays(i.toLong())
        DayTargetInput(d, expenditureFor(l), l.copy(date = d), bodyMassKg = mass, ffmKg = ffm)
    }

    private fun weeklyBudget(loads: List<DayLoad>, goal: NutritionGoal, rate: Double): Double =
        loads.sumOf { l ->
            MetabolicModel.targetIntake(
                expenditureFor(l),
                NutritionTargets.dailyEnergyBalanceKcal(goal, rate, mass)
            )!!
        }

    @Test
    fun `a periodised week still sums to the week's energy budget`() {
        val targets = NutritionTargets.forWeek(week(trainingWeek), NutritionGoal.MAINTAIN, 0.0)
        assertEquals(7, targets.size)
        assertEquals(
            weeklyBudget(trainingWeek, NutritionGoal.MAINTAIN, 0.0),
            targets.sumOf { it.kcal },
            5.0
        )
    }

    @Test
    fun `reconciliation keeps the shape of the week, hard days still eat more`() {
        val targets = NutritionTargets.forWeek(week(trainingWeek), NutritionGoal.MAINTAIN, 0.0)
        val restDay = targets[0]
        val bigDay = targets[5]
        assertTrue("rest ${restDay.kcal} vs big ${bigDay.kcal}", bigDay.kcal > restDay.kcal)
    }

    @Test
    fun `a deficit week sums below maintenance by the requested amount`() {
        val targets = NutritionTargets.forWeek(week(trainingWeek), NutritionGoal.LOSE_FAT, -0.5)
        val maintenance = weeklyBudget(trainingWeek, NutritionGoal.MAINTAIN, 0.0)
        val expected = weeklyBudget(trainingWeek, NutritionGoal.LOSE_FAT, -0.5)
        val actual = targets.sumOf { it.kcal }
        assertTrue(actual < maintenance)
        assertEquals(expected, actual, 5.0)
    }

    @Test
    fun `an empty week produces no targets rather than throwing`() {
        assertTrue(NutritionTargets.forWeek(emptyList(), NutritionGoal.MAINTAIN, 0.0).isEmpty())
    }
}
