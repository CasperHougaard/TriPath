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
import javax.inject.Inject

data class AutoPlannerSettingsState(
    val isAutoPlannerEnabled: Boolean = true,
    val activeRunningGoal: RunningGoal? = null
)

@HiltViewModel
class AutoPlannerSettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val uiState: StateFlow<AutoPlannerSettingsState> = preferencesManager.autoPlannerEnabledFlow
        .combine(preferencesManager.activeRunningGoalFlow) { smartPlanning: Boolean, activeRunningGoal: RunningGoal? ->
            AutoPlannerSettingsState(
                isAutoPlannerEnabled = smartPlanning,
                activeRunningGoal = activeRunningGoal
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

