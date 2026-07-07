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
        runningGoal: RunningGoal? = null,
        // The first day any session may be placed on (typically today). Running progression weeks stay
        // aligned to [startDate] for counting/goal-date math, but both strength and a partial run
        // lead-in week can begin here so training isn't delayed to the plan's week start.
        earliestSessionDate: LocalDate = startDate
    ): GenerationResult {
        val smartEnabled = preferencesManager.autoPlannerEnabledFlow.first()
        if (!smartEnabled) {
            Log.e(tag, "Smart Planning is DISABLED in settings. Aborting.")
            return GenerationResult.Failure(
                reason = "Smart Planning is disabled",
                details = "Please enable Smart Planning in Auto-planner Settings to generate training plans."
            )
        }

        val strengthEnabled = preferencesManager.autoPlanStrengthEnabledFlow.first()
        val considersStrength = preferencesManager.runningConsidersStrengthFlow.first()

        val effectiveRunningGoal = runningGoal ?: preferencesManager.getActiveRunningGoal()
        if (effectiveRunningGoal == null && !strengthEnabled) {
            return GenerationResult.Failure(
                reason = "Running goal required",
                details = "Auto-planner is run-focused. Create a running goal, or enable Add Strength Training, in Auto-planner Settings before generating a plan."
            )
        }

        val weeks = (months * 4).coerceAtLeast(1)

        // Strength sessions run on a fixed every-3rd-day cadence, independent of the running goal.
        val strengthPlans = if (strengthEnabled) {
            StrengthPlanGenerator.generateStrengthPlans(
                firstWorkoutDate = preferencesManager.getStrengthFirstWorkoutDate(),
                planStartDate = startDate,
                weeks = weeks,
                earliestSessionDate = earliestSessionDate
            )
        } else {
            emptyList()
        }

        // The run planner derives progression entirely from the saved running goal.
        val runningGoalPlans = if (effectiveRunningGoal != null) {
            val progression = RunningProgressionRules.generateWeeklyTargets(
                goal = effectiveRunningGoal,
                planStartDate = startDate,
                openEndedWeeks = weeks
            )

            when {
                progression.warnings.contains(RunningProgressionWarning.TARGET_DATE_TOO_SOON) ->
                    return GenerationResult.Failure(
                        reason = "TARGET_DATE_TOO_SOON",
                        details = "Complete-distance running goals need a target date at least 2 weeks after the generated plan start."
                    )

                progression.warnings.contains(RunningProgressionWarning.TARGET_DATE_TOO_FAR) ->
                    return GenerationResult.Failure(
                        reason = "TARGET_DATE_TOO_FAR",
                        details = "Complete-distance running goals can be planned up to 52 weeks ahead. Choose a closer target date."
                    )

                progression.warnings.contains(RunningProgressionWarning.LONG_GOAL_HORIZON) ->
                    Log.w(tag, "LONG_GOAL_HORIZON: Running goal extends beyond the normal 24-week planning window.")
            }

            RunGoalTrainingPlanMapper.mapToTrainingPlans(
                goal = effectiveRunningGoal,
                progressionResult = progression,
                preferredRunningDays = effectiveRunningGoal.preferredDays,
                planStartDate = startDate,
                strengthDates = if (considersStrength) strengthPlans.map { it.date } else emptyList(),
                earliestRunDate = earliestSessionDate
            )
        } else {
            emptyList()
        }

        val allPlans = runningGoalPlans + strengthPlans
        Log.i(tag, "GENERATION COMPLETE. Runs=${runningGoalPlans.size}, Strength=${strengthPlans.size}.")
        return if (allPlans.isEmpty()) {
            GenerationResult.Failure(
                reason = "No plans were generated",
                details = "Generation completed but produced no plans. Check the running goal inputs, preferred days, and strength settings."
            )
        } else {
            GenerationResult.Success(allPlans)
        }
    }
}
