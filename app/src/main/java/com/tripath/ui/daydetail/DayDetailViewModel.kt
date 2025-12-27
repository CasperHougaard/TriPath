package com.tripath.ui.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.database.util.TemplateSerializer
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DayDetailUiState(
    val date: LocalDate,
    val plannedActivities: List<TrainingPlan> = emptyList(),
    val completedWorkouts: List<WorkoutLog> = emptyList(),
    val dayNote: DayNote? = null,
    val isLoading: Boolean = true,
    val userProfile: UserProfile? = null
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DayDetailUiState(date = LocalDate.now()))
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    val allTemplates: StateFlow<List<DayTemplate>> = repository.getAllDayTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadData(date: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(date = date, isLoading = true)
            
            combine(
                repository.getTrainingPlansByDateRange(date, date),
                repository.getWorkoutLogsByDateRange(date, date),
                repository.getDayNote(date),
                repository.getUserProfile()
            ) { plans, logs, note, profile ->
                DayDetailUiState(
                    date = date,
                    plannedActivities = plans,
                    completedWorkouts = logs,
                    dayNote = note,
                    isLoading = false,
                    userProfile = profile
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun addActivity(activity: TrainingPlan) {
        viewModelScope.launch {
            repository.insertTrainingPlan(activity)
        }
    }

    fun updateActivity(activity: TrainingPlan) {
        viewModelScope.launch {
            repository.updateTrainingPlan(activity)
        }
    }

    fun deleteActivity(activity: TrainingPlan) {
        viewModelScope.launch {
            repository.deleteTrainingPlan(activity)
        }
    }

    fun saveNote(noteText: String) {
        viewModelScope.launch {
            val currentDate = _uiState.value.date
            val existingNote = _uiState.value.dayNote
            
            if (noteText.isBlank()) {
                existingNote?.let { repository.deleteDayNote(it) }
            } else {
                val newNote = DayNote(date = currentDate, note = noteText)
                repository.insertDayNote(newNote)
            }
        }
    }

    fun saveCurrentAsTemplate(name: String) {
        viewModelScope.launch {
            val activities = _uiState.value.plannedActivities
            if (activities.isNotEmpty()) {
                val json = TemplateSerializer.serializeActivities(activities)
                val template = DayTemplate(name = name, activitiesJson = json)
                repository.insertDayTemplate(template)
            }
        }
    }

    fun applyTemplate(template: DayTemplate) {
        viewModelScope.launch {
            val date = _uiState.value.date
            val newActivities = TemplateSerializer.deserializeActivities(template.activitiesJson, date)
            
            // Per plan: "Replace" behavior - delete current activities for this day first
            _uiState.value.plannedActivities.forEach { 
                repository.deleteTrainingPlan(it) 
            }
            
            // Insert new activities
            repository.insertTrainingPlans(newActivities)
        }
    }

    fun deleteTemplate(template: DayTemplate) {
        viewModelScope.launch {
            repository.deleteDayTemplate(template)
        }
    }
}

