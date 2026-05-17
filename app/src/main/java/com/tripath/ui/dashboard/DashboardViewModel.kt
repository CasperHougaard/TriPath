package com.tripath.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.WorkoutType
import com.tripath.domain.TrainingMetricsCalculator
import com.tripath.ui.model.FormStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private fun currentWeekStart(): LocalDate =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private data class DashboardWeekSelection(
    val visibleWeekStart: LocalDate = currentWeekStart(),
    val selectedDate: LocalDate = LocalDate.now()
)

enum class SyncStatus {
    IDLE, SYNCING, SUCCESS, ERROR
}

enum class DashboardActivityDisplayState {
    NONE,
    PLANNED,
    COMPLETED,
    MIXED
}

data class DashboardActivityCellState(
    val workoutType: WorkoutType,
    val displayMinutes: Int = 0,
    val plannedMinutes: Int = 0,
    val completedMinutes: Int = 0,
    val displayState: DashboardActivityDisplayState = DashboardActivityDisplayState.NONE
)

data class DashboardDayColumnState(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val cells: List<DashboardActivityCellState>
)

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
    val visibleWeekStart: LocalDate = currentWeekStart(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDatePlan: TrainingPlan? = null,
    val selectedDateLogs: List<WorkoutLog> = emptyList(),
    val isRestDay: Boolean = false,
    val restDayMessage: String = "Rest Day",
    val isWorkoutCompleted: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val syncError: String? = null,
    val lastSyncTimestamp: Long? = null,
    val weekDayStatuses: List<DayStatus> = emptyList(),
    val weekColumns: List<DashboardDayColumnState> = emptyList(),
    val greeting: String = "Good Morning",
    // Performance Metrics (Banister Impulse Response Model)
    val ctl: Double = 0.0,  // Chronic Training Load (Fitness)
    val atl: Double = 0.0,  // Acute Training Load (Fatigue)
    val tsb: Double = 0.0,  // Training Stress Balance (Form)
    val formStatus: FormStatus = FormStatus.OPTIMAL,
    val weeklyAllowedTSS: Int = 0
)

private val dashboardWorkoutOrder = listOf(
    WorkoutType.STRENGTH,
    WorkoutType.RUN,
    WorkoutType.BIKE,
    WorkoutType.HIKE,
    WorkoutType.WALK,
    WorkoutType.SWIM,
    WorkoutType.OTHER
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val healthConnectManager: HealthConnectManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _weekSelection = MutableStateFlow(DashboardWeekSelection())

    init {
        checkPermissionsAndSync()
        loadDashboardData()
        loadPerformanceMetrics()
        updateGreeting()
    }

    fun selectDate(date: LocalDate) {
        _weekSelection.value = _weekSelection.value.copy(selectedDate = date)
    }

    fun showPreviousWeek() {
        shiftVisibleWeek(weeks = -1)
    }

    fun showNextWeek() {
        shiftVisibleWeek(weeks = 1)
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
     * Load performance metrics (CTL, ATL, TSB) using the Banister Impulse Response model.
     */
    private fun loadPerformanceMetrics() {
        viewModelScope.launch {
            try {
                val allLogs = repository.getAllWorkoutLogsOnce()
                val metrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                    logs = allLogs,
                    targetDate = LocalDate.now()
                )
                
                val formStatus = when {
                    metrics.tsb > 5 -> FormStatus.FRESHNESS
                    metrics.tsb < -30 -> FormStatus.OVERREACHING
                    else -> FormStatus.OPTIMAL
                }

                val allowedTss = TrainingMetricsCalculator.calculateSafeWeeklyTSS(metrics.ctl)
                
                _uiState.value = _uiState.value.copy(
                    ctl = metrics.ctl,
                    atl = metrics.atl,
                    tsb = metrics.tsb,
                    formStatus = formStatus,
                    weeklyAllowedTSS = allowedTss
                )
            } catch (e: Exception) {
                // Silently handle errors - performance metrics are non-critical
            }
        }
    }

    /**
     * Check Health Connect permissions and trigger auto-sync if granted.
     */
    private fun checkPermissionsAndSync() {
        viewModelScope.launch {
            try {
                val hasPermissions = healthConnectManager.hasAllPermissions()

                _uiState.value = _uiState.value.copy(
                    hasHealthConnectPermissions = hasPermissions
                )

                if (hasPermissions) {
                    syncWorkoutsFromHealthConnect()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    hasHealthConnectPermissions = false,
                    syncStatus = SyncStatus.ERROR,
                    syncError = e.message ?: "Unable to start Health Connect sync"
                )
            }
        }
    }

    /**
     * Trigger a sync with Health Connect (workouts and sleep).
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
                
                // Perform sync on IO thread - workouts and sleep
                val workoutResult = withContext(Dispatchers.IO) {
                    healthConnectManager.syncWorkouts(daysToLookBack = syncDays)
                }
                
                // Also sync sleep data
                withContext(Dispatchers.IO) {
                    healthConnectManager.syncSleep(daysToLookBack = syncDays)
                }
                
                if (workoutResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        syncStatus = SyncStatus.SUCCESS,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    // Dashboard flows already observe the underlying Room tables, so avoid
                    // creating duplicate collectors here. Only refresh the computed metrics.
                    loadPerformanceMetrics()
                    
                    // Reset success status after a delay
                    launch {
                        kotlinx.coroutines.delay(3000)
                        _uiState.value = _uiState.value.copy(syncStatus = SyncStatus.IDLE)
                    }
                } else {
                    val error = workoutResult.exceptionOrNull()
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

            _weekSelection.flatMapLatest { selection ->
                val weekStart = selection.visibleWeekStart
                val weekEnd = weekStart.plusDays(6)

                combine(
                    repository.getTrainingPlansByDateRange(weekStart, weekEnd),
                    repository.getWorkoutLogsByDateRange(weekStart, weekEnd)
                ) { plans, logs ->
                    Triple(selection, plans, logs)
                }
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    syncStatus = SyncStatus.ERROR,
                    syncError = e.message ?: "Unable to load dashboard data"
                )
            }.collect { (selection, plans, logs) ->
                val weekStart = selection.visibleWeekStart
                val selectedDate = selection.selectedDate
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

                val weekColumns = (0..6).map { i ->
                    val date = weekStart.plusDays(i.toLong())
                    val dayPlans = plans.filter { it.date == date }
                    val dayLogs = logs.filter { it.date == date }

                    DashboardDayColumnState(
                        date = date,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        cells = dashboardWorkoutOrder.map { workoutType ->
                            val plannedMinutes = dayPlans
                                .filter { it.type == workoutType }
                                .sumOf { it.durationMinutes }
                            val completedMinutes = dayLogs
                                .filter { it.type == workoutType }
                                .sumOf { it.durationMinutes }
                            val displayState = when {
                                completedMinutes > 0 && plannedMinutes > 0 -> DashboardActivityDisplayState.MIXED
                                completedMinutes > 0 -> DashboardActivityDisplayState.COMPLETED
                                plannedMinutes > 0 -> DashboardActivityDisplayState.PLANNED
                                else -> DashboardActivityDisplayState.NONE
                            }
                            val displayMinutes = when (displayState) {
                                DashboardActivityDisplayState.MIXED -> maxOf(plannedMinutes, completedMinutes)
                                DashboardActivityDisplayState.COMPLETED -> completedMinutes
                                DashboardActivityDisplayState.PLANNED -> plannedMinutes
                                DashboardActivityDisplayState.NONE -> 0
                            }

                            DashboardActivityCellState(
                                workoutType = workoutType,
                                displayMinutes = displayMinutes,
                                plannedMinutes = plannedMinutes,
                                completedMinutes = completedMinutes,
                                displayState = displayState
                            )
                        }
                    )
                }

                // Preserve Health Connect sync state when updating dashboard data
                _uiState.value = _uiState.value.copy(
                    weeklyPlannedTSS = plannedTSS,
                    weeklyActualTSS = actualTSS,
                    weeklyLoadProgress = progress,
                    visibleWeekStart = weekStart,
                    selectedDate = selectedDate,
                    selectedDatePlan = selectedDatePlan,
                    selectedDateLogs = selectedDateLogs,
                    isRestDay = isRestDay,
                    restDayMessage = if (isRestDay) "Active Recovery" else "Rest Day",
                    isWorkoutCompleted = isCompleted,
                    weekDayStatuses = weekDayStatuses,
                    weekColumns = weekColumns
                )
            }
        }
    }

    private fun shiftVisibleWeek(weeks: Long) {
        val currentSelection = _weekSelection.value
        val newWeekStart = currentSelection.visibleWeekStart.plusWeeks(weeks)
        val dayOffset = currentSelection.selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value

        _weekSelection.value = currentSelection.copy(
            visibleWeekStart = newWeekStart,
            selectedDate = newWeekStart.plusDays(dayOffset.toLong())
        )
    }
}
