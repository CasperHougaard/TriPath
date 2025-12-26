package com.tripath.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class SyncStatus {
    IDLE, SYNCING, SUCCESS, ERROR
}

data class DayStatus(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasPlan: Boolean,
    val isCompleted: Boolean,
    val isRestDay: Boolean
)

data class DashboardUiState(
    val weeklyPlannedTSS: Int = 0,
    val weeklyActualTSS: Int = 0,
    val weeklyLoadProgress: Float = 0f,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDatePlan: TrainingPlan? = null,
    val selectedDateLogs: List<WorkoutLog> = emptyList(),
    val isRestDay: Boolean = false, // Relative to selectedDate
    val restDayMessage: String = "Rest Day", // Relative to selectedDate
    val isWorkoutCompleted: Boolean = false, // Relative to selectedDate
    val hasHealthConnectPermissions: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val syncError: String? = null,
    val lastSyncTimestamp: Long? = null,
    val weekDayStatuses: List<DayStatus> = emptyList(),
    val greeting: String = "Good Morning"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val healthConnectManager: HealthConnectManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    init {
        checkPermissionsAndSync()
        loadDashboardData()
        updateGreeting()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    private fun updateGreeting() {
        val hour = LocalTime.now().hour
        val greeting = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            in 18..22 -> "Good Evening"
            else -> "Good Night"
        }
        _uiState.value = _uiState.value.copy(greeting = greeting)
    }

    /**
     * Check Health Connect permissions and trigger auto-sync if granted.
     */
    private fun checkPermissionsAndSync() {
        viewModelScope.launch {
            val hasPermissions = healthConnectManager.hasAllPermissions()
            
            _uiState.value = _uiState.value.copy(
                hasHealthConnectPermissions = hasPermissions
            )
            
            if (hasPermissions) {
                syncWorkoutsFromHealthConnect()
            }
        }
    }

    /**
     * Trigger a sync with Health Connect.
     */
    fun syncData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                syncStatus = SyncStatus.SYNCING,
                syncError = null
            )
            
            try {
                // Get the sync days preference
                val syncDays = preferencesManager.syncDaysFlow.first()
                
                // Perform sync on IO thread
                val result = withContext(Dispatchers.IO) {
                    healthConnectManager.syncWorkouts(daysToLookBack = syncDays)
                }
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        syncStatus = SyncStatus.SUCCESS,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    // Reload dashboard data to reflect new workouts
                    loadDashboardData()
                    
                    // Reset success status after a delay
                    launch {
                        kotlinx.coroutines.delay(3000)
                        _uiState.value = _uiState.value.copy(syncStatus = SyncStatus.IDLE)
                    }
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        syncStatus = SyncStatus.ERROR,
                        syncError = error?.message ?: "Sync failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    syncStatus = SyncStatus.ERROR,
                    syncError = e.message ?: "Sync failed"
                )
            }
        }
    }

    /**
     * Sync workouts from Health Connect to the local database.
     * Legacy method, preferred is syncData().
     * 
     * @param days Number of days to look back (default is 30)
     */
    fun syncWorkoutsFromHealthConnect(days: Int = 30) {
        syncData()
    }

    /**
     * Perform a full historical sync from Health Connect.
     */
    fun syncFullHistory() {
        syncWorkoutsFromHealthConnect(days = 365) // Sync up to a year
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
                repository.getWorkoutLogsByDateRange(weekStart, weekEnd),
                _selectedDate
            ) { plans, logs, selectedDate ->
                // Calculate weekly TSS
                val plannedTSS = plans.sumOf { it.plannedTSS }
                val actualTSS = logs.sumOf { (it.computedTSS ?: 0) }
                
                // Calculate progress percentage
                val progress = when {
                    plannedTSS == 0 -> 0f
                    actualTSS > plannedTSS -> (actualTSS.toFloat() / plannedTSS.toFloat()) // Can exceed 100%
                    else -> (actualTSS.toFloat() / plannedTSS.toFloat())
                }

                // Get selected day's data
                val selectedDatePlan = plans.find { it.date == selectedDate }
                val selectedDateLogs = logs.filter { it.date == selectedDate }
                val isRestDay = selectedDatePlan == null
                
                // Check if workout is completed (for the selected day)
                val isCompleted = if (selectedDatePlan != null) {
                    logs.any { it.date == selectedDate && it.type == selectedDatePlan.type }
                } else {
                    false
                }

                // Build week day statuses
                val weekDayStatuses = (0..6).map { i ->
                    val date = weekStart.plusDays(i.toLong())
                    val planForDay = plans.find { it.date == date }
                    val logForDay = logs.find { it.date == date && (planForDay == null || it.type == planForDay.type) }
                    
                    DayStatus(
                        date = date,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        hasPlan = planForDay != null,
                        isCompleted = logForDay != null,
                        isRestDay = planForDay == null
                    )
                }

                // Preserve Health Connect sync state when updating dashboard data
                _uiState.value.copy(
                    weeklyPlannedTSS = plannedTSS,
                    weeklyActualTSS = actualTSS,
                    weeklyLoadProgress = progress,
                    selectedDate = selectedDate,
                    selectedDatePlan = selectedDatePlan,
                    selectedDateLogs = selectedDateLogs,
                    isRestDay = isRestDay,
                    restDayMessage = if (isRestDay) "Active Recovery" else "Rest Day",
                    isWorkoutCompleted = isCompleted,
                    weekDayStatuses = weekDayStatuses
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
