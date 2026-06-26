package com.tripath.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.WorkoutType
import com.tripath.ui.coach.RunningGoalEditorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RunningGoalEditorUiState(
    val editorState: RunningGoalEditorState = RunningGoalEditorState(),
    val suggestedLongestRunKm: Float? = null,
    val suggestedWeeklyVolumeKm: Float? = null
)

@HiltViewModel
class RunningGoalEditorViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val trainingRepository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningGoalEditorUiState())
    val uiState: StateFlow<RunningGoalEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activeGoal = preferencesManager.getActiveRunningGoal()
            _uiState.update { it.copy(editorState = RunningGoalEditorState.fromGoal(activeGoal)) }
        }
        viewModelScope.launch {
            loadBaselineSuggestions()
        }
    }

    private suspend fun loadBaselineSuggestions() {
        val cutoff = LocalDate.now().minusDays(60).toEpochDay()
        val recentRuns = trainingRepository.getWorkoutLogsByType(WorkoutType.RUN)
            .first()
            .filter { it.date.toEpochDay() >= cutoff && it.distanceMeters != null && it.distanceMeters > 0 }

        if (recentRuns.isEmpty()) return

        val longestKm = recentRuns.maxOf { it.distanceMeters!! }.toFloat() / 1000f

        val cutoff28 = LocalDate.now().minusDays(28).toEpochDay()
        val last28Runs = recentRuns.filter { it.date.toEpochDay() >= cutoff28 }
        val weeklyVolumeKm = if (last28Runs.isNotEmpty())
            last28Runs.sumOf { it.distanceMeters!! }.toFloat() / 4f / 1000f
        else null

        _uiState.update { it.copy(suggestedLongestRunKm = longestKm, suggestedWeeklyVolumeKm = weeklyVolumeKm) }
    }

    fun save(state: RunningGoalEditorState) {
        val goal = state.toRunningGoalOrNull() ?: return
        viewModelScope.launch {
            preferencesManager.saveActiveRunningGoal(goal)
        }
    }
}
