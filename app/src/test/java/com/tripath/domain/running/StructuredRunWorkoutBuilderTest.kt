package com.tripath.domain.running

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class StructuredRunWorkoutBuilderTest {
    @Test
    fun `tempo workout has warm up tempo and cool down`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.TEMPO,
            plannedDistanceMeters = 8_000
        )

        assertEquals(listOf(
            RunWorkoutStepType.WARM_UP,
            RunWorkoutStepType.TEMPO,
            RunWorkoutStepType.COOL_DOWN
        ), workout.steps.map { it.type })
        assertTrue(workout.summaryText.contains("tempo"))
    }

    @Test
    fun `interval workout has repeated hard intervals and recoveries`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.INTERVALS,
            plannedDistanceMeters = 7_000
        )

        val intervalCount = workout.steps.count { it.type == RunWorkoutStepType.INTERVAL }
        val recoveryCount = workout.steps.count { it.type == RunWorkoutStepType.RECOVERY_JOG }

        assertTrue(intervalCount in 4..6)
        assertEquals(intervalCount - 1, recoveryCount)
        assertTrue(workout.summaryText.contains("hard"))
    }

    @Test
    fun `progression workout has easy steady and hard progression`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.PROGRESSION,
            plannedDistanceMeters = 9_000
        )

        assertEquals(listOf(
            RunWorkoutStepType.EASY,
            RunWorkoutStepType.STEADY,
            RunWorkoutStepType.COMFORTABLY_HARD
        ), workout.steps.map { it.type })
        assertTrue(workout.summaryText.contains("Start easy"))
        assertTrue(workout.summaryText.contains("finish comfortably hard"))
    }

    @Test
    fun `race pace workout has race pace block`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.RACE_PACE,
            plannedDistanceMeters = 8_000,
            targetDistanceMeters = 10_000
        )

        assertTrue(workout.steps.any { it.type == RunWorkoutStepType.RACE_PACE })
        assertTrue(workout.summaryText.contains("race pace"))
    }

    @Test
    fun `baseline pace adds pace targets to summary`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.INTERVALS,
            plannedDistanceMeters = 7_000,
            baselinePaceSecPerKm = 330
        )

        assertTrue(workout.summaryText.contains("/km"))
        assertTrue(workout.steps.any { it.description.contains("/km") })
    }

    @Test
    fun `missing baseline pace falls back to effort targets`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.TEMPO,
            plannedDistanceMeters = 8_000,
            baselinePaceSecPerKm = null
        )

        assertTrue(workout.summaryText.contains("RPE"))
        assertTrue(workout.steps.any { it.description.contains("RPE") })
    }

    @Test
    fun `final week tempo pace is slightly faster than week one`() {
        val weekOne = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.TEMPO,
            plannedDistanceMeters = 8_000,
            baselinePaceSecPerKm = 330,
            weekIndex = 1,
            totalWeeks = 12
        )
        val finalWeek = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.TEMPO,
            plannedDistanceMeters = 8_000,
            baselinePaceSecPerKm = 330,
            weekIndex = 12,
            totalWeeks = 12
        )

        val weekOneTempo = weekOne.steps.single { it.type == RunWorkoutStepType.TEMPO }
        val finalWeekTempo = finalWeek.steps.single { it.type == RunWorkoutStepType.TEMPO }

        assertEquals(315, weekOneTempo.targetLow)
        assertTrue((finalWeekTempo.targetLow ?: Int.MAX_VALUE) < (weekOneTempo.targetLow ?: Int.MIN_VALUE))
        assertTrue((finalWeekTempo.targetHigh ?: Int.MAX_VALUE) < (weekOneTempo.targetHigh ?: Int.MIN_VALUE))
    }

    @Test
    fun `easy pace improves less than tempo pace`() {
        val easyWorkout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.EASY,
            plannedDistanceMeters = 6_000,
            baselinePaceSecPerKm = 330,
            weekIndex = 12,
            totalWeeks = 12
        )
        val tempoWorkout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.TEMPO,
            plannedDistanceMeters = 8_000,
            baselinePaceSecPerKm = 330,
            weekIndex = 12,
            totalWeeks = 12
        )

        val easyStep = easyWorkout.steps.single()
        val tempoStep = tempoWorkout.steps.single { it.type == RunWorkoutStepType.TEMPO }
        val easyImprovement = 350 - (easyStep.targetLow ?: 350)
        val tempoImprovement = 315 - (tempoStep.targetLow ?: 315)

        assertTrue(easyImprovement in 0..7)
        assertTrue(tempoImprovement > easyImprovement)
    }

    @Test
    fun `missing baseline pace still uses effort text late in plan`() {
        val workout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.INTERVALS,
            plannedDistanceMeters = 7_000,
            baselinePaceSecPerKm = null,
            weekIndex = 20,
            totalWeeks = 24
        )

        assertFalse(workout.summaryText.contains("/km"))
        assertTrue(workout.summaryText.contains("RPE"))
    }

    @Test
    fun `easy and long run stay simple`() {
        val easyWorkout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.EASY,
            plannedDistanceMeters = 6_000
        )
        val longRunWorkout = StructuredRunWorkoutBuilder.build(
            sessionType = RunningSessionType.LONG_RUN,
            plannedDistanceMeters = 16_000
        )

        assertEquals(1, easyWorkout.steps.size)
        assertEquals(RunWorkoutStepType.EASY, easyWorkout.steps.single().type)
        assertEquals(1, longRunWorkout.steps.size)
        assertEquals(RunWorkoutStepType.LONG_AEROBIC, longRunWorkout.steps.single().type)
    }
}