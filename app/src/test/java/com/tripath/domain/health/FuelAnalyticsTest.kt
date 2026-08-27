package com.tripath.domain.health

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.BiologicalSex
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The join between planned training and what to eat.
 *
 * These are the tests for the gap this whole change closes: the carbohydrate model has always known
 * that a big day is fuelled starting the day before, and nothing ever told it what tomorrow held.
 */
class FuelAnalyticsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    /** A Wednesday, so "end of this week" is a real distance away rather than today. */
    private val today: LocalDate = LocalDate.of(2026, 8, 26)

    private val profile = UserProfile(
        biologicalSex = BiologicalSex.MALE,
        birthDate = LocalDate.of(1996, 8, 20),
        heightCm = 180
    )

    private fun weighIn(daysAgo: Long, weightKg: Double) = BodyCompositionLog(
        id = "scan-$daysAgo",
        timestamp = today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli(),
        weightKg = weightKg,
        bodyFatPercent = null,
        boneMassKg = null,
        leanMassKg = null,
        importedAt = 0L
    )

    private fun ride(date: LocalDate, tss: Int, minutes: Int) = WorkoutLog(
        connectId = "w-$date",
        date = date,
        type = WorkoutType.BIKE,
        durationMinutes = minutes,
        computedTSS = tss
    )

    private fun build(
        plannedTss: Map<LocalDate, Int> = emptyMap(),
        plannedMinutes: Map<LocalDate, Int> = emptyMap(),
        workouts: List<WorkoutLog> = emptyList(),
        intakeByDate: Map<LocalDate, Double> = emptyMap(),
        weighIns: List<BodyCompositionLog> = listOf(weighIn(30, 80.0)),
        windowDays: Long = 30
    ) = FuelAnalytics.build(
        workouts = workouts,
        nutritionByDate = intakeByDate.mapValues { (_, kcal) -> kcal to null },
        bodyComposition = weighIns,
        dailyActivity = emptyList(),
        profile = profile,
        plannedTssByDate = plannedTss,
        windowStart = today.minusDays(windowDays),
        today = today,
        weightByDate = weighIns.associate {
            java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() to it.weightKg!!
        },
        plannedMinutesByDate = plannedMinutes
    )

    // ---- The horizon ---------------------------------------------------------------------------

    @Test
    fun `the window always reaches at least tomorrow`() {
        val horizon = FuelAnalytics.horizonEnd(today, Locale.UK)
        assertTrue(horizon >= today.plusDays(1))
    }

    @Test
    fun `the window reaches the end of the week so a whole week can be reconciled`() {
        // Wednesday under a Monday-start locale: the week still has Sunday in it.
        val horizon = FuelAnalytics.horizonEnd(today, Locale.UK)
        assertEquals(DayOfWeek.SUNDAY, horizon.dayOfWeek)
    }

    @Test
    fun `on the last day of the week the horizon is simply tomorrow`() {
        val sunday = today.plusDays(4)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(sunday.plusDays(1), FuelAnalytics.horizonEnd(sunday, Locale.UK))
    }

    // ---- Fuelling for what is coming ------------------------------------------------------------

    /** The point of the whole exercise: a big day tomorrow is fuelled starting today. */
    @Test
    fun `a big session planned for tomorrow raises today's carbohydrate target`() {
        val withoutPlan = build().today?.target
        val withPlan = build(
            plannedTss = mapOf(today.plusDays(1) to 240),
            plannedMinutes = mapOf(today.plusDays(1) to 240)
        ).today?.target

        assertNotNull(withoutPlan)
        assertNotNull(withPlan)
        assertTrue(
            "today's carbs ${withPlan!!.carbsG} should exceed ${withoutPlan!!.carbsG}",
            withPlan.carbsG > withoutPlan.carbsG
        )
        assertTrue(withPlan.rationale.contains("preload"))
    }

    /**
     * Today's session counts before it has been logged. At 07:00 nothing has happened yet, and a
     * target sized from an empty day would under-fuel the session it exists to fuel.
     */
    @Test
    fun `today's own planned session counts before anything is logged`() {
        val restDay = build().today?.target
        val plannedBigDay = build(
            plannedTss = mapOf(today to 220),
            plannedMinutes = mapOf(today to 220)
        ).today?.target

        assertNotNull(restDay)
        assertEquals(DayKind.REST, restDay!!.dayKind)
        assertEquals(DayKind.EXTREME, plannedBigDay!!.dayKind)
        assertTrue(plannedBigDay.carbsG > restDay.carbsG)
    }

    /**
     * A session that was planned last Monday and skipped cost nothing, and must not be retro-fitted
     * into what that day needed — the history chart is read as a record of what happened.
     */
    @Test
    fun `a planned session in the past is ignored`() {
        val lastWeek = today.minusDays(7)
        val plain = build().days.first { it.date == lastWeek }.target
        val withStalePlan = build(plannedTss = mapOf(lastWeek to 260))
            .days.first { it.date == lastWeek }.target

        assertNotNull(plain)
        assertEquals(plain!!.carbsG, withStalePlan!!.carbsG, 0.001)
        assertEquals(DayKind.REST, withStalePlan.dayKind)
    }

    @Test
    fun `duration breaks a tie that TSS alone cannot`() {
        // Same modest TSS, but three hours of it. The long day needs the bigger carbohydrate band.
        val short = build(plannedTss = mapOf(today to 80), plannedMinutes = mapOf(today to 60))
        val long = build(plannedTss = mapOf(today to 80), plannedMinutes = mapOf(today to 200))

        assertEquals(DayKind.MODERATE, short.today?.target?.dayKind)
        assertEquals(DayKind.EXTREME, long.today?.target?.dayKind)
    }

    // ---- Forecast days stay out of observed history ---------------------------------------------

    @Test
    fun `days stops at today and the forecast carries the rest`() {
        val analysis = build(plannedTss = mapOf(today.plusDays(1) to 200))

        assertEquals(today, analysis.days.last().date)
        assertEquals(today, analysis.today?.date)
        assertTrue(analysis.forecast.all { it.date.isAfter(today) })
        assertEquals(today.plusDays(1), analysis.tomorrow?.date)
    }

    /**
     * Rolling energy availability takes the last seven *observed* days. If forecast rows joined that
     * list they would push real days out of the window and quietly turn a known figure unknown.
     */
    @Test
    fun `a forecast never displaces observed days in the rolling energy availability window`() {
        val intake = (0L..6L).associate { today.minusDays(it) to 2600.0 }
        val scans = listOf(
            weighIn(30, 80.0).copy(leanMassKg = 65.0),
            weighIn(0, 80.0).copy(leanMassKg = 65.0)
        )

        val withoutPlan = build(intakeByDate = intake, weighIns = scans).rollingEnergyAvailability
        val withPlan = build(
            plannedTss = mapOf(today.plusDays(1) to 200),
            intakeByDate = intake,
            weighIns = scans
        ).rollingEnergyAvailability

        assertNotNull(withoutPlan.kcalPerKgFfm)
        assertEquals(withoutPlan.kcalPerKgFfm!!, withPlan.kcalPerKgFfm!!, 0.001)
        assertEquals(withoutPlan.daysCounted, withPlan.daysCounted)
    }

    @Test
    fun `a forecast day reports no intake and no energy balance`() {
        val tomorrow = build(plannedTss = mapOf(today.plusDays(1) to 200)).tomorrow

        assertNotNull(tomorrow)
        assertNull(tomorrow!!.intakeKcal)
        assertNull(tomorrow.balanceKcal)
        // It does predict an expenditure, from the assumption that the target gets eaten.
        assertNotNull(tomorrow.tdeeKcal)
    }

    // ---- Planned work costs energy --------------------------------------------------------------

    /**
     * The bug this pair of tests exists for: the carbohydrate band looked forward while the energy
     * budget looked back, so a planned session raised the carb *demand* and not the *budget* to pay
     * for it — and the target was then clamped back down with a "carbs below band" warning on
     * exactly the days that needed carbs most.
     */
    @Test
    fun `a planned session raises the day's energy target, not only its carbohydrate band`() {
        val rest = build().today?.target
        val planned = build(
            plannedTss = mapOf(today to 200),
            plannedMinutes = mapOf(today to 180)
        ).today?.target

        assertNotNull(rest)
        assertNotNull(planned)
        assertTrue(
            "planned ${planned!!.kcal} should exceed rest ${rest!!.kcal}",
            planned.kcal > rest.kcal + 500
        )
    }

    @Test
    fun `a completed session is not paid for twice`() {
        val done = ride(today, tss = 200, minutes = 180)
        val actualOnly = build(workouts = listOf(done))
        val actualAndPlan = build(
            workouts = listOf(done),
            plannedTss = mapOf(today to 200),
            plannedMinutes = mapOf(today to 180)
        )

        assertEquals(0.0, actualAndPlan.today!!.plannedExerciseKcal, 0.001)
        assertEquals(
            actualOnly.today!!.target!!.kcal,
            actualAndPlan.today!!.target!!.kcal,
            0.001
        )
    }

    /** Half-done: the logged part is priced from what it cost, the rest from the estimate. */
    @Test
    fun `only the unlogged part of a plan is estimated`() {
        val analysis = build(
            workouts = listOf(ride(today, tss = 80, minutes = 60)),
            plannedTss = mapOf(today to 200)
        )
        val planned = analysis.today!!.plannedExerciseKcal

        assertEquals(120 * FuelAnalytics.DEFAULT_KCAL_PER_TSS, planned, 0.001)
    }

    @Test
    fun `a past day is never charged for a session that did not happen`() {
        val lastWeek = today.minusDays(7)
        val day = build(plannedTss = mapOf(lastWeek to 260)).days.first { it.date == lastWeek }
        assertEquals(0.0, day.plannedExerciseKcal, 0.001)
    }

    /**
     * The same TSS costs different athletes different amounts, so the price comes from their own
     * sessions once there are enough of them to measure.
     */
    @Test
    fun `the per-TSS price is measured from the athlete's own sessions`() {
        // Six sessions, all with an explicit calorie figure, at a deliberately un-default rate.
        val logged = (1L..6L).map { daysAgo ->
            ride(today.minusDays(daysAgo), tss = 100, minutes = 60).copy(calories = 1200)
        }
        val rate = FuelAnalytics.personalKcalPerTss(logged, weightKg = 80.0)

        assertNotNull(rate)
        assertEquals(12.0, rate!!, 0.001)
    }

    @Test
    fun `too little history falls back to the default rate rather than guessing`() {
        val thin = listOf(ride(today.minusDays(1), tss = 100, minutes = 60).copy(calories = 1200))
        assertNull(FuelAnalytics.personalKcalPerTss(thin, weightKg = 80.0))
    }

    // ---- Per-day body mass ----------------------------------------------------------------------

    /**
     * A target for a past day belongs to the athlete who lived it. Sizing the whole window from the
     * latest weigh-in would let this morning's scale rewrite what last month needed.
     */
    @Test
    fun `a past day's target uses that day's weight, not today's`() {
        val scans = listOf(weighIn(30, 70.0), weighIn(2, 90.0))
        val analysis = build(weighIns = scans)

        val early = analysis.days.first { it.date == today.minusDays(20) }.target
        val recent = analysis.today?.target

        assertNotNull(early)
        assertNotNull(recent)
        // Protein is a flat multiple of body mass, so it reads the weight used straight back out.
        assertEquals(70.0 * NutritionTargets.PROTEIN_BASE_G_PER_KG, early!!.proteinG, 0.001)
        assertEquals(90.0 * NutritionTargets.PROTEIN_BASE_G_PER_KG, recent!!.proteinG, 0.001)
    }

    // ---- Ignored and logged sessions ------------------------------------------------------------

    @Test
    fun `a logged session on a day with no plan still raises that day's needs`() {
        val logged = build(workouts = listOf(ride(today, tss = 210, minutes = 190))).today?.target
        val rest = build().today?.target

        assertEquals(DayKind.EXTREME, logged?.dayKind)
        assertTrue(logged!!.carbsG > rest!!.carbsG)
    }

    /** Planned and actual do not stack: the larger wins, so a completed session is not double-counted. */
    @Test
    fun `a completed session and its own plan are not added together`() {
        val actualOnly = build(workouts = listOf(ride(today, tss = 200, minutes = 180)))
        val actualAndPlan = build(
            workouts = listOf(ride(today, tss = 200, minutes = 180)),
            plannedTss = mapOf(today to 200),
            plannedMinutes = mapOf(today to 180)
        )

        assertEquals(
            actualOnly.today!!.target!!.carbsG,
            actualAndPlan.today!!.target!!.carbsG,
            0.001
        )
    }
}
