package com.tripath.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TaskTriggerType
import com.tripath.domain.NutritionTargets
import com.tripath.domain.RecoveryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class RecoveryTimeRange {
    WEEK, MONTH, YEAR
}

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
    val isLoading: Boolean = true,
    val suggestedWeight: Double? = null // Auto-filled weight from last recorded value
)

/**
 * Data class representing a single day in recovery history
 */
data class RecoveryHistoryDay(
    val date: LocalDate,
    val wellnessLog: DailyWellnessLog?,
    val dailyTss: Int,
    val sleepLog: SleepLog? = null
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

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _timeRange = MutableStateFlow(RecoveryTimeRange.MONTH)
    val timeRange: StateFlow<RecoveryTimeRange> = _timeRange.asStateFlow()

    private val today = LocalDate.now()

    /**
     * Suggested weight from last recorded value (for auto-fill)
     */
    private val _suggestedWeight = MutableStateFlow<Double?>(null)
    
    /**
     * Reactive UI state that updates when selectedDate changes
     */
    val uiState: StateFlow<RecoveryUiState> = combine(
        _selectedDate.flatMapLatest { date ->
            combine(
                recoveryRepository.getWellnessLogFlow(date),
                trainingRepository.getWorkoutLogsByDateRange(date, date),
                recoveryRepository.getAllTasks()
            ) { log, workouts, allTasks ->
                Triple(log, workouts, allTasks)
            }
        },
        _suggestedWeight
    ) { (log, workouts, allTasks), suggestedWeight ->
        // Use existing log if available, or null if no log exists for this date
        val currentLog = log

        // Calculate daily TSS
        val dailyTss = workouts.sumOf { it.computedTSS ?: 0 }

        // Get relevant tasks based on workouts
        val relevantTasks = RecoveryEngine.getRelevantTasks(workouts, allTasks)

        // Get completed task IDs from log (handle null log)
        val completedTaskIds = currentLog?.completedTaskIds.orEmpty().toSet()

        // Build active tasks with completion status
        val activeTasks = relevantTasks.map { task ->
            TaskItem(
                task = task,
                isCompleted = completedTaskIds.contains(task.id)
            )
        }

        // Calculate nutrition targets if weight is available (use current log weight or suggested weight)
        val effectiveWeight = currentLog?.morningWeight ?: suggestedWeight
        val nutritionTargets = effectiveWeight?.let { weight ->
            RecoveryEngine.calculateNutrition(weight, dailyTss)
        }

        // Get coach advice (handle null log with default)
        val coachAdvice = currentLog?.let { RecoveryEngine.getCoachAdvice(it) } ?: ""

        RecoveryUiState(
            currentLog = currentLog,
            nutritionTargets = nutritionTargets,
            activeTasks = activeTasks,
            coachAdvice = coachAdvice,
            isLoading = false,
            suggestedWeight = suggestedWeight
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecoveryUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            recoveryRepository.initializeDefaults()
        }
        loadSuggestedWeight()
    }

    /**
     * Load suggested weight when selectedDate changes
     */
    private fun loadSuggestedWeight() {
        viewModelScope.launch {
            _selectedDate.collect { date ->
                val log = recoveryRepository.getWellnessLog(date)
                if (log?.morningWeight == null) {
                    // Only suggest weight if log doesn't have one
                    val lastWeight = recoveryRepository.getLastRecordedWeight(date)
                    _suggestedWeight.value = lastWeight
                } else {
                    // Clear suggested weight if log already has weight
                    _suggestedWeight.value = null
                }
            }
        }
    }

    /**
     * All wellness tasks for habit consistency tracking
     */
    val allTasks: StateFlow<List<WellnessTaskDefinition>> = recoveryRepository.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Historical data state based on selected time range
     * Combines wellness logs with workout logs for trend analysis
     */
    val historyState: StateFlow<List<RecoveryHistoryDay>> = _timeRange
        .flatMapLatest { timeRange ->
            val (startDate, endDate) = when (timeRange) {
                RecoveryTimeRange.WEEK -> {
                    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    weekStart to weekStart.plusDays(6)
                }
                RecoveryTimeRange.MONTH -> {
                    val monthStart = today.withDayOfMonth(1)
                    monthStart to today.with(TemporalAdjusters.lastDayOfMonth())
                }
                RecoveryTimeRange.YEAR -> {
                    val yearStart = today.withDayOfYear(1)
                    yearStart to today.with(TemporalAdjusters.lastDayOfYear())
                }
            }
            
            combine(
                recoveryRepository.getWellnessLogsByDateRange(startDate, endDate),
                trainingRepository.getWorkoutLogsByDateRange(startDate, endDate),
                trainingRepository.getSleepLogsByDateRange(startDate, endDate)
            ) { logs, workouts, sleepLogs ->
                // Create a map of date to daily TSS
                val tssByDate = workouts.groupBy { it.date }
                    .mapValues { (_, dayWorkouts) ->
                        dayWorkouts.sumOf { it.computedTSS ?: 0 }
                    }
                
                // Create a map of date to sleep log
                val sleepLogByDate = sleepLogs.associateBy { it.date }
                
                // Create list of all days in range
                val daysInRange = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()
                (0..daysInRange).map { daysOffset ->
                    val date = startDate.plusDays(daysOffset.toLong())
                    val log = logs.find { it.date == date }
                    val dailyTss = tssByDate[date] ?: 0
                    val sleepLog = sleepLogByDate[date]
                    
                    RecoveryHistoryDay(
                        date = date,
                        wellnessLog = log,
                        dailyTss = dailyTss,
                        sleepLog = sleepLog
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Change the selected date
     */
    fun changeDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * Navigate to previous day
     */
    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    /**
     * Navigate to next day (limited to 7 days in the future)
     */
    fun nextDay() {
        val nextDate = _selectedDate.value.plusDays(1)
        val maxFutureDate = today.plusDays(7)
        if (!nextDate.isAfter(maxFutureDate)) {
            _selectedDate.value = nextDate
        }
    }

    /**
     * Jump back to today
     */
    fun jumpToToday() {
        _selectedDate.value = today
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
            val currentDate = _selectedDate.value
            val currentLog = recoveryRepository.getWellnessLog(currentDate) 
                ?: DailyWellnessLog(date = currentDate)
            
            val updatedLog = currentLog.copy(
                sorenessIndex = soreness,
                moodIndex = mood,
                allergySeverity = allergy
            )

            recoveryRepository.insertWellnessLog(updatedLog)
            // State will update automatically via reactive flow
        }
    }

    /**
     * Toggle task completion status
     */
    fun toggleTask(taskId: Long, isChecked: Boolean) {
        viewModelScope.launch {
            val currentDate = _selectedDate.value
            val currentLog = recoveryRepository.getWellnessLog(currentDate) 
                ?: DailyWellnessLog(date = currentDate)
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
            // State will update automatically via reactive flow
        }
    }

    /**
     * Update morning weight and recalculate nutrition targets
     */
    fun updateWeight(weight: Double) {
        viewModelScope.launch {
            val currentDate = _selectedDate.value
            val currentLog = recoveryRepository.getWellnessLog(currentDate) 
                ?: DailyWellnessLog(date = currentDate)
            
            val updatedLog = currentLog.copy(
                morningWeight = weight
            )

            recoveryRepository.insertWellnessLog(updatedLog)
            // Clear suggested weight since user explicitly set it
            _suggestedWeight.value = null
            // State will update automatically via reactive flow
        }
    }

    /**
     * Reset all data for the selected date
     */
    fun resetDay() {
        viewModelScope.launch {
            val currentDate = _selectedDate.value
            val currentLog = recoveryRepository.getWellnessLog(currentDate)
            currentLog?.let {
                recoveryRepository.deleteWellnessLog(it)
            }
            // Clear suggested weight
            _suggestedWeight.value = null
            // Reload suggested weight for this date
            val lastWeight = recoveryRepository.getLastRecordedWeight(currentDate)
            _suggestedWeight.value = lastWeight
            // State will update automatically via reactive flow
        }
    }

    /**
     * Log the day - saves any pre-filled weight that hasn't been explicitly saved yet
     */
    fun logDay() {
        viewModelScope.launch {
            val currentDate = _selectedDate.value
            val currentLog = recoveryRepository.getWellnessLog(currentDate)
            val suggestedWeight = _suggestedWeight.value
            
            // If we have a suggested weight but no saved weight, save it now
            if (currentLog?.morningWeight == null && suggestedWeight != null) {
                val logToSave = currentLog ?: DailyWellnessLog(date = currentDate)
                val updatedLog = logToSave.copy(morningWeight = suggestedWeight)
                recoveryRepository.insertWellnessLog(updatedLog)
                // Clear suggested weight since it's now saved
                _suggestedWeight.value = null
            }
            // State will update automatically via reactive flow
        }
    }

    /**
     * Set the time range for history view
     */
    fun setTimeRange(range: RecoveryTimeRange) {
        _timeRange.value = range
    }

    /**
     * Add a new habit/task definition
     */
    fun addNewHabit(title: String, type: TaskTriggerType, threshold: Int?) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                val task = WellnessTaskDefinition(
                    id = 0, // Auto-generated
                    title = title.trim(),
                    description = null,
                    type = type,
                    triggerThreshold = threshold
                )
                recoveryRepository.insertTask(task)
                // Tasks will update automatically via getAllTasks() flow
            }
        }
    }

    /**
     * Update an existing habit/task definition
     */
    fun updateHabit(
        id: Long,
        title: String,
        type: TaskTriggerType,
        threshold: Int?
    ) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                val task = WellnessTaskDefinition(
                    id = id,
                    title = title.trim(),
                    description = null,
                    type = type,
                    triggerThreshold = threshold
                )
                recoveryRepository.updateTask(task)
                // Tasks will update automatically via getAllTasks() flow
            }
        }
    }

    /**
     * Delete a habit/task definition
     */
    fun deleteHabit(definitionId: Long) {
        viewModelScope.launch {
            recoveryRepository.deleteTaskById(definitionId)
            // Tasks will update automatically via getAllTasks() flow
        }
    }
}
