package com.tripath.ui.coach

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
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
import com.tripath.domain.coach.AutoPlannerGenerator
import com.tripath.domain.coach.CoachWarning
import com.tripath.domain.coach.PlannedStrainAdvisor
import com.tripath.domain.coach.ReadinessStatus
import com.tripath.domain.health.EnergyAvailabilityBand
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.ReadinessService
import com.tripath.domain.coach.TrainingRulesEngine
import com.tripath.domain.running.RunningGoal
import com.tripath.ui.model.FormStatus
import com.tripath.ui.model.PerformanceDataPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    val logs: List<WorkoutLog>,
    val plans: List<TrainingPlan>
)

private data class ReadinessData(
    val workoutLogs: List<WorkoutLog>,
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
    private val preferencesManager: PreferencesManager,
    private val autoPlannerGenerator: AutoPlannerGenerator,
    private val readinessService: ReadinessService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    // Readiness and alerts state flows
    private val _readinessState = MutableStateFlow<ReadinessStatus?>(null)
    val readinessState: StateFlow<ReadinessStatus?> = _readinessState.asStateFlow()

    /**
     * The per-channel readiness assessment — score, ranked drivers, and which disciplines are a good
     * idea today. This is the model TriPath now owns and hands to LiftPath; [readinessState] is the
     * older single-number view kept until the Coach card is rebuilt around this.
     */
    private val _assessmentState = MutableStateFlow<ReadinessAssessment?>(null)
    val assessmentState: StateFlow<ReadinessAssessment?> = _assessmentState.asStateFlow()
    
    private val _alertsState = MutableStateFlow<List<CoachWarning>>(emptyList())
    val alertsState: StateFlow<List<CoachWarning>> = _alertsState.asStateFlow()
    
    // Generation state flows
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    private val _generationSuccess = MutableStateFlow<Int?>(null) // Number of plans generated
    val generationSuccess: StateFlow<Int?> = _generationSuccess.asStateFlow()
    
    val isSmartPlanningEnabled: StateFlow<Boolean> = preferencesManager.autoPlannerEnabledFlow
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
                repository.getAllWorkoutLogs(), // We need logs for metrics
                repository.getAllTrainingPlans() // Planned workouts drive the forecast
            ) { profile, activePeriods, allPeriods, logs, plans ->
                Data(profile, activePeriods, allPeriods, logs, plans)
            }.collect { data ->
                val profile = data.profile
                val activePeriods = data.activePeriods
                val allPeriods = data.allPeriods
                val logs = data.logs
                val plans = data.plans

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
                // Show 4 months of history for the chart.
                val chartStartDate = today.minusMonths(4)
                
                // Filter logs for the relevant period up to today for chart
                val chartLogs = logs.filter { !it.date.isAfter(today) }

                // Calculate current metrics (actuals only, up to today)
                val currentMetrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                    logs = chartLogs,
                    targetDate = today
                )

                // Future planned workouts (after today) drive the forecast portion of the curve.
                val futurePlans = plans.filter { it.date.isAfter(today) }
                val plannedTssByDate = futurePlans
                    .groupBy { it.date }
                    .mapValues { (_, dayPlans) -> dayPlans.sumOf { it.plannedTSS } }
                // Completed workouts logged on a future date (e.g. manually marked done ahead of time).
                val furthestFutureLog = logs.filter { it.date.isAfter(today) }.maxOfOrNull { it.date }
                // Always project 2 months into the future; extend further if plans or future logs go beyond that.
                val projectionEnd = maxOf(
                    today.plusMonths(2),
                    futurePlans.maxOfOrNull { it.date } ?: today,
                    furthestFutureLog ?: today
                )

                // Generate chart data (actuals up to today, projection to projectionEnd).
                // Pass full logs so future-dated completed workouts are counted on the forecast.
                val performanceData = generatePerformanceData(
                    logs = logs,
                    plannedTssByDate = plannedTssByDate,
                    startDate = chartStartDate,
                    today = today,
                    endDate = projectionEnd
                )

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
                preferencesManager.autoPlannerEnabledFlow,
                repository.getTrainingPlansByDateRange(today, today),
                repository.getUserProfile()
            ) { workoutLogs: List<WorkoutLog>, smartEnabled: Boolean, todayPlans: List<TrainingPlan>, profile: UserProfile? ->
                ReadinessData(
                    workoutLogs = workoutLogs,
                    smartEnabled = smartEnabled,
                    todayPlans = todayPlans,
                    profile = profile
                )
            }.collect { data: ReadinessData ->
                val today = LocalDate.now()
                val workoutLogs = data.workoutLogs

                if (data.smartEnabled && data.profile != null) {
                    val chartLogs = workoutLogs.filter { !it.date.isAfter(today) }
                    val currentMetrics = TrainingMetricsCalculator.calculatePerformanceMetrics(
                        logs = chartLogs,
                        targetDate = today
                    )

                    val defaultWellness = DailyWellnessLog(
                        date = today,
                        allergySeverity = AllergySeverity.NONE
                    )

                    val lastNightDate = today.minusDays(1)
                    val sleepLog = repository.getSleepLogByDate(lastNightDate)
                    val sleepScore = sleepLog?.sleepScore
                    val tsbInt = currentMetrics.tsb.roundToInt()

                    val readiness = trainingRulesEngine.calculateReadiness(
                        tsb = tsbInt,
                        sleepScore = sleepScore,
                        soreness = null,
                        mood = null,
                        allergy = AllergySeverity.NONE
                    )
                    _readinessState.value = readiness

                    // The full per-channel assessment, which is what the planner rules and LiftPath
                    // both read. Kept alongside the legacy score rather than replacing it inline so
                    // the Coach card can adopt it independently of this load path.
                    val assessment = runCatching { readinessService.currentReadiness(today) }
                        .onFailure { Log.w("CoachViewModel", "Readiness assessment unavailable", it) }
                        .getOrNull()
                    _assessmentState.value = assessment

                    val currentPhase = CoachEngine.calculatePhase(today, data.profile.goalDate)
                    val coachPhase = currentPhase.toCoachPhase()
                    val yesterday = workoutLogs.filter { it.date == today.minusDays(1) }.firstOrNull()
                    val lastStrengthDate = workoutLogs
                        .filter { it.type == WorkoutType.STRENGTH }
                        .maxOfOrNull { it.date }
                    val fourteenDaysAgo = today.minusDays(14)
                    val recentRuns = workoutLogs.filter { log ->
                        log.type == WorkoutType.RUN &&
                        !log.date.isBefore(fourteenDaysAgo) &&
                        !log.date.isAfter(today)
                    }

                    val warnings = trainingRulesEngine.validateDailyPlan(
                        yesterday = yesterday,
                        todayPlan = null,
                        todayWellness = defaultWellness,
                        lastStrengthDate = lastStrengthDate,
                        currentPhase = coachPhase,
                        recentRuns = recentRuns,
                        readiness = assessment,
                        // Carried on the assessment rather than rebuilt here: the fuel model has
                        // already run once inside the readiness service and running it twice is how
                        // two parts of the same screen end up quoting different numbers.
                        energyAvailability = assessment?.energyAvailability
                            ?: EnergyAvailabilityBand.UNKNOWN
                    )

                    // Where the coming week stacks two sessions on the same tissue. Advisory, and
                    // about the *schedule* rather than about today — which is why it is appended
                    // here rather than folded into the day's rules.
                    val planWarnings = runCatching { readinessService.planConflicts(today) }
                        .onFailure { Log.w("CoachViewModel", "Plan conflict check unavailable", it) }
                        .getOrDefault(emptyList())
                        .let { PlannedStrainAdvisor.asWarnings(it) }

                    _alertsState.value = warnings + planWarnings
                } else {
                    _readinessState.value = null
                    _assessmentState.value = null
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

    /**
     * Builds the Performance Pulse chart series. Actuals (completed workouts) cover
     * [startDate]..[today]; the curve is then projected forward to [endDate] using
     * [plannedTssByDate]. Points after [today] are flagged as projected.
     */
    private fun generatePerformanceData(
        logs: List<WorkoutLog>,
        plannedTssByDate: Map<LocalDate, Int>,
        startDate: LocalDate,
        today: LocalDate,
        endDate: LocalDate
    ): List<PerformanceDataPoint> {
        val series = TrainingMetricsCalculator.calculatePerformanceSeries(
            logs = logs,
            plannedTssByDate = plannedTssByDate,
            seriesStart = startDate,
            seriesEnd = endDate,
            actualUntil = today
        )

        // When planned workouts run out before the end of the visible frame, hold the
        // curve flat from the last planned day instead of letting CTL/ATL decay toward
        // zero on empty days. With no future plans at all, hold flat from today.
        val lastPlannedDate = plannedTssByDate.keys.maxOrNull() ?: today
        val flatlineFrom = if (lastPlannedDate.isBefore(endDate)) lastPlannedDate else null
        val flatlineMetrics = flatlineFrom?.let { from -> series.firstOrNull { it.first == from }?.second }

        return series.map { (date, metrics) ->
            val effectiveMetrics = if (flatlineFrom != null && flatlineMetrics != null && date.isAfter(flatlineFrom)) {
                flatlineMetrics
            } else {
                metrics
            }

            val label = if (date.dayOfMonth == 1 ||
                           date.dayOfMonth == 15 ||
                           date == startDate ||
                           date == endDate) {
                shortDateFormatter.format(date)
            } else {
                ""
            }

            PerformanceDataPoint(
                date = date,
                ctl = effectiveMetrics.ctl,
                atl = effectiveMetrics.atl,
                tsb = effectiveMetrics.tsb,
                label = label,
                isProjected = date.isAfter(today)
            )
        }
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

    fun generateSeasonPlan(months: Int = 3, runningGoal: RunningGoal? = null) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            _generationSuccess.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    val effectiveRunningGoal = runningGoal ?: preferencesManager.getActiveRunningGoal()

                    // Get current user profile
                    val profile = repository.getUserProfileOnce()
                    if (profile == null && effectiveRunningGoal == null) {
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
                    
                    // The first counting week always starts on a Monday so weekly progression and
                    // goal-date math stay week-aligned. Training itself can begin earlier than this
                    // (see earliestSessionDate below) via a non-counting partial lead-in week.
                    val planStartDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                    
                    // Get recent logs for cold-start validation (last 14 days)
                    val recentLogs = allLogs.filter { 
                        !it.date.isBefore(today.minusDays(14)) && 
                        !it.date.isAfter(today.minusDays(1))
                    }
                    
                    // Delete ALL existing training plans before generating new ones
                    // This ensures clean slate: removes plans before, during, and after the new plan scope
                    repository.deleteAllTrainingPlans()
                    
                    // Progression counting starts next Monday, but training (strength cadence and a
                    // partial run lead-in week) may begin as early as today.
                    val generationResult = autoPlannerGenerator.generateSeason(
                        startDate = planStartDate,
                        currentCtl = currentCtl,
                        months = months,
                        recentRealLogs = recentLogs,
                        runningGoal = effectiveRunningGoal,
                        earliestSessionDate = today
                    )
                    
                    // Handle result
                    when (generationResult) {
                        is AutoPlannerGenerator.GenerationResult.Success -> {
                            val generatedPlans = generationResult.plans
                            if (generatedPlans.isNotEmpty()) {
                                repository.insertTrainingPlans(generatedPlans)
                                _generationSuccess.value = generatedPlans.size
                            } else {
                                _generationError.value = "Generation completed but produced no plans. Please check your weekly availability and training constraints."
                            }
                        }
                        is AutoPlannerGenerator.GenerationResult.Failure -> {
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
