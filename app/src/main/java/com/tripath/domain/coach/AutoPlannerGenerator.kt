package com.tripath.domain.coach

import android.util.Log
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.domain.running.RunningGoal
import com.tripath.domain.running.RunGoalTrainingPlanMapper
import com.tripath.domain.running.RunningProgressionRules
import com.tripath.domain.running.RunningProgressionWarning
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Run-only Auto-Planner implementation.
 */
@Singleton
class AutoPlannerGenerator @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val tag = "AutoPlanner"

    sealed class GenerationResult {
        data class Success(val plans: List<TrainingPlan>) : GenerationResult()
        data class Failure(val reason: String, val details: String? = null) : GenerationResult()
    }

    suspend fun generateSeason(
        startDate: LocalDate,
        currentCtl: Double,
        months: Int = 3,
        recentRealLogs: List<WorkoutLog> = emptyList(),
        runningGoal: RunningGoal? = null
    ): GenerationResult {
        val smartEnabled = preferencesManager.autoPlannerEnabledFlow.first()
        if (!smartEnabled) {
            Log.e(tag, "Smart Planning is DISABLED in settings. Aborting.")
            return GenerationResult.Failure(
                reason = "Smart Planning is disabled",
                details = "Please enable Smart Planning in Auto-planner Settings to generate training plans."
            )
        }

        val effectiveRunningGoal = runningGoal ?: preferencesManager.getActiveRunningGoal()
            ?: return GenerationResult.Failure(
                reason = "Running goal required",
                details = "Auto-planner is now run-focused. Create a running goal in Auto-planner Settings before generating a plan."
            )

        // The run-only planner derives progression entirely from the saved running goal.
        val progression = RunningProgressionRules.generateWeeklyTargets(
            goal = effectiveRunningGoal,
            planStartDate = startDate,
            openEndedWeeks = (months * 4).coerceAtLeast(1)
        )

        when {
            progression.warnings.contains(RunningProgressionWarning.TARGET_DATE_TOO_SOON) -> {
                return GenerationResult.Failure(
                    reason = "TARGET_DATE_TOO_SOON",
                    details = "Complete-distance running goals need a target date at least 2 weeks after the generated plan start."
                )
            }

            progression.warnings.contains(RunningProgressionWarning.TARGET_DATE_TOO_FAR) -> {
                return GenerationResult.Failure(
                    reason = "TARGET_DATE_TOO_FAR",
                    details = "Complete-distance running goals can be planned up to 52 weeks ahead. Choose a closer target date."
                )
            }

            progression.warnings.contains(RunningProgressionWarning.LONG_GOAL_HORIZON) -> {
                Log.w(tag, "LONG_GOAL_HORIZON: Running goal extends beyond the normal 24-week planning window.")
            }
        }

        val runningGoalPlans = RunGoalTrainingPlanMapper.mapToTrainingPlans(
            goal = effectiveRunningGoal,
            progressionResult = progression,
            preferredRunningDays = effectiveRunningGoal.preferredDays,
            planStartDate = startDate
        )

        Log.i(tag, "RUNNING-GOAL GENERATION COMPLETE. Created ${runningGoalPlans.size} workouts.")
        return if (runningGoalPlans.isEmpty()) {
            GenerationResult.Failure(
                reason = "No running-goal plans were generated",
                details = "The running-goal path completed but produced no plans. Check the running goal inputs and preferred days."
            )
        } else {
            GenerationResult.Success(runningGoalPlans)
        }
    }
}
