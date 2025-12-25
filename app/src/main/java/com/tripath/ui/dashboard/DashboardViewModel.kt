package com.tripath.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class DashboardUiState(
    val weeklyPlannedTSS: Int = 0,
    val weeklyActualTSS: Int = 0,
    val weeklyLoadProgress: Float = 0f,
    val todaysPlan: TrainingPlan? = null,
    val isRestDay: Boolean = false,
    val restDayMessage: String = "Rest Day",
    val isWorkoutCompleted: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            val today = LocalDate.now()
            
            // Calculate current week (Monday to Sunday)
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)

            // Combine flows for weekly data
            combine(
                repository.getTrainingPlansByDateRange(weekStart, weekEnd),
                repository.getWorkoutLogsByDateRange(weekStart, weekEnd)
            ) { plans, logs ->
                // Calculate weekly TSS
                val plannedTSS = plans.sumOf { it.plannedTSS }
                val actualTSS = logs.sumOf { (it.computedTSS ?: 0) }
                
                // Calculate progress percentage
                val progress = when {
                    plannedTSS == 0 -> 0f
                    actualTSS > plannedTSS -> (actualTSS.toFloat() / plannedTSS.toFloat()) // Can exceed 100%
                    else -> (actualTSS.toFloat() / plannedTSS.toFloat())
                }

                // Get today's plan
                val todaysPlan = plans.firstOrNull { it.date == today }
                val isRestDay = todaysPlan == null
                
                // Check if workout is completed
                val isCompleted = if (todaysPlan != null) {
                    logs.any { it.date == today && it.type == todaysPlan.type }
                } else {
                    false
                }

                DashboardUiState(
                    weeklyPlannedTSS = plannedTSS,
                    weeklyActualTSS = actualTSS,
                    weeklyLoadProgress = progress,
                    todaysPlan = todaysPlan,
                    isRestDay = isRestDay,
                    restDayMessage = if (isRestDay) "Active Recovery" else "Rest Day",
                    isWorkoutCompleted = isCompleted
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}

