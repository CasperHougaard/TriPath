package com.tripath.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.UserProfile
import com.tripath.domain.CoachEngine
import com.tripath.domain.PerformanceMetrics
import com.tripath.domain.TrainingMetricsCalculator
import com.tripath.domain.TrainingPhase
import com.tripath.ui.model.FormStatus
import com.tripath.ui.model.PerformanceDataPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private data class Data(
    val profile: UserProfile?,
    val activePeriods: List<SpecialPeriod>,
    val allPeriods: List<SpecialPeriod>,
    val logs: List<WorkoutLog>
)

data class CoachUiState(
    val currentPhase: TrainingPhase? = null,
    val activeSpecialPeriods: List<SpecialPeriod> = emptyList(),
    val allSpecialPeriods: List<SpecialPeriod> = emptyList(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(0.0, 0.0, 0.0),
    val coachAssessment: String = "Loading assessment...",
    val performanceData: List<PerformanceDataPoint> = emptyList(),
    val goalDate: LocalDate? = null,
    val isLoading: Boolean = false,
    val formStatus: FormStatus = FormStatus.OPTIMAL
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

    init {
        loadCoachData()
    }

    fun loadCoachData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val today = LocalDate.now()
            
            combine(
                repository.getUserProfile(),
                repository.getActiveSpecialPeriods(today),
                repository.getAllSpecialPeriods(),
                repository.getAllWorkoutLogs() // We need logs for metrics
            ) { profile, activePeriods, allPeriods, logs ->
                Data(profile, activePeriods, allPeriods, logs)
            }.collect { data ->
                val profile = data.profile
                val activePeriods = data.activePeriods
                val allPeriods = data.allPeriods
                val logs = data.logs
                
                if (profile == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        coachAssessment = "Please set up your user profile and goal date to receive coaching."
                    )
                    return@collect
                }

                val goalDate = profile.goalDate
                val currentPhase = CoachEngine.calculatePhase(today, goalDate)
                
                // Calculate Performance Metrics (CTL/ATL/TSB)
                // We need history for the chart, let's say 90 days back like ProgressScreen
                val chartStartDate = today.minusDays(90)
                
                // Filter logs for the relevant period up to today for chart
                val chartLogs = logs.filter { !it.date.isAfter(today) }

                // Calculate current metrics
                val currentMetrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                    logs = chartLogs,
                    targetDate = today
                )
                
                // Generate chart data
                val performanceData = generatePerformanceData(chartLogs, chartStartDate, today)

                // Generate Assessment
                val assessment = generateCoachMessage(currentPhase, currentMetrics.tsb, activePeriods)
                
                val formStatus = determineFormStatus(currentMetrics.tsb)

                _uiState.value = _uiState.value.copy(
                    currentPhase = currentPhase,
                    activeSpecialPeriods = activePeriods,
                    allSpecialPeriods = allPeriods,
                    performanceMetrics = currentMetrics,
                    coachAssessment = assessment,
                    performanceData = performanceData,
                    goalDate = goalDate,
                    isLoading = false,
                    formStatus = formStatus
                )
            }
        }
    }

    private fun generateCoachMessage(
        phase: TrainingPhase,
        tsb: Double,
        activePeriods: List<SpecialPeriod>
    ): String {
        // Priority 1: Special Period Overrides
        val injuryPeriod = activePeriods.find { it.type == SpecialPeriodType.INJURY }
        if (injuryPeriod != null) {
            return "Recovery Mode. Focus on mobility and nutrition. Avoid impact training. Physiological repair is the priority. Monitor inflammation."
        }
        
        val recoveryWeek = activePeriods.find { it.type == SpecialPeriodType.RECOVERY_WEEK }
        if (recoveryWeek != null) {
            return "Active Recovery Week. Reduce volume and intensity to allow adaptation. Focus on sleep and quality nutrition."
        }

        val holiday = activePeriods.find { it.type == SpecialPeriodType.HOLIDAY }
        if (holiday != null) {
            return "Holiday Mode. Maintain activity if possible, but enjoy the break. Don't stress about missed sessions."
        }

        // Priority 2: Critical TSB Thresholds
        if (tsb < -40) {
            return "CRITICAL: Systemic fatigue is too high (TSB < -40). High risk of injury or overtraining. Skip high-intensity sessions today."
        }
        
        // Priority 3: Sweet Spot
        if (tsb >= -30 && tsb <= -10) {
            return "Phase: ${phase.displayName}. You are in the Sweet Spot. Your body is absorbing the workload efficiently. Keep going."
        }

        // Priority 4: Phase-Specific Messaging
        return when (phase) {
            TrainingPhase.OffSeason -> "Focus: Structural Integrity. Prioritize 48h rest between heavy strength sessions for muscle protein synthesis. Build raw strength now."
            TrainingPhase.Base -> "Phase: Base. Focus on aerobic capacity, technique, and consistency. Keep intensity low and volume steady."
            TrainingPhase.Build -> "Phase: Build. Progressive overload is key. Hit your key sessions hard and respect recovery days."
            TrainingPhase.Peak -> "Phase: Peak. Specificity is highest now. Focus on race-pace intervals and simulation sessions."
            TrainingPhase.Taper -> "Phase: Taper. Race ready! Maintain sharpness with short, high-intensity sessions but reduce overall volume significantly."
            TrainingPhase.Transition -> "Phase: Transition. Rest, recover, and reset mentally. Unstructured activity only."
        }
    }

    private fun generatePerformanceData(
        logs: List<WorkoutLog>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PerformanceDataPoint> {
        val dataPoints = mutableListOf<PerformanceDataPoint>()
        var currentDate = startDate
        
        // To optimize, we could calculate these iteratively instead of full recalculation each day,
        // but re-using TrainingMetricsCalculator for each day is safer for correctness given the complex EWMA logic
        // and data size is likely small enough on mobile.
        // For a more optimized approach, we'd refactor TrainingMetricsCalculator to return a time series.
        
        while (!currentDate.isAfter(endDate)) {
            val metrics = TrainingMetricsCalculator.calculatePerformanceMetrics(logs, currentDate)
            
            val label = if (currentDate.dayOfMonth == 1 || 
                           currentDate.dayOfMonth == 15 || 
                           currentDate == startDate || 
                           currentDate == endDate) {
                shortDateFormatter.format(currentDate)
            } else {
                ""
            }
            
            dataPoints.add(PerformanceDataPoint(
                date = currentDate,
                ctl = metrics.ctl,
                atl = metrics.atl,
                tsb = metrics.tsb,
                label = label
            ))
            currentDate = currentDate.plusDays(1)
        }
        return dataPoints
    }
    
    private fun determineFormStatus(tsb: Double): FormStatus {
        return when {
            tsb > 5.0 -> FormStatus.FRESHNESS
            tsb >= -30.0 && tsb <= -10.0 -> FormStatus.OPTIMAL
            tsb < -30.0 -> FormStatus.OVERREACHING
            else -> FormStatus.OPTIMAL
        }
    }

    fun addSpecialPeriod(type: SpecialPeriodType, startDate: LocalDate, endDate: LocalDate, notes: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertSpecialPeriod(
                    SpecialPeriod(
                        type = type,
                        startDate = startDate,
                        endDate = endDate,
                        notes = notes
                    )
                )
            }
        }
    }

    fun deleteSpecialPeriod(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteSpecialPeriod(id)
            }
        }
    }
}

