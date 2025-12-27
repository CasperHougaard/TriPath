package com.tripath.ui.settings.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SyncedExercisesUiState(
    val isLoading: Boolean = false,
    val exercises: List<ExerciseSessionRecord> = emptyList(),
    val errorMessage: String? = null,
    val syncDaysBack: Int = 30
)

@HiltViewModel
class SyncedExercisesViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncedExercisesUiState())
    val uiState: StateFlow<SyncedExercisesUiState> = _uiState.asStateFlow()

    init {
        loadExercises()
    }

    fun loadExercises() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            val syncDays = preferencesManager.syncDaysFlow.first()
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(syncDays.toLong())
            
            val sessions = healthConnectManager.readRawExerciseSessions(startDate, endDate)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                exercises = sessions,
                syncDaysBack = syncDays
            )
        }
    }
}

