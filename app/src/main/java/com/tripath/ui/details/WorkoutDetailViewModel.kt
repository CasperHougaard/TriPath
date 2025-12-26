package com.tripath.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Workout Detail Screen
 */
data class WorkoutDetailUiState(
    val trainingPlan: TrainingPlan? = null,
    val workoutLog: WorkoutLog? = null,
    val userProfile: UserProfile? = null,
    val linkedPlan: TrainingPlan? = null, // For TSS comparison with completed logs
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlanned: Boolean = false
)

/**
 * ViewModel for Workout Detail Screen
 * Handles data loading for both planned workouts and completed logs
 */
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    /**
     * Load workout data based on ID and type
     */
    fun loadWorkout(workoutId: String, isPlanned: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isPlanned = isPlanned)
            
            try {
                // Fetch user profile for HR zone calculations
                val profile = repository.getUserProfileOnce()
                
                if (isPlanned) {
                    // Load planned workout
                    val plan = repository.getTrainingPlanById(workoutId)
                    if (plan != null) {
                        _uiState.value = _uiState.value.copy(
                            trainingPlan = plan,
                            userProfile = profile,
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Workout not found",
                            isLoading = false
                        )
                    }
                } else {
                    // Load completed workout log
                    val log = repository.getWorkoutLogByConnectId(workoutId)
                    if (log != null) {
                        // Try to find linked plan for TSS comparison
                        val linkedPlan = findLinkedPlan(log)
                        
                        _uiState.value = _uiState.value.copy(
                            workoutLog = log,
                            linkedPlan = linkedPlan,
                            userProfile = profile,
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Workout not found",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load workout",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Find a training plan that matches this completed workout
     * Used for TSS comparison
     */
    private suspend fun findLinkedPlan(log: WorkoutLog): TrainingPlan? {
        return try {
            val plans = repository.getAllTrainingPlansOnce()
            plans.firstOrNull { plan ->
                plan.date == log.date && plan.type == log.type
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete the current workout
     */
    fun deleteWorkout(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state.isPlanned && state.trainingPlan != null) {
                    repository.deleteTrainingPlan(state.trainingPlan)
                    onDeleted()
                } else if (!state.isPlanned && state.workoutLog != null) {
                    repository.deleteWorkoutLog(state.workoutLog)
                    onDeleted()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete workout: ${e.message}"
                )
            }
        }
    }

    /**
     * Calculate HR zone percentage relative to max heart rate
     * Returns a value between 0.0 and 1.0
     */
    fun calculateHrZonePercentage(avgHeartRate: Int?, maxHeartRate: Int?): Float {
        if (avgHeartRate == null || maxHeartRate == null || maxHeartRate == 0) {
            return 0f
        }
        return (avgHeartRate.toFloat() / maxHeartRate.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Get HR zone label based on percentage of max HR
     */
    fun getHrZoneLabel(avgHeartRate: Int?, maxHeartRate: Int?): String {
        if (avgHeartRate == null || maxHeartRate == null || maxHeartRate == 0) {
            return "N/A"
        }
        val percentage = (avgHeartRate.toFloat() / maxHeartRate.toFloat()) * 100
        return when {
            percentage < 60 -> "Zone 1 (Recovery)"
            percentage < 70 -> "Zone 2 (Endurance)"
            percentage < 80 -> "Zone 3 (Tempo)"
            percentage < 90 -> "Zone 4 (Threshold)"
            else -> "Zone 5 (VO2 Max)"
        }
    }

    /**
     * Calculate max heart rate from log data if available
     * Note: WorkoutLog currently doesn't have maxHeartRate field, but we include this for future use
     */
    fun getMaxHeartRateFromLog(): Int? {
        // Currently WorkoutLog doesn't store max HR, only avg HR
        // This is a placeholder for future enhancement
        return null
    }
}

