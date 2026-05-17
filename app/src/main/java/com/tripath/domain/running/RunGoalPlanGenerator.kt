package com.tripath.domain.running

import com.tripath.data.local.database.entities.TrainingPlan
import java.time.LocalDate

object RunGoalPlanGenerator {
    fun generatePlans(
        runningGoal: RunningGoal?,
        startDate: LocalDate,
        months: Int = 3
    ): List<TrainingPlan>? {
        if (runningGoal == null) return null

        val progression = RunningProgressionRules.generateWeeklyTargets(
            goal = runningGoal,
            planStartDate = startDate,
            openEndedWeeks = (months * 4).coerceAtLeast(1)
        )

        return RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = runningGoal,
            progressionResult = progression,
            preferredRunningDays = runningGoal.preferredDays,
            planStartDate = startDate
        ).sortedBy { it.date }
    }
}