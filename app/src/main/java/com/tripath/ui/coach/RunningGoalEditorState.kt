package com.tripath.ui.coach

import com.tripath.domain.running.ProgressionSafety
import com.tripath.domain.running.RunningGoal
import com.tripath.domain.running.RunningGoalType
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

data class RunningGoalEditorState(
    val goalType: RunningGoalType = RunningGoalType.ENDURANCE,
    val targetDistanceKm: String = "",
    val targetDate: LocalDate? = null,
    val runsPerWeek: String = "",
    val preferredDays: Set<DayOfWeek> = emptySet(),
    val baselineLongestRunKm: String = "",
    val baselineWeeklyRunKm: String = "",
    val progressionSafety: ProgressionSafety = ProgressionSafety.STANDARD
) {
    fun isValid(): Boolean {
        val runsPerWeekValue = parsePositiveInt(runsPerWeek)
        if (runsPerWeek.isNotBlank() && (runsPerWeekValue == null || runsPerWeekValue !in 1..7)) return false

        val distanceMeters = parseKilometersToMeters(targetDistanceKm)
        return when (goalType) {
            RunningGoalType.COMPLETE_DISTANCE -> distanceMeters != null && targetDate != null
            RunningGoalType.CONSISTENCY, RunningGoalType.ENDURANCE -> true
        }
    }

    fun toRunningGoalOrNull(): RunningGoal? {
        if (!isValid()) return null

        return RunningGoal(
            type = goalType,
            targetDistanceMeters = parseKilometersToMeters(targetDistanceKm),
            targetDate = targetDate,
            runsPerWeek = parsePositiveInt(runsPerWeek),
            preferredDays = preferredDays.toList().sortedBy { it.value },
            baselineLongestRunMeters = parseKilometersToMeters(baselineLongestRunKm),
            baselineWeeklyRunMeters = parseKilometersToMeters(baselineWeeklyRunKm),
            maxWeeklyProgressPercent = progressionSafety.maxWeeklyProgressPercent
        )
    }

    companion object {
        fun fromGoal(goal: RunningGoal?): RunningGoalEditorState {
            if (goal == null) return RunningGoalEditorState()

            return RunningGoalEditorState(
                goalType = goal.type,
                targetDistanceKm = metersToKmString(goal.targetDistanceMeters),
                targetDate = goal.targetDate,
                runsPerWeek = goal.runsPerWeek?.toString().orEmpty(),
                preferredDays = goal.preferredDays?.toSet().orEmpty(),
                baselineLongestRunKm = metersToKmString(goal.baselineLongestRunMeters),
                baselineWeeklyRunKm = metersToKmString(goal.baselineWeeklyRunMeters),
                progressionSafety = goal.maxWeeklyProgressPercent?.let { pct ->
                    ProgressionSafety.entries.minByOrNull { kotlin.math.abs(it.maxWeeklyProgressPercent - pct) }
                } ?: ProgressionSafety.STANDARD
            )
        }

        private fun metersToKmString(value: Int?): String {
            if (value == null) return ""
            val km = value / 1000.0
            return if (km % 1.0 == 0.0) km.roundToInt().toString() else km.toString()
        }
    }
}

private fun parsePositiveInt(value: String): Int? {
    return value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
}

private fun parseKilometersToMeters(value: String): Int? {
    val km = value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return null
    if (km <= 0) return null
    return (km * 1000).roundToInt()
}