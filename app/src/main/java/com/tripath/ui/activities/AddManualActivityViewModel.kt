package com.tripath.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.WorkoutType
import com.tripath.domain.TrainingMetricsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class AddManualActivityUiState(
    val date: LocalDate = LocalDate.now(),
    val type: WorkoutType = WorkoutType.RUN,
    val durationHours: Int = 1,
    val durationMinutes: Int = 0,
    val zone: Int = 2,
    val computedTss: Int = 0,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddManualActivityViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddManualActivityUiState())
    val uiState: StateFlow<AddManualActivityUiState> = _uiState.asStateFlow()

    fun initDate(date: LocalDate) = recalculate(_uiState.value.copy(date = date))
    fun setDate(date: LocalDate) = recalculate(_uiState.value.copy(date = date))
    fun setType(type: WorkoutType) = recalculate(_uiState.value.copy(type = type))
    fun setDurationHours(h: Int) = recalculate(_uiState.value.copy(durationHours = h.coerceIn(0, 23)))
    fun setDurationMinutes(m: Int) = recalculate(_uiState.value.copy(durationMinutes = m.coerceIn(0, 59)))
    fun setZone(zone: Int) = recalculate(_uiState.value.copy(zone = zone.coerceIn(1, 5)))

    private fun recalculate(state: AddManualActivityUiState) {
        val totalMinutes = state.durationHours * 60 + state.durationMinutes
        val tss = if (totalMinutes > 0)
            TrainingMetricsCalculator.calculateManualTss(state.type, totalMinutes, state.zone)
        else 0
        _uiState.value = state.copy(computedTss = tss)
    }

    fun save() {
        val s = _uiState.value
        val totalMinutes = s.durationHours * 60 + s.durationMinutes
        if (totalMinutes <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val log = WorkoutLog(
                    connectId = "manual_${UUID.randomUUID()}",
                    date = s.date,
                    type = s.type,
                    durationMinutes = totalMinutes,
                    computedTSS = s.computedTss
                )
                repository.insertWorkoutLog(log)
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
            }
        }
    }
}
