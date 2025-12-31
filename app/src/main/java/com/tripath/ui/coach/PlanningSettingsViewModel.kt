package com.tripath.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanningSettingsState(
    val isSmartPlanningEnabled: Boolean = true,
    val runConsecutiveAllowed: Boolean = false,
    val strengthSpacingHours: Int = 48,
    val rampRateLimit: Float = 5.0f,
    val mechanicalLoadMonitoring: Boolean = true,
    val allowCommuteExemption: Boolean = true
)

@HiltViewModel
class PlanningSettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private data class FirstFiveSettings(
        val smartPlanning: Boolean,
        val consecutive: Boolean,
        val spacing: Int,
        val rampRate: Float,
        val monitoring: Boolean
    )

    val uiState: StateFlow<PlanningSettingsState> = combine(
        preferencesManager.smartPlanningEnabledFlow,
        preferencesManager.runConsecutiveAllowedFlow,
        preferencesManager.strengthSpacingHoursFlow,
        preferencesManager.rampRateLimitFlow,
        preferencesManager.mechanicalLoadMonitoringFlow
    ) { smartPlanning: Boolean, consecutive: Boolean, spacing: Int, rampRate: Float, monitoring: Boolean ->
        FirstFiveSettings(smartPlanning, consecutive, spacing, rampRate, monitoring)
    }.combine(preferencesManager.allowCommuteExemptionFlow) { first5: FirstFiveSettings, commute: Boolean ->
        PlanningSettingsState(
            isSmartPlanningEnabled = first5.smartPlanning,
            runConsecutiveAllowed = first5.consecutive,
            strengthSpacingHours = first5.spacing,
            rampRateLimit = first5.rampRate,
            mechanicalLoadMonitoring = first5.monitoring,
            allowCommuteExemption = commute
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlanningSettingsState()
    )

    fun setSmartPlanning(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmartPlanningEnabled(enabled)
        }
    }

    fun setRunConsecutiveAllowed(allowed: Boolean) {
        viewModelScope.launch {
            preferencesManager.setRunConsecutiveAllowed(allowed)
        }
    }

    fun setStrengthSpacingHours(hours: Int) {
        viewModelScope.launch {
            preferencesManager.setStrengthSpacingHours(hours)
        }
    }

    fun setRampRateLimit(limit: Float) {
        viewModelScope.launch {
            preferencesManager.setRampRateLimit(limit)
        }
    }

    fun setMechanicalLoadMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setMechanicalLoadMonitoring(enabled)
        }
    }

    fun setAllowCommuteExemption(allowed: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAllowCommuteExemption(allowed)
        }
    }
}

