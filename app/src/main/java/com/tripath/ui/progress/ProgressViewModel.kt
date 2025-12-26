package com.tripath.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.domain.TrainingMetricsCalculator
import com.tripath.ui.model.FormStatus
import com.tripath.ui.model.PerformanceDataPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ProgressUiState(
    val currentTSB: Double = 0.0,
    val currentStatus: FormStatus = FormStatus.OPTIMAL,
    val performanceData: List<PerformanceDataPoint> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

    init {
        loadProgressData()
    }

    fun loadProgressData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                val startDate = today.minusDays(90)

                // Fetch all workout logs (we need all logs up to today for accurate CTL/ATL calculation)
                val allLogs = repository.getAllWorkoutLogsOnce()
                    .filter { !it.date.isAfter(today) }

                // Calculate performance metrics for each day in the 90-day range
                val performanceData = mutableListOf<PerformanceDataPoint>()
                
                var currentDate = startDate
                while (!currentDate.isAfter(today)) {
                    val metrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                        logs = allLogs,
                        targetDate = currentDate
                    )
                    
                    // Create label for x-axis (show every ~15 days or use smart labeling)
                    val label = if (currentDate.dayOfMonth == 1 || 
                                   currentDate.dayOfMonth == 15 || 
                                   currentDate == startDate || 
                                   currentDate == today) {
                        shortDateFormatter.format(currentDate)
                    } else {
                        ""
                    }
                    
                    performanceData.add(
                        PerformanceDataPoint(
                            date = currentDate,
                            ctl = metrics.ctl,
                            atl = metrics.atl,
                            tsb = metrics.tsb,
                            label = label
                        )
                    )
                    
                    currentDate = currentDate.plusDays(1)
                }

                // Get today's metrics for current status
                val todayMetrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                    logs = allLogs,
                    targetDate = today
                )
                
                val currentStatus = determineFormStatus(todayMetrics.tsb)

                _uiState.value = _uiState.value.copy(
                    currentTSB = todayMetrics.tsb,
                    currentStatus = currentStatus,
                    performanceData = performanceData,
                    isLoading = false
                )
            }
        }
    }

    private fun determineFormStatus(tsb: Double): FormStatus {
        return when {
            tsb > 5.0 -> FormStatus.FRESHNESS
            tsb >= -30.0 && tsb <= -10.0 -> FormStatus.OPTIMAL
            tsb < -30.0 -> FormStatus.OVERREACHING
            else -> FormStatus.OPTIMAL // Values between -10 and +5 are treated as optimal
        }
    }
}

