package com.tripath.ui.planner

import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlin.math.ceil

data class WeeklyRowState(
    val weekStart: LocalDate,
    val days: List<WeekDay>,
    val plannedTSS: Int,
    val actualTSS: Int,
    val totalDurationMinutes: Int,
    val tssCompletionProgress: Float,
    val hasTssJumpWarning: Boolean = false,
    val monthLabel: String? = null, // Label if month changes at this week
    val weekNumber: Int = 1 // Week number from start date
)

data class WeeklyPlannerUiState(
    val weeklyRows: List<WeeklyRowState> = emptyList(),
    val startDate: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val currentMonth: LocalDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()),
    val disciplineDistribution: Map<WorkoutType, Float> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val showBottomSheet: Boolean = false,
    val includeImportedActivities: Boolean = false,
    val isMonthView: Boolean = false,
    val userProfile: UserProfile? = null
)

@HiltViewModel
class WeeklyPlannerViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyPlannerUiState())
    val uiState: StateFlow<WeeklyPlannerUiState> = _uiState.asStateFlow()

    private val _isMonthView = MutableStateFlow(false)
    private val _startDate = MutableStateFlow(
        LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    private val _includeImportedActivities = MutableStateFlow(false)

    private val dayNameFormatter = DateTimeFormatter.ofPattern("E", java.util.Locale.ENGLISH)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM", java.util.Locale.ENGLISH)

    init {
        loadMatrixData()
        observeUserProfile()
        loadIncludeImportedPreference()
    }

    private fun loadIncludeImportedPreference() {
        viewModelScope.launch {
            preferencesManager.includeImportedActivitiesFlow.collect { include ->
                _includeImportedActivities.value = include
            }
        }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            repository.getUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(userProfile = profile)
            }
        }
    }

    private fun getReferenceMonth(start: LocalDate, isMonthView: Boolean): LocalDate {
        if (isMonthView) {
            // In month view, the start date is the Monday of the week containing the 1st
            // So we can find the 1st by looking ahead up to 6 days
            return (0..6).map { start.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == 1 } 
                ?: start.plusDays(7).with(TemporalAdjusters.firstDayOfMonth()) // Fallback
        } else {
            // In rolling view, find the month of the "center" of the view (start + 14 days)
            return start.plusDays(14).with(TemporalAdjusters.firstDayOfMonth())
        }
    }

    fun previousMonth() {
        val currentRefMonth = getReferenceMonth(_startDate.value, _isMonthView.value)
        val prevMonthFirst = currentRefMonth.minusMonths(1)
        _isMonthView.value = true
        _startDate.value = prevMonthFirst.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun nextMonth() {
        val currentRefMonth = getReferenceMonth(_startDate.value, _isMonthView.value)
        val nextMonthFirst = currentRefMonth.plusMonths(1)
        _isMonthView.value = true
        _startDate.value = nextMonthFirst.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun goToCurrent() {
        _isMonthView.value = false
        _startDate.value = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadMatrixData() {
        viewModelScope.launch {
            combine(_startDate, _includeImportedActivities, _isMonthView) { start, include, isMonthView -> Triple(start, include, isMonthView) }
                .flatMapLatest { (start, includeImported, isMonthView) ->
                    val numberOfWeeks = if (isMonthView) {
                        val firstOfMonth = getReferenceMonth(start, true)
                        val lastOfMonth = firstOfMonth.with(TemporalAdjusters.lastDayOfMonth())
                        val daysBetween = ChronoUnit.DAYS.between(start, lastOfMonth) + 1
                        ceil(daysBetween.toDouble() / 7.0).toInt()
                    } else {
                        4 // Standard rolling view
                    }
                    
                    val end = start.plusWeeks(numberOfWeeks.toLong()).minusDays(1)
                    combine(
                        repository.getTrainingPlansByDateRange(start, end),
                        repository.getWorkoutLogsByDateRange(start, end),
                        repository.getSpecialPeriodsByDateRange(start, end)
                    ) { plans, logs, specialPeriods ->
                        // Filter out training plans if Smart Planning is disabled
                        val filteredPlans = if (smartPlanningEnabled) {
                            plans
                        } else {
                            emptyList()
                        }
                        val rows = (0 until numberOfWeeks).map { weekIndex ->
                            val weekStart = start.plusWeeks(weekIndex.toLong())
                            
                            // Check if this week starts a new month or is the first week
                            val monthLabel = if (weekIndex == 0 || weekStart.month != weekStart.minusWeeks(1).month) {
                                weekStart.format(monthFormatter)
                            } else null

                            val daysOfWeek = (0 until 7).map { dayIndex ->
                                val date = weekStart.plusDays(dayIndex.toLong())
                                val dayPlans = filteredPlans.filter { it.date == date }
                                val dayLogs = logs.filter { it.date == date }
                                val daySpecialPeriods = specialPeriods.filter { 
                                    (date >= it.startDate && date <= it.endDate)
                                }
                                
                                WeekDay(
                                    date = date,
                                    dayName = date.format(dayNameFormatter),
                                    workouts = dayPlans,
                                    completedLogs = dayLogs,
                                    specialPeriods = daySpecialPeriods,
                                    isToday = date == LocalDate.now()
                                )
                            }

                            val weekPlans = filteredPlans.filter { it.date >= weekStart && it.date < weekStart.plusWeeks(1) }
                            val weekLogs = logs.filter { it.date >= weekStart && it.date < weekStart.plusWeeks(1) }
                            
                            val filteredWeekLogs = if (includeImported) {
                                weekLogs
                            } else {
                                weekLogs.filter { log ->
                                    weekPlans.any { plan ->
                                        plan.date == log.date && plan.type == log.type
                                    }
                                }
                            }
                            
                            val plannedTSS = weekPlans.sumOf { it.plannedTSS.toLong() }.toInt()
                            val actualTSS = filteredWeekLogs.sumOf { (it.computedTSS ?: 0).toLong() }.toInt()
                            val totalDuration = filteredWeekLogs.sumOf { it.durationMinutes.toLong() }.toInt()
                            
                            val progress = if (plannedTSS > 0) {
                                actualTSS.toFloat() / plannedTSS.toFloat()
                            } else if (actualTSS > 0) 1f else 0f

                            // Calculate actual ISO week number
                            val weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())

                            WeeklyRowState(
                                weekStart = weekStart,
                                days = daysOfWeek,
                                plannedTSS = plannedTSS,
                                actualTSS = actualTSS,
                                totalDurationMinutes = totalDuration,
                                tssCompletionProgress = progress.coerceIn(0f, 1f),
                                monthLabel = monthLabel,
                                weekNumber = weekNumber
                            )
                        }

                        // Calculate warnings
                        val weeklyRows = rows.mapIndexed { index, row ->
                            val hasWarning = if (index > 0) {
                                val prevPlannedTSS = rows[index - 1].plannedTSS
                                if (prevPlannedTSS > 0) {
                                    (row.plannedTSS.toFloat() / prevPlannedTSS.toFloat()) > 1.15f
                                } else {
                                    row.plannedTSS > 50
                                }
                            } else false
                            row.copy(hasTssJumpWarning = hasWarning)
                        }

                        // Distribution
                        val totalDuration = if (includeImported) {
                            filteredPlans.sumOf { it.durationMinutes } + logs.sumOf { it.durationMinutes }
                        } else {
                            filteredPlans.sumOf { it.durationMinutes }
                        }
                        
                        val distribution = if (totalDuration > 0) {
                            val planDistribution = filteredPlans.groupBy { it.type }
                                .mapValues { (_, workouts) ->
                                    workouts.sumOf { it.durationMinutes }.toFloat() / totalDuration.toFloat()
                                }
                            
                            if (includeImported) {
                                val logDistribution = logs.groupBy { it.type }
                                    .mapValues { (_, workoutLogs) ->
                                        workoutLogs.sumOf { it.durationMinutes }.toFloat() / totalDuration.toFloat()
                                    }
                                
                                (planDistribution.keys + logDistribution.keys).associateWith { type ->
                                    (planDistribution[type] ?: 0f) + (logDistribution[type] ?: 0f)
                                }
                            } else {
                                planDistribution
                            }
                        } else emptyMap()

                        val referenceMonth = getReferenceMonth(start, isMonthView)
                        
                        WeeklyPlannerUiState(
                            weeklyRows = weeklyRows,
                            startDate = start,
                            currentMonth = referenceMonth,
                            disciplineDistribution = distribution,
                            includeImportedActivities = includeImported,
                            isMonthView = isMonthView,
                            userProfile = _uiState.value.userProfile
                        )
                    }
                }.collect { newState ->
                    _uiState.value = newState
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

    fun deleteWorkout(workout: TrainingPlan) {
        viewModelScope.launch {
            repository.deleteTrainingPlan(workout)
        }
    }

    fun copyWeek(sourceWeekStart: LocalDate) {
        viewModelScope.launch {
            repository.copyWeek(
                sourceStartDate = sourceWeekStart,
                targetStartDate = sourceWeekStart.plusWeeks(1)
            )
        }
    }

    fun setIncludeImportedActivities(include: Boolean) {
        viewModelScope.launch {
            preferencesManager.setIncludeImportedActivities(include)
            _includeImportedActivities.value = include
        }
    }
}
