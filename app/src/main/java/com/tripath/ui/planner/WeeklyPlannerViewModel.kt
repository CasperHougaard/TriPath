package com.tripath.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeeklyPlannerUiState(
    val weekDays: List<WeekDay> = emptyList(),
    val selectedDate: LocalDate? = null,
    val showBottomSheet: Boolean = false
)

@HiltViewModel
class WeeklyPlannerViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyPlannerUiState())
    val uiState: StateFlow<WeeklyPlannerUiState> = _uiState.asStateFlow()

    private val dayNameFormatter = DateTimeFormatter.ofPattern("EEEE")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    init {
        loadWeekData()
    }

    private fun loadWeekData() {
        viewModelScope.launch {
            val today = LocalDate.now()
            
            // Calculate current week (Monday to Sunday) - CRITICAL: Use MONDAY as week start
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)

            // Generate list of days in the week
            val daysOfWeek = (0..6).map { weekStart.plusDays(it.toLong()) }

            // Observe training plans for the week
            repository.getTrainingPlansByDateRange(weekStart, weekEnd)
                .map { plans ->
                    daysOfWeek.map { date ->
                        val dayName = date.format(dayNameFormatter)
                        val dayWorkouts = plans.filter { it.date == date }
                        WeekDay(
                            date = date,
                            dayName = dayName,
                            workouts = dayWorkouts
                        )
                    }
                }
                .collect { weekDays ->
                    _uiState.value = _uiState.value.copy(weekDays = weekDays)
                }
        }
    }

    fun openAddWorkoutSheet(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            showBottomSheet = true
        )
    }

    fun closeBottomSheet() {
        _uiState.value = _uiState.value.copy(
            showBottomSheet = false,
            selectedDate = null
        )
    }

    fun addWorkout(workout: TrainingPlan) {
        viewModelScope.launch {
            repository.insertTrainingPlan(workout)
            closeBottomSheet()
        }
    }
}

