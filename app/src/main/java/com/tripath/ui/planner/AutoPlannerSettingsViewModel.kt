package com.tripath.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.domain.running.RunningGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class AutoPlannerSettingsState(
    val isAutoPlannerEnabled: Boolean = true,
    val activeRunningGoal: RunningGoal? = null,
    val isStrengthEnabled: Boolean = false,
    val runningConsidersStrength: Boolean = false,
    val strengthFirstWorkoutDate: LocalDate =
        LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
)

@HiltViewModel
class AutoPlannerSettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val uiState: StateFlow<AutoPlannerSettingsState> = combine(
        preferencesManager.autoPlannerEnabledFlow,
        preferencesManager.activeRunningGoalFlow,
        preferencesManager.autoPlanStrengthEnabledFlow,
        preferencesManager.runningConsidersStrengthFlow,
        preferencesManager.strengthFirstWorkoutDateFlow
    ) { smartPlanning, activeRunningGoal, strengthEnabled, considersStrength, firstWorkoutDate ->
        AutoPlannerSettingsState(
            isAutoPlannerEnabled = smartPlanning,
            activeRunningGoal = activeRunningGoal,
            isStrengthEnabled = strengthEnabled,
            runningConsidersStrength = considersStrength,
            strengthFirstWorkoutDate = firstWorkoutDate
        )
    }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutoPlannerSettingsState()
    )

    fun setAutoPlannerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoPlannerEnabled(enabled)
        }
    }

    fun setStrengthEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoPlanStrengthEnabled(enabled)
        }
    }

    fun setRunningConsidersStrength(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setRunningConsidersStrength(enabled)
        }
    }

    fun setStrengthFirstWorkoutDate(date: LocalDate) {
        viewModelScope.launch {
            preferencesManager.setStrengthFirstWorkoutDate(date)
        }
    }

    fun saveActiveRunningGoal(goal: RunningGoal) {
        viewModelScope.launch {
            preferencesManager.saveActiveRunningGoal(goal)
        }
    }

    fun clearActiveRunningGoal() {
        viewModelScope.launch {
            preferencesManager.clearActiveRunningGoal()
        }
    }
}

