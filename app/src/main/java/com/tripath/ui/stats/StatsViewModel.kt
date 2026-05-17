package com.tripath.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class TimePeriod {
    WEEK, MONTH, YEAR
}

data class WorkoutTypeStats(
    val type: WorkoutType,
    val count: Int,
    val totalDistance: Double, // in meters
    val totalTSS: Int,
    val totalDuration: Int, // in minutes
    val avgPace: Double = 0.0, // speed in km/h or pace
    val avgPower: Int = 0 // watts
)

data class VolumeDataPoint(
    val label: String,
    val durationHours: Double,
    val date: LocalDate,
    val type: WorkoutType? = null // For discipline coloring
)

data class VolumeGoalProgress(
    val label: String,
    val actualHours: Double,
    val goalHours: Double,
    val expectedHoursToDate: Double,
    val progressFraction: Float,
    val expectedFraction: Float,
    val deltaHours: Double
)


data class StatsUiState(
    val selectedPeriod: TimePeriod = TimePeriod.YEAR,
    val totalTSS: Int = 0,
    val totalWorkouts: Int = 0,
    val totalDistance: Double = 0.0, // in meters
    val totalHours: Double = 0.0,
    val annualVolumeGoalHours: Float? = null,
    val volumeGoalProgress: List<VolumeGoalProgress> = emptyList(),
    val volumeGoalAveragePerBucket: Double? = null,
    val volumeCurrentAveragePerBucket: Double? = null,
    val workoutTypeStats: Map<WorkoutType, WorkoutTypeStats> = emptyMap(),
    val tssTrendData: List<TssDataPoint> = emptyList(),
    val volumeTrendData: List<VolumeDataPoint> = emptyList(),
    val formScore: Int = 0, // Simplified Form score
    val formTrend: FormTrend = FormTrend.STABLE,
    val isLoading: Boolean = false
)

enum class FormTrend {
    IMPROVING, STABLE, DECLINING, FATIGUED
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    // New: Color by discipline toggle state
    private val _colorByDiscipline = MutableStateFlow(true)
    val colorByDiscipline: StateFlow<Boolean> = _colorByDiscipline.asStateFlow()

    fun setColorByDiscipline(enabled: Boolean) {
        _colorByDiscipline.value = enabled
    }

    fun saveAnnualVolumeGoal(goalHours: Float?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentProfile = repository.getUserProfileOnce() ?: UserProfile()
                repository.upsertUserProfile(
                    currentProfile.copy(annualVolumeGoalHours = goalHours)
                )
            }
            loadStats()
        }
    }

    init {
        loadStats()
    }

    fun selectPeriod(period: TimePeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val (startDate, endDate) = when (_uiState.value.selectedPeriod) {
                    TimePeriod.WEEK -> {
                        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        weekStart to weekStart.plusDays(6)
                    }
                    TimePeriod.MONTH -> {
                        val monthStart = today.withDayOfMonth(1)
                        monthStart to today.with(TemporalAdjusters.lastDayOfMonth())
                    }
                    TimePeriod.YEAR -> {
                        val yearStart = today.withDayOfYear(1)
                        yearStart to today.with(TemporalAdjusters.lastDayOfYear())
                    }
                }

                val userProfile = repository.getUserProfileOnce()
                val annualVolumeGoalHours = userProfile?.annualVolumeGoalHours

                val allLogs = repository.getAllWorkoutLogsOnce()
                val selectedLogs = allLogs
                    .filter { it.date.isAfter(startDate.minusDays(1)) && !it.date.isAfter(endDate) }

                // Calculate aggregate stats
                val totalTSS = selectedLogs.sumOf { it.computedTSS ?: 0 }
                val totalWorkouts = selectedLogs.size
                val totalDistance = selectedLogs.sumOf { it.distanceMeters ?: 0.0 }
                val totalMinutes = selectedLogs.sumOf { it.durationMinutes }
                val totalHours = totalMinutes / 60.0

                // Group by workout type
                val workoutTypeStats = selectedLogs
                    .groupBy { it.type }
                    .mapValues { (type, logs) ->
                        val duration = logs.sumOf { it.durationMinutes }
                        val distance = logs.sumOf { it.distanceMeters ?: 0.0 }
                        val avgSpeed = if (duration > 0) (distance / 1000.0) / (duration / 60.0) else 0.0
                        val avgPower = logs.mapNotNull { it.avgPowerWatts }.average().toInt()

                        WorkoutTypeStats(
                            type = type,
                            count = logs.size,
                            totalDistance = distance,
                            totalTSS = logs.sumOf { it.computedTSS ?: 0 },
                            totalDuration = duration,
                            avgPace = avgSpeed,
                            avgPower = avgPower
                        )
                    }
                    .filterKeys { it in listOf(WorkoutType.RUN, WorkoutType.BIKE, WorkoutType.SWIM, WorkoutType.STRENGTH, WorkoutType.WALK, WorkoutType.HIKE) }

                // Generate time series data for charts
                val tssTrendData = generateTSSData(selectedLogs, startDate, endDate, _uiState.value.selectedPeriod)
                val volumeTrendData = generateVolumeData(selectedLogs, startDate, endDate, _uiState.value.selectedPeriod)
                val volumeGoalProgress = annualVolumeGoalHours?.let {
                    buildVolumeGoalProgress(allLogs, today, it.toDouble())
                }.orEmpty()
                val volumeGoalAveragePerBucket = annualVolumeGoalHours?.let {
                    calculateAverageNeededPerBucket(it.toDouble(), _uiState.value.selectedPeriod, today)
                }
                val volumeCurrentAveragePerBucket = calculateCurrentAveragePerBucket(
                    logs = allLogs,
                    period = _uiState.value.selectedPeriod,
                    today = today
                )

                // Calculate Form (Simplified)
                val (formScore, formTrend) = calculateForm(selectedLogs, totalTSS)

                _uiState.value = _uiState.value.copy(
                    totalTSS = totalTSS,
                    totalWorkouts = totalWorkouts,
                    totalDistance = totalDistance,
                    totalHours = totalHours,
                    annualVolumeGoalHours = annualVolumeGoalHours,
                    volumeGoalProgress = volumeGoalProgress,
                    volumeGoalAveragePerBucket = volumeGoalAveragePerBucket,
                    volumeCurrentAveragePerBucket = volumeCurrentAveragePerBucket,
                    workoutTypeStats = workoutTypeStats,
                    tssTrendData = tssTrendData,
                    volumeTrendData = volumeTrendData,
                    formScore = formScore,
                    formTrend = formTrend,
                    isLoading = false
                )
            }
        }
    }

    private fun generateTSSData(
        logs: List<WorkoutLog>,
        startDate: LocalDate,
        endDate: LocalDate,
        period: TimePeriod
    ): List<TssDataPoint> {
        val types = WorkoutType.values()
        return when (period) {
            TimePeriod.WEEK -> {
                var currentDate = startDate
                val data = mutableListOf<TssDataPoint>()
                while (!currentDate.isAfter(endDate)) {
                    for (type in types) {
                        val dayLogs = logs.filter { it.date == currentDate && it.type == type }
                        val tss = dayLogs.sumOf { it.computedTSS ?: 0 }
                        data.add(TssDataPoint(
                            label = currentDate.dayOfWeek.name.take(1),
                            tss = tss,
                            date = currentDate,
                            type = type
                        ))
                    }
                    currentDate = currentDate.plusDays(1)
                }
                data
            }
            TimePeriod.MONTH -> {
                val data = mutableListOf<TssDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val weekEnd = currentDate.plusDays(6).coerceAtMost(endDate)
                    for (type in types) {
                        val periodLogs = logs.filter {
                            it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(weekEnd)
                                && it.type == type
                        }
                        val tss = periodLogs.sumOf { it.computedTSS ?: 0 }
                        data.add(TssDataPoint(
                            label = "${currentDate.dayOfMonth}",
                            tss = tss,
                            date = currentDate,
                            type = type
                        ))
                    }
                    currentDate = currentDate.plusDays(7)
                }
                data
            }
            TimePeriod.YEAR -> {
                val data = mutableListOf<TssDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val monthEnd = currentDate.withDayOfMonth(currentDate.lengthOfMonth())
                    for (type in types) {
                        val periodLogs = logs.filter {
                            it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(monthEnd)
                                && it.type == type
                        }
                        val tss = periodLogs.sumOf { it.computedTSS ?: 0 }
                        data.add(TssDataPoint(
                            label = currentDate.month.name.take(3),
                            tss = tss,
                            date = currentDate,
                            type = type
                        ))
                    }
                    currentDate = currentDate.plusMonths(1)
                }
                data
            }
        }
    }
    
    private fun generateVolumeData(
        logs: List<WorkoutLog>,
        startDate: LocalDate,
        endDate: LocalDate,
        period: TimePeriod
    ): List<VolumeDataPoint> {
         // Re-use logic structure from TSS but sum hours
         // For simplicity, using same intervals as TSS chart
        val types = WorkoutType.values()
        return when (period) {
            TimePeriod.WEEK -> {
                var currentDate = startDate
                val data = mutableListOf<VolumeDataPoint>()
                while (!currentDate.isAfter(endDate)) {
                    for (type in types) {
                        val dayLogs = logs.filter { it.date == currentDate && it.type == type }
                        val hours = dayLogs.sumOf { it.durationMinutes } / 60.0
                        data.add(VolumeDataPoint(
                            label = currentDate.dayOfWeek.name.take(1),
                            durationHours = hours,
                            date = currentDate,
                            type = type
                        ))
                    }
                    currentDate = currentDate.plusDays(1)
                }
                data
            }
            TimePeriod.MONTH -> {
                val data = mutableListOf<VolumeDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val weekEnd = currentDate.plusDays(6).coerceAtMost(endDate)
                    for (type in types) {
                        val periodLogs = logs.filter { 
                            it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(weekEnd) && it.type == type
                        }
                        val hours = periodLogs.sumOf { it.durationMinutes } / 60.0
                        data.add(VolumeDataPoint(
                            label = "${currentDate.dayOfMonth}",
                            durationHours = hours,
                            date = currentDate,
                            type = type
                        ))
                    }
                    currentDate = currentDate.plusDays(7)
                }
                data
            }
            TimePeriod.YEAR -> {
                val data = mutableListOf<VolumeDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val monthEnd = currentDate.withDayOfMonth(currentDate.lengthOfMonth())
                    for (type in types) {
                        val periodLogs = logs.filter {
                            it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(monthEnd) && it.type == type
                        }
                        val hours = periodLogs.sumOf { it.durationMinutes } / 60.0
                        data.add(VolumeDataPoint(
                            label = currentDate.month.name.take(3),
                            durationHours = hours,
                            date = currentDate,
                            type = type
                        ))
                    }
                    currentDate = currentDate.plusMonths(1)
                }
                data
            }
        }
    }

    private fun calculateForm(logs: List<WorkoutLog>, totalTSS: Int): Pair<Int, FormTrend> {
        // Very basic mock calculation since we don't have full history for CTL/ATL
        // In a real app, we'd query past 42 days rolling avg.
        
        // Simulating logic: if recent load is very high -> Fatigued
        // If consistent -> Stable/Improving
        
        val avgDailyTSS = if (logs.isNotEmpty()) totalTSS / logs.size else 0
        
        return when {
            avgDailyTSS > 100 -> -20 to FormTrend.FATIGUED
            avgDailyTSS > 60 -> 5 to FormTrend.IMPROVING
            else -> 0 to FormTrend.STABLE
        }
    }

    private fun buildVolumeGoalProgress(
        logs: List<WorkoutLog>,
        today: LocalDate,
        annualGoalHours: Double
    ): List<VolumeGoalProgress> {
        val yearStart = today.withDayOfYear(1)
        val monthStart = today.withDayOfMonth(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val yearLength = today.lengthOfYear().toDouble()
        val monthLength = today.lengthOfMonth().toDouble()
        val weekLength = 7.0

        fun hoursBetween(start: LocalDate, end: LocalDate): Double = logs
            .filter { it.date.isAfter(start.minusDays(1)) && !it.date.isAfter(end) }
            .sumOf { it.durationMinutes } / 60.0

        fun progress(
            label: String,
            actualHours: Double,
            goalHours: Double,
            elapsedFraction: Double
        ): VolumeGoalProgress {
            val expectedHoursToDate = goalHours * elapsedFraction
            return VolumeGoalProgress(
                label = label,
                actualHours = actualHours,
                goalHours = goalHours,
                expectedHoursToDate = expectedHoursToDate,
                progressFraction = (actualHours / goalHours).coerceAtLeast(0.0).toFloat(),
                expectedFraction = elapsedFraction.coerceIn(0.0, 1.0).toFloat(),
                deltaHours = actualHours - expectedHoursToDate
            )
        }

        val yearGoal = annualGoalHours
        val monthGoal = annualGoalHours * (monthLength / yearLength)
        val weekGoal = annualGoalHours * (weekLength / yearLength)
        val elapsedWeekDays = java.time.temporal.ChronoUnit.DAYS.between(weekStart, today).toDouble() + 1.0

        return listOf(
            progress(
                label = "Year",
                actualHours = hoursBetween(yearStart, today),
                goalHours = yearGoal,
                elapsedFraction = today.dayOfYear / yearLength
            ),
            progress(
                label = "Month",
                actualHours = hoursBetween(monthStart, today),
                goalHours = monthGoal,
                elapsedFraction = today.dayOfMonth / monthLength
            ),
            progress(
                label = "Week",
                actualHours = hoursBetween(weekStart, today),
                goalHours = weekGoal,
                elapsedFraction = elapsedWeekDays / weekLength
            )
        )
    }

    private fun calculateAverageNeededPerBucket(
        annualGoalHours: Double,
        period: TimePeriod,
        today: LocalDate
    ): Double {
        val yearLength = today.lengthOfYear().toDouble()
        return when (period) {
            TimePeriod.YEAR -> annualGoalHours / 12.0
            TimePeriod.MONTH -> annualGoalHours * 7.0 / yearLength
            TimePeriod.WEEK -> annualGoalHours / yearLength
        }
    }

    private fun calculateCurrentAveragePerBucket(
        logs: List<WorkoutLog>,
        period: TimePeriod,
        today: LocalDate
    ): Double? {
        val yearStart = today.withDayOfYear(1)
        val yearToDateHours = logs
            .filter { it.date.isAfter(yearStart.minusDays(1)) && !it.date.isAfter(today) }
            .sumOf { it.durationMinutes } / 60.0

        if (yearToDateHours <= 0.0) {
            return null
        }

        val elapsedYearFraction = today.dayOfYear / today.lengthOfYear().toDouble()
        if (elapsedYearFraction <= 0.0) {
            return null
        }

        val annualizedHours = yearToDateHours / elapsedYearFraction
        return calculateAverageNeededPerBucket(annualizedHours, period, today)
    }
}
