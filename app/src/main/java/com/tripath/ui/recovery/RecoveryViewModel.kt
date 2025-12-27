package com.tripath.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.AllergySeverity
import com.tripath.domain.NutritionTargets
import com.tripath.domain.RecoveryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * UI State for Recovery Hub Screen
 */
data class TaskItem(
    val task: WellnessTaskDefinition,
    val isCompleted: Boolean
)

data class RecoveryUiState(
    val currentLog: DailyWellnessLog? = null,
    val nutritionTargets: NutritionTargets? = null,
    val activeTasks: List<TaskItem> = emptyList(),
    val coachAdvice: String = "",
    val isLoading: Boolean = true
)

/**
 * ViewModel for Recovery Hub Screen
 * Manages wellness logs, nutrition calculations, and recovery tasks
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val recoveryRepository: RecoveryRepository,
    private val trainingRepository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    private val today = LocalDate.now()

    init {
        initializeDefaults()
        loadRecoveryData()
    }

    /**
     * Initialize default tasks if the database is empty
     */
    private fun initializeDefaults() {
        viewModelScope.launch {
            recoveryRepository.initializeDefaults()
        }
    }

    /**
     * Load today's wellness log and workouts, then calculate derived state
     */
    private fun loadRecoveryData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Load today's wellness log (or create default if missing)
                var currentLog = recoveryRepository.getWellnessLog(today)
                if (currentLog == null) {
                    currentLog = DailyWellnessLog(date = today)
                    recoveryRepository.insertWellnessLog(currentLog)
                }

                // Load today's workouts
                val allWorkouts = trainingRepository.getAllWorkoutLogsOnce()
                val todayWorkouts = allWorkouts.filter { it.date == today }

                // Calculate daily TSS
                val dailyTss = todayWorkouts.sumOf { it.computedTSS ?: 0 }

                // Load all task definitions
                val allTasks = recoveryRepository.getAllTasksOnce()

                // Get relevant tasks based on workouts
                val relevantTasks = RecoveryEngine.getRelevantTasks(todayWorkouts, allTasks)

                // Get completed task IDs from log
                val completedTaskIds = currentLog.completedTaskIds.orEmpty().toSet()

                // Build active tasks with completion status
                val activeTasks = relevantTasks.map { task ->
                    TaskItem(
                        task = task,
                        isCompleted = completedTaskIds.contains(task.id)
                    )
                }

                // Calculate nutrition targets if weight is available
                val nutritionTargets = currentLog.morningWeight?.let { weight ->
                    RecoveryEngine.calculateNutrition(weight, dailyTss)
                }

                // Get coach advice
                val coachAdvice = RecoveryEngine.getCoachAdvice(currentLog)

                _uiState.value = _uiState.value.copy(
                    currentLog = currentLog,
                    nutritionTargets = nutritionTargets,
                    activeTasks = activeTasks,
                    coachAdvice = coachAdvice,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                // TODO: Handle error state if needed
            }
        }
    }

    /**
     * Update subjective metrics (soreness, mood, allergy severity)
     */
    fun updateSubjectiveMetrics(
        soreness: Int?,
        mood: Int?,
        allergy: AllergySeverity?
    ) {
        viewModelScope.launch {
            val currentLog = _uiState.value.currentLog ?: DailyWellnessLog(date = today)
            
            val updatedLog = currentLog.copy(
                sorenessIndex = soreness,
                moodIndex = mood,
                allergySeverity = allergy
            )

            recoveryRepository.insertWellnessLog(updatedLog)

            // Recalculate coach advice
            val coachAdvice = RecoveryEngine.getCoachAdvice(updatedLog)

            _uiState.value = _uiState.value.copy(
                currentLog = updatedLog,
                coachAdvice = coachAdvice
            )
        }
    }

    /**
     * Toggle task completion status
     */
    fun toggleTask(taskId: Long, isChecked: Boolean) {
        viewModelScope.launch {
            val currentLog = _uiState.value.currentLog ?: DailyWellnessLog(date = today)
            val completedTaskIds = currentLog.completedTaskIds.orEmpty().toMutableList()

            if (isChecked) {
                if (!completedTaskIds.contains(taskId)) {
                    completedTaskIds.add(taskId)
                }
            } else {
                completedTaskIds.remove(taskId)
            }

            val updatedLog = currentLog.copy(
                completedTaskIds = completedTaskIds
            )

            recoveryRepository.insertWellnessLog(updatedLog)

            // Update active tasks in state
            val activeTasks = _uiState.value.activeTasks.map { taskItem ->
                if (taskItem.task.id == taskId) {
                    taskItem.copy(isCompleted = isChecked)
                } else {
                    taskItem
                }
            }

            _uiState.value = _uiState.value.copy(
                currentLog = updatedLog,
                activeTasks = activeTasks
            )
        }
    }

    /**
     * Update morning weight and recalculate nutrition targets
     */
    fun updateWeight(weight: Double) {
        viewModelScope.launch {
            val currentLog = _uiState.value.currentLog ?: DailyWellnessLog(date = today)
            
            val updatedLog = currentLog.copy(
                morningWeight = weight
            )

            recoveryRepository.insertWellnessLog(updatedLog)

            // Recalculate nutrition targets
            val allWorkouts = trainingRepository.getAllWorkoutLogsOnce()
            val todayWorkouts = allWorkouts.filter { it.date == today }
            val dailyTss = todayWorkouts.sumOf { it.computedTSS ?: 0 }
            val nutritionTargets = RecoveryEngine.calculateNutrition(weight, dailyTss)

            _uiState.value = _uiState.value.copy(
                currentLog = updatedLog,
                nutritionTargets = nutritionTargets
            )
        }
    }
}
