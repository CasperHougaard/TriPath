package com.tripath.domain.running

import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RunGoalTrainingPlanMapperTest {
    @Test
    fun `3 runs per week with no preferred days uses tuesday thursday sunday`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.COMPLETE_DISTANCE),
            progressionResult = progressionResult(listOf(4000, 5000, 8000)),
            preferredRunningDays = emptyList(),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        assertEquals(3, result.size)
        assertTrue(result.all { it.type == WorkoutType.RUN })
        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            result.map { it.date.dayOfWeek }
        )
        assertNotEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            result.map { it.date.dayOfWeek }
        )
    }

    @Test
    fun `3 runs per week with preferred tuesday thursday sunday uses those days`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.COMPLETE_DISTANCE),
            progressionResult = progressionResult(listOf(4000, 5000, 8000)),
            preferredRunningDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            result.map { it.date.dayOfWeek }
        )
    }

    @Test
    fun `clustered preferred days rebalance to safer spacing`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.CONSISTENCY),
            progressionResult = progressionResult(listOf(3000, 3000, 3000)),
            preferredRunningDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
            result.map { it.date.dayOfWeek }
        )
        assertFalse(containsConsecutiveDays(result.map { it.date.dayOfWeek }))
    }

    @Test
    fun `2 runs per week prefers tuesday and sunday without consecutive days`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.ENDURANCE),
            progressionResult = progressionResult(listOf(4000, 8000)),
            preferredRunningDays = emptyList(),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
            result.map { it.date.dayOfWeek }
        )
        assertFalse(containsConsecutiveDays(result.map { it.date.dayOfWeek }))
    }

    @Test
    fun `4 runs per week uses monday wednesday friday sunday`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.CONSISTENCY),
            progressionResult = progressionResult(listOf(3000, 4000, 5000, 7000)),
            preferredRunningDays = emptyList(),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
            result.map { it.date.dayOfWeek }
        )
        assertFalse(containsConsecutiveDays(result.map { it.date.dayOfWeek }))
    }

    @Test
    fun `long run goes to sunday when sunday is scheduled`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.COMPLETE_DISTANCE),
            progressionResult = progressionResult(
                sessionDistancesMeters = listOf(4000, 5000, 10000),
                sessionTypes = listOf(RunningSessionType.EASY, RunningSessionType.TEMPO, RunningSessionType.LONG_RUN)
            ),
            preferredRunningDays = emptyList(),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        val longRun = result.single { it.subType == "Long Run" }
        assertEquals(LocalDate.of(2026, 5, 24), longRun.date)
        assertEquals(10000, longRun.plannedDistanceMeters)
        assertTrue(result.any { it.subType == "Tempo Run" })
    }

    @Test
    fun `long run goes to saturday when sunday is unavailable`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.COMPLETE_DISTANCE),
            progressionResult = progressionResult(listOf(4000, 5000, 10000)),
            preferredRunningDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        val longRun = result.single { it.subType == "Long Run" }
        assertEquals(LocalDate.of(2026, 5, 23), longRun.date)
        assertEquals(10000, longRun.plannedDistanceMeters)
    }

    @Test
    fun `long run goes to latest selected day when no weekend day is scheduled`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.COMPLETE_DISTANCE),
            progressionResult = progressionResult(listOf(4000, 5000, 10000)),
            preferredRunningDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        val longRun = result.single { it.subType == "Long Run" }
        assertEquals(LocalDate.of(2026, 5, 22), longRun.date)
        assertEquals(10000, longRun.plannedDistanceMeters)
    }

    @Test
    fun `generated week never contains duplicate dates`() {
        val result = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = RunningGoal(type = RunningGoalType.ENDURANCE),
            progressionResult = progressionResult(listOf(3000, 4000, 5000, 6000, 7000)),
            preferredRunningDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            planStartDate = LocalDate.of(2026, 5, 18)
        )

        assertEquals(result.size, result.map { it.date }.distinct().size)
    }

    @Test
    fun `existing non-running planner behavior is untouched when goal is absent`() {
        val result = RunGoalPlanGenerator.generatePlans(
            runningGoal = null,
            startDate = LocalDate.of(2026, 5, 18),
            months = 3
        )

        assertEquals(null, result)
    }

    private fun progressionResult(
        sessionDistancesMeters: List<Int>,
        sessionTypes: List<RunningSessionType> = emptyList()
    ): RunningProgressionResult {
        return RunningProgressionResult(
            weeklyTargets = listOf(
                RunningWeekTarget(
                    weekIndex = 1,
                    weekStartDate = LocalDate.of(2026, 5, 18),
                    isRecoveryWeek = false,
                    runsPerWeek = sessionDistancesMeters.size,
                    sessionDistancesMeters = sessionDistancesMeters,
                    sessionTypes = sessionTypes
                )
            )
        )
    }

    private fun containsConsecutiveDays(days: List<DayOfWeek>): Boolean {
        if (days.size <= 1) return false

        val orderedWeekDays = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )
        val indexes = days.map { orderedWeekDays.indexOf(it) }.sorted()

        return indexes.zipWithNext().any { (current, next) -> next - current == 1 }
    }
}