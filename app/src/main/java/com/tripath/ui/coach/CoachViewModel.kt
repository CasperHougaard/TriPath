package com.tripath.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.AnchorType
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TrainingBalance
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.domain.CoachEngine
import com.tripath.domain.PerformanceMetrics
import com.tripath.domain.TrainingMetricsCalculator
import com.tripath.domain.TrainingPhase
import com.tripath.domain.toCoachPhase
import com.tripath.domain.coach.CoachPlanGenerator
import com.tripath.domain.coach.CoachWarning
import com.tripath.domain.coach.ReadinessStatus
import com.tripath.domain.coach.TrainingRulesEngine
import com.tripath.ui.model.FormStatus
import com.tripath.ui.model.PerformanceDataPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private data class Data(
    val profile: UserProfile?,
    val activePeriods: List<SpecialPeriod>,
    val allPeriods: List<SpecialPeriod>,
    val logs: List<WorkoutLog>
)

private data class ReadinessData(
    val workoutLogs: List<WorkoutLog>,
    val wellnessLog: DailyWellnessLog?,
    val smartEnabled: Boolean,
    val todayPlans: List<TrainingPlan>,
    val profile: UserProfile?
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
    val formStatus: FormStatus = FormStatus.OPTIMAL,
    val userProfile: UserProfile? = null
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val trainingRulesEngine: TrainingRulesEngine,
    private val recoveryRepository: RecoveryRepository,
    private val preferencesManager: PreferencesManager,
    private val coachPlanGenerator: CoachPlanGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    // Readiness and alerts state flows
    private val _readinessState = MutableStateFlow<ReadinessStatus?>(null)
    val readinessState: StateFlow<ReadinessStatus?> = _readinessState.asStateFlow()
    
    private val _alertsState = MutableStateFlow<List<CoachWarning>>(emptyList())
    val alertsState: StateFlow<List<CoachWarning>> = _alertsState.asStateFlow()
    
    // Generation state flows
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _generationSuccess = MutableStateFlow<Int?>(null) // Number of plans generated
    val generationSuccess: StateFlow<Int?> = _generationSuccess.asStateFlow()
    
    val isSmartPlanningEnabled: StateFlow<Boolean> = preferencesManager.smartPlanningEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

    init {
        loadCoachData()
        loadReadinessData()
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
                        coachAssessment = "Please set up your user profile and goal date to receive coaching.",
                        userProfile = null
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
                    formStatus = formStatus,
                    userProfile = profile
                )
            }
        }
    }

    /**
     * Load readiness status and coach alerts using TrainingRulesEngine.
     */
    private fun loadReadinessData() {
        viewModelScope.launch {
            val today = LocalDate.now()
            
            combine(
                repository.getAllWorkoutLogs(),
                recoveryRepository.getWellnessLogFlow(today),
                preferencesManager.smartPlanningEnabledFlow,
                repository.getTrainingPlansByDateRange(today, today),
                repository.getUserProfile()
            ) { workoutLogs: List<WorkoutLog>, wellnessLog: DailyWellnessLog?, smartEnabled: Boolean, todayPlans: List<TrainingPlan>, profile: UserProfile? ->
                ReadinessData(
                    workoutLogs = workoutLogs,
                    wellnessLog = wellnessLog,
                    smartEnabled = smartEnabled,
                    todayPlans = todayPlans,
                    profile = profile
                )
            }.collect { data: ReadinessData ->
                val today = LocalDate.now()
                val workoutLogs = data.workoutLogs
                
                // Calculate readiness if smart planning is enabled
                if (data.smartEnabled && data.profile != null) {
                    // Get TSB from current metrics
                    val chartLogs = workoutLogs.filter { !it.date.isAfter(today) }
                    val currentMetrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                        logs = chartLogs,
                        targetDate = today
                    )
                    
                    // Extract wellness data
                    val wellness = data.wellnessLog ?: DailyWellnessLog(
                        date = today,
                        allergySeverity = AllergySeverity.NONE
                    )
                    
                    // Get sleep score from last night's sleep (yesterday's date, since sleep is dated by start time)
                    // For example: if today is Tuesday, we want Monday night's sleep (dated Monday)
                    val lastNightDate = today.minusDays(1)
                    val sleepLog = repository.getSleepLogByDate(lastNightDate)
                    val sleepScore = sleepLog?.sleepScore
                    val tsbInt = currentMetrics.tsb.roundToInt()
                    
                    // Calculate readiness
                    val readiness = trainingRulesEngine.calculateReadiness(
                        tsb = tsbInt,
                        sleepScore = sleepScore,
                        soreness = wellness.sorenessIndex,
                        mood = wellness.moodIndex,
                        allergy = wellness.allergySeverity ?: AllergySeverity.NONE
                    )
                    _readinessState.value = readiness
                    
                    // Validate daily plan
                    val currentPhase = CoachEngine.calculatePhase(today, data.profile.goalDate)
                    val coachPhase = currentPhase.toCoachPhase()
                    
                    // Get yesterday's workout
                    val yesterday = workoutLogs.filter { it.date == today.minusDays(1) }.firstOrNull()
                    
                    // Get last strength date
                    val lastStrengthDate = workoutLogs
                        .filter { it.type == WorkoutType.STRENGTH }
                        .maxOfOrNull { it.date }
                    
                    // Get recent runs (last 14 days for mechanical load comparison)
                    val fourteenDaysAgo = today.minusDays(14)
                    val recentRuns = workoutLogs.filter { log ->
                        log.type == WorkoutType.RUN &&
                        !log.date.isBefore(fourteenDaysAgo) &&
                        !log.date.isAfter(today)
                    }
                    
                    // For todayPlan, pass null as we're validating completed workouts
                    // (Option A from plan - future enhancement can validate planned workouts)
                    val warnings = trainingRulesEngine.validateDailyPlan(
                        yesterday = yesterday,
                        todayPlan = null, // Pass null as we only validate completed workouts for now
                        todayWellness = wellness,
                        lastStrengthDate = lastStrengthDate,
                        currentPhase = coachPhase,
                        recentRuns = recentRuns
                    )
                    _alertsState.value = warnings
                } else {
                    // Smart planning disabled - clear readiness and alerts
                    _readinessState.value = null
                    _alertsState.value = emptyList()
                }
            }
        }
    }

    /**
     * Generates coach assessment message based on phase, TSB, and special periods.
     * 
     * @deprecated This method uses hardcoded TSB thresholds (-40, -30, -10) that will be replaced
     * with Preferences-based logic (see PlanningSettings). Hardcoded strength spacing (48h) 
     * will also be replaced with user-configurable values. Will be replaced in Iron Brain refactor.
     */
    @Deprecated(
        message = "Hardcoded TSB thresholds and strength spacing will be replaced with Preferences-based logic",
        replaceWith = ReplaceWith("Preferences-based assessment logic (Iron Brain)")
    )
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
        // TODO: Replace hardcoded -40 threshold with Preferences-based value (Iron Brain)
        if (tsb < -40) {
            return "CRITICAL: Systemic fatigue is too high (TSB < -40). High risk of injury or overtraining. Skip high-intensity sessions today."
        }
        
        // Priority 3: Sweet Spot
        // TODO: Replace hardcoded -30 to -10 thresholds with Preferences-based values (Iron Brain)
        if (tsb >= -30 && tsb <= -10) {
            return "Phase: ${phase.displayName}. You are in the Sweet Spot. Your body is absorbing the workload efficiently. Keep going."
        }

        // Priority 4: Phase-Specific Messaging
        return when (phase) {
            TrainingPhase.OffSeason -> {
                // TODO: Replace hardcoded "48h" with Preferences.strengthSpacingHours value
                "Focus: Structural Integrity. Prioritize 48h rest between heavy strength sessions for muscle protein synthesis. Build raw strength now."
            }
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


    fun updateAvailability(
        weeklyAvailability: Map<DayOfWeek, List<WorkoutType>>,
        longTrainingDay: DayOfWeek,
        strengthDays: Int,
        trainingBalance: TrainingBalance
    ) {
        val currentProfile = _uiState.value.userProfile ?: return
        val updatedProfile = currentProfile.copy(
            weeklyAvailability = weeklyAvailability,
            longTrainingDay = longTrainingDay,
            strengthDays = strengthDays,
            trainingBalance = trainingBalance
        )
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.upsertUserProfile(updatedProfile)
            }
        }
    }

    fun updateDayAnchor(day: DayOfWeek, type: AnchorType) {
        viewModelScope.launch {
            val currentProfile = _uiState.value.userProfile
            val currentSchedule = currentProfile?.weeklySchedule?.toMutableMap() 
                ?: UserProfile.DEFAULT_WEEKLY_SCHEDULE.toMutableMap()
            currentSchedule[day] = type
            
            val updatedProfile = currentProfile?.copy(weeklySchedule = currentSchedule)
                ?: UserProfile(weeklySchedule = currentSchedule)
            
            withContext(Dispatchers.IO) {
                repository.upsertUserProfile(updatedProfile)
            }
        }
    }

    fun generateSeasonPlan(months: Int = 3) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            _generationSuccess.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    // Get current user profile
                    val profile = repository.getUserProfileOnce()
                    if (profile == null) {
                        _generationError.value = "User profile not found. Please complete your profile."
                        return@withContext
                    }
                    
                    // Get current CTL from existing metrics
                    val today = LocalDate.now()
                    val allLogs = repository.getAllWorkoutLogsOnce()
                    val currentMetrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                        logs = allLogs,
                        targetDate = today
                    )
                    val currentCtl = currentMetrics.ctl
                    
                    // Calculate the next Monday (or current Monday if today is Monday)
                    // This ensures plans always start at the beginning of a week
                    val planStartDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                    
                    // Get recent logs for cold-start validation (last 14 days)
                    val recentLogs = allLogs.filter { 
                        !it.date.isBefore(today.minusDays(14)) && 
                        !it.date.isAfter(today.minusDays(1))
                    }
                    
                    // Delete existing plans for the generation period to avoid over-population
                    // Start deletion from the plan start date (next Monday)
                    val endDate = planStartDate.plusMonths(months.toLong())
                    repository.deleteTrainingPlansByDateRange(planStartDate, endDate)
                    
                    // Generate the plan starting from next Monday
                    val generationResult = coachPlanGenerator.generateSeason(
                        startDate = planStartDate,
                        currentCtl = currentCtl,
                        months = months,
                        recentRealLogs = recentLogs
                    )
                    
                    // Handle result
                    when (generationResult) {
                        is CoachPlanGenerator.GenerationResult.Success -> {
                            val generatedPlans = generationResult.plans
                            if (generatedPlans.isNotEmpty()) {
                                repository.insertTrainingPlans(generatedPlans)
                                _generationSuccess.value = generatedPlans.size
                            } else {
                                _generationError.value = "Generation completed but produced no plans. Please check your weekly availability and training constraints."
                            }
                        }
                        is CoachPlanGenerator.GenerationResult.Failure -> {
                            val errorMessage = if (generationResult.details != null) {
                                "${generationResult.reason}\n\n${generationResult.details}"
                            } else {
                                generationResult.reason
                            }
                            _generationError.value = errorMessage
                        }
                    }
                }
            } catch (e: Exception) {
                _generationError.value = "Error generating plan: ${e.message}"
                android.util.Log.e("CoachViewModel", "Generation error", e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearGenerationError() {
        _generationError.value = null
    }

    fun clearGenerationSuccess() {
        _generationSuccess.value = null
    }
}
