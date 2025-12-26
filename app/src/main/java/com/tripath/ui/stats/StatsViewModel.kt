package com.tripath.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
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

data class TimeSeriesDataPoint(
    val label: String,
    val value: Int,
    val date: LocalDate
)

data class VolumeDataPoint(
    val label: String,
    val durationHours: Double,
    val date: LocalDate
)

data class StatsUiState(
    val selectedPeriod: TimePeriod = TimePeriod.MONTH,
    val totalTSS: Int = 0,
    val totalWorkouts: Int = 0,
    val totalDistance: Double = 0.0, // in meters
    val totalHours: Double = 0.0,
    val workoutTypeStats: Map<WorkoutType, WorkoutTypeStats> = emptyMap(),
    val tssTrendData: List<TimeSeriesDataPoint> = emptyList(),
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

                // Get all workout logs in the date range
                val allLogs = repository.getAllWorkoutLogsOnce()
                    .filter { it.date.isAfter(startDate.minusDays(1)) && !it.date.isAfter(endDate) }

                // Calculate aggregate stats
                val totalTSS = allLogs.sumOf { it.computedTSS ?: 0 }
                val totalWorkouts = allLogs.size
                val totalDistance = allLogs.sumOf { it.distanceMeters ?: 0.0 }
                val totalMinutes = allLogs.sumOf { it.durationMinutes }
                val totalHours = totalMinutes / 60.0

                // Group by workout type
                val workoutTypeStats = allLogs
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

                // Generate time series data for charts
                val tssTrendData = generateTSSData(allLogs, startDate, endDate, _uiState.value.selectedPeriod)
                val volumeTrendData = generateVolumeData(allLogs, startDate, endDate, _uiState.value.selectedPeriod)

                // Calculate Form (Simplified)
                val (formScore, formTrend) = calculateForm(allLogs, totalTSS)

                _uiState.value = _uiState.value.copy(
                    totalTSS = totalTSS,
                    totalWorkouts = totalWorkouts,
                    totalDistance = totalDistance,
                    totalHours = totalHours,
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
    ): List<TimeSeriesDataPoint> {
        return when (period) {
            TimePeriod.WEEK -> {
                // Daily bars
                var currentDate = startDate
                val data = mutableListOf<TimeSeriesDataPoint>()
                while (!currentDate.isAfter(endDate)) {
                    val dayLogs = logs.filter { it.date == currentDate }
                    val dayTSS = dayLogs.sumOf { it.computedTSS ?: 0 }
                    data.add(TimeSeriesDataPoint(
                        label = currentDate.dayOfWeek.name.take(1),
                        value = dayTSS,
                        date = currentDate
                    ))
                    currentDate = currentDate.plusDays(1)
                }
                data
            }
            TimePeriod.MONTH -> {
                // Weekly bars? Or every 3 days? Let's do weekly aggregation for readability
                val data = mutableListOf<TimeSeriesDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val weekEnd = currentDate.plusDays(6).coerceAtMost(endDate)
                    val periodLogs = logs.filter { 
                        it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(weekEnd) 
                    }
                    val tss = periodLogs.sumOf { it.computedTSS ?: 0 }
                    data.add(TimeSeriesDataPoint(
                        label = "${currentDate.dayOfMonth}",
                        value = tss,
                        date = currentDate
                    ))
                    currentDate = currentDate.plusDays(7)
                }
                data
            }
            TimePeriod.YEAR -> {
                // Monthly bars
                val data = mutableListOf<TimeSeriesDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val monthEnd = currentDate.withDayOfMonth(currentDate.lengthOfMonth())
                    val periodLogs = logs.filter {
                         it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(monthEnd)
                    }
                    val tss = periodLogs.sumOf { it.computedTSS ?: 0 }
                    data.add(TimeSeriesDataPoint(
                        label = currentDate.month.name.take(3),
                        value = tss,
                        date = currentDate
                    ))
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
         return when (period) {
            TimePeriod.WEEK -> {
                var currentDate = startDate
                val data = mutableListOf<VolumeDataPoint>()
                while (!currentDate.isAfter(endDate)) {
                    val dayLogs = logs.filter { it.date == currentDate }
                    val hours = dayLogs.sumOf { it.durationMinutes } / 60.0
                    data.add(VolumeDataPoint(
                        label = currentDate.dayOfWeek.name.take(1),
                        durationHours = hours,
                        date = currentDate
                    ))
                    currentDate = currentDate.plusDays(1)
                }
                data
            }
            TimePeriod.MONTH -> {
                val data = mutableListOf<VolumeDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val weekEnd = currentDate.plusDays(6).coerceAtMost(endDate)
                    val periodLogs = logs.filter { 
                        it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(weekEnd) 
                    }
                    val hours = periodLogs.sumOf { it.durationMinutes } / 60.0
                    data.add(VolumeDataPoint(
                        label = "${currentDate.dayOfMonth}",
                        durationHours = hours,
                        date = currentDate
                    ))
                    currentDate = currentDate.plusDays(7)
                }
                data
            }
            TimePeriod.YEAR -> {
                val data = mutableListOf<VolumeDataPoint>()
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    val monthEnd = currentDate.withDayOfMonth(currentDate.lengthOfMonth())
                    val periodLogs = logs.filter {
                         it.date.isAfter(currentDate.minusDays(1)) && !it.date.isAfter(monthEnd)
                    }
                    val hours = periodLogs.sumOf { it.durationMinutes } / 60.0
                    data.add(VolumeDataPoint(
                        label = currentDate.month.name.take(3),
                        durationHours = hours,
                        date = currentDate
                    ))
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
}
