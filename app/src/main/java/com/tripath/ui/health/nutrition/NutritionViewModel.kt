package com.tripath.ui.health.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.NutritionPreset
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.NutritionMacro
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.ProjectionMode
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.domain.health.DailyNutritionTarget
import com.tripath.domain.health.DayKind
import com.tripath.domain.health.EnergyAvailabilityResult
import com.tripath.domain.health.FuelAnalytics
import com.tripath.domain.health.HealthReference
import com.tripath.domain.health.PlannedLoad
import com.tripath.ui.health.HealthTimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class NutritionUiState(
    /** The current day's row, or null when nothing has been logged today (not zero). */
    val today: NutritionLog? = null,
    val todayDate: LocalDate = LocalDate.now(),
    val logs: List<NutritionLog> = emptyList(),
    val selectedPeriod: HealthTimePeriod = HealthTimePeriod.ONE_MONTH,
    val avgCalories: Double? = null,
    val avgProtein: Double? = null,
    val avgCarbs: Double? = null,
    val avgFat: Double? = null,
    // User-configured soft targets (primary). Null when the user hasn't set them.
    val userProteinTargetG: Float? = null,
    val userCalorieTarget: Float? = null,
    // Demographics-derived fallbacks/suggestions (null when profile is incomplete).
    val proteinTarget: HealthReference.Band? = null,
    val maintenanceCalories: Double? = null,
    /** Goal- and training-load-aware target for today from [FuelAnalytics]. Null until settled. */
    val dynamicTarget: DailyNutritionTarget? = null,
    /**
     * Tomorrow's target, from planned training. Shown because it is *why* today's carbohydrate is
     * what it is — a preload the athlete cannot see the reason for is just a number that moved.
     */
    val tomorrowTarget: DailyNutritionTarget? = null,
    /** 7-day rolling energy availability — a fuelling-readiness screening signal, not a diagnosis. */
    val energyAvailability: EnergyAvailabilityResult = EnergyAvailabilityResult.UNKNOWN,
    /** Needed against eaten, day by day over [selectedPeriod]. Oldest first. */
    val fuelHistory: List<FuelHistoryDay> = emptyList()
) {
    /**
     * Effective protein target for the progress bar: the user's value, else the load-aware target,
     * else the demographics-derived minimum.
     */
    val effectiveProteinTargetG: Double?
        get() = userProteinTargetG?.toDouble() ?: dynamicTarget?.proteinG ?: proteinTarget?.min

    /** Effective calorie target: the user's value, else the load-aware target, else maintenance. */
    val effectiveCalorieTarget: Double?
        get() = userCalorieTarget?.toDouble() ?: dynamicTarget?.kcal ?: maintenanceCalories
}

/**
 * One day of "what the work asked for" against "what was actually eaten".
 *
 * Every figure is nullable and means it: a day with no food logged is a **gap**, never a zero. The
 * difference matters more here than anywhere else in the app — a chart that draws an unlogged day as
 * 0 kcal invents a starvation day and then averages it in.
 *
 * [tss] is the day's training load, carried so the chart can show *why* a day needed what it did.
 */
data class FuelHistoryDay(
    val date: LocalDate,
    val dayKind: DayKind?,
    val neededKcal: Double?,
    val eatenKcal: Double?,
    val neededProteinG: Double?,
    val eatenProteinG: Double?,
    /** Prescribed only — carbohydrate is not logged, so there is no "eaten" counterpart. */
    val neededCarbsG: Double?,
    val tss: Int,
    /** kcal per kg fat-free mass. A screening signal — see [com.tripath.domain.health.EnergyAvailability]. */
    val energyAvailability: Double?,
    /** The day's sessions, already readable ("Bike 90 min") — the reason the day needed what it did. */
    val activities: List<String> = emptyList()
)

private fun WorkoutType.readable(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Progress fraction for a soft target bar: clamped to [0, 1] and never signals failure.
 * Returns 0 when there is no positive target. Values over the target read as full (1f).
 */
fun softProgressFraction(value: Double, target: Double?): Float =
    if (target != null && target > 0) (value / target).coerceIn(0.0, 1.0).toFloat() else 0f

/**
 * Shortest history fed to [FuelAnalytics.build], whatever period is on screen — enough for its
 * adaptive correction to settle. Longer periods extend it so the history chart can cover them.
 */
private const val MIN_FUEL_WINDOW_DAYS = 21L

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val recoveryRepository: RecoveryRepository,
    private val trainingRepository: TrainingRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val _selectedPeriod = MutableStateFlow(HealthTimePeriod.ONE_MONTH)
    private val _selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Ledger id of the most recent add, so the Snackbar can undo exactly that one. */
    private var lastEntryId: Long? = null

    /** Bundled once so the seven sources below fit within [combine]'s five-argument overload. */
    private data class FuelInputs(
        val workouts: List<WorkoutLog>,
        val bodyLogs: List<BodyCompositionLog>,
        val dailyActivity: List<DailyActivityLog>,
        val profile: UserProfile?,
        val plans: List<TrainingPlan>
    )

    val uiState: StateFlow<NutritionUiState> = combine(
        recoveryRepository.getNutritionLogs(),
        _selectedPeriod,
        combine(
            trainingRepository.getAllWorkoutLogs(),
            recoveryRepository.getBodyCompositionLogs(),
            recoveryRepository.getDailyActivityLogs(),
            preferencesManager.userProfileFlow,
            trainingRepository.getAllTrainingPlans()
        ) { workouts, bodyLogs, dailyActivity, profile, plans ->
            FuelInputs(workouts, bodyLogs, dailyActivity, profile, plans)
        }
    ) { logs, period, fuelInputs ->
        val cutoff = today.minusDays(period.days)
        val filtered = logs.filter { it.date >= cutoff }.sortedByDescending { it.date }
        fun avg(selector: (NutritionLog) -> Double?): Double? =
            filtered.mapNotNull(selector).takeIf { it.isNotEmpty() }?.average()

        val bodyLogs = fuelInputs.bodyLogs
        val profile = fuelInputs.profile
        val weightKg = bodyLogs.firstOrNull { it.weightKg != null }?.weightKg

        // Long enough for AdaptiveExpenditure's correction ratio to settle, and long enough to cover
        // whatever period the history chart is showing — a 1Y chart of what each day needed has to
        // have had each of those days modelled.
        val windowStart = today.minusDays(maxOf(period.days, MIN_FUEL_WINDOW_DAYS))
        val weightByDate: Map<LocalDate, Double> = bodyLogs
            .filter { it.weightKg != null }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayLogs) -> dayLogs.maxByOrNull { it.timestamp }!!.weightKg!! }
        val nutritionByDate = logs.associate { it.date to (it.energyKcal to it.proteinG) }
        // Ignored sessions are excluded here as they are everywhere else. Counting a session the
        // athlete deliberately struck out would inflate the expenditure the target is sized from.
        val workouts = fuelInputs.workouts.filter { !it.isIgnored }
        // What is planned from today on, which is what lets today's carbohydrate account for
        // tomorrow's long session rather than being sized as though tomorrow were a rest day.
        val plannedLoad = PlannedLoad.forHorizon(
            mode = profile?.effectiveProjectionMode ?: ProjectionMode.DEFAULT,
            completedWorkouts = workouts,
            plans = fuelInputs.plans,
            today = today,
            horizonEnd = FuelAnalytics.horizonEnd(today)
        )
        val fuel = FuelAnalytics.build(
            workouts = workouts,
            nutritionByDate = nutritionByDate,
            bodyComposition = bodyLogs,
            dailyActivity = fuelInputs.dailyActivity,
            profile = profile,
            plannedTssByDate = plannedLoad.tssByDate,
            windowStart = windowStart,
            today = today,
            weightByDate = weightByDate,
            plannedMinutesByDate = plannedLoad.minutesByDate
        )

        // Needed against eaten, per day. Assembled here rather than in the fuel model because two of
        // the four series are raw log values: the model knows what a day *required*, the log knows
        // what went in, and joining them is the whole point of the chart.
        val workoutsByDate = workouts.groupBy { it.date }
        val tssByDate = workoutsByDate
            .mapValues { (_, dayLogs) -> dayLogs.sumOf { it.computedTSS ?: 0 } }
        val proteinByDate = logs.associate { it.date to it.proteinG }
        val fuelHistory = fuel.days
            .filter { !it.date.isBefore(cutoff) }
            .map { day ->
                FuelHistoryDay(
                    date = day.date,
                    dayKind = day.target?.dayKind,
                    neededKcal = day.target?.kcal,
                    eatenKcal = day.intakeKcal,
                    neededProteinG = day.target?.proteinG,
                    eatenProteinG = proteinByDate[day.date],
                    neededCarbsG = day.target?.carbsG,
                    tss = tssByDate[day.date] ?: 0,
                    energyAvailability = day.energyAvailability.kcalPerKgFfm,
                    activities = workoutsByDate[day.date]
                        .orEmpty()
                        .map { "${it.type.readable()} ${it.durationMinutes} min" }
                )
            }

        NutritionUiState(
            today = logs.firstOrNull { it.date == today },
            todayDate = today,
            logs = filtered,
            selectedPeriod = period,
            avgCalories = avg { it.energyKcal },
            avgProtein = avg { it.proteinG },
            avgCarbs = avg { it.carbsG },
            avgFat = avg { it.fatG },
            userProteinTargetG = profile?.proteinTargetG,
            userCalorieTarget = profile?.calorieTarget,
            proteinTarget = HealthReference.proteinTargetGrams(weightKg),
            maintenanceCalories = HealthReference.maintenanceCalories(
                sex = profile?.biologicalSex,
                age = profile?.ageOn(),
                weightKg = weightKg,
                heightCm = profile?.heightCm
            ),
            dynamicTarget = fuel.today?.target,
            tomorrowTarget = fuel.tomorrow?.target,
            energyAvailability = fuel.rollingEnergyAvailability,
            fuelHistory = fuelHistory
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionUiState())

    /** The day whose itemised log is open, or null when no day sheet is showing. */
    val selectedDay: StateFlow<LocalDate?> = _selectedDay

    /** Entries of the open day, newest first; empty for days logged before the ledger existed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayEntries: StateFlow<List<NutritionEntry>> = _selectedDay
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyList()) else recoveryRepository.getNutritionEntries(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Saved presets for the library, alphabetical by label. */
    val presets: StateFlow<List<NutritionPreset>> = recoveryRepository.getNutritionPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openDay(date: LocalDate) {
        _selectedDay.value = date
    }

    fun closeDay() {
        _selectedDay.value = null
    }

    fun selectPeriod(period: HealthTimePeriod) {
        _selectedPeriod.value = period
    }

    /** Quick-add a single macro (or kcal) to today, atomically. */
    fun quickAdd(macro: NutritionMacro, grams: Double) {
        viewModelScope.launch {
            lastEntryId = recoveryRepository.quickAddMacro(today, macro, grams)
        }
    }

    /**
     * Custom add: increment several of [date]'s fields at once (defaults to today, so a past day
     * can be backfilled). Null args leave that field alone.
     */
    fun addCustom(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?, label: String? = null, date: LocalDate = today) {
        viewModelScope.launch {
            lastEntryId = recoveryRepository.addNutrition(date, kcal, protein, carbs, fat, label)
        }
    }

    /** Save a label+macro combination to the library, for re-use without retyping it. */
    fun saveAsPreset(label: String, kcal: Double?, protein: Double?) {
        viewModelScope.launch {
            recoveryRepository.saveNutritionPreset(label, kcal, protein)
        }
    }

    /** Apply a saved preset to today, the same way a custom add would. */
    fun applyPreset(preset: NutritionPreset) {
        viewModelScope.launch {
            lastEntryId = recoveryRepository.addNutrition(
                today, preset.kcal, preset.proteinG, preset.carbsG, preset.fatG, preset.label
            )
        }
    }

    fun deletePreset(preset: NutritionPreset) {
        viewModelScope.launch {
            recoveryRepository.deleteNutritionPreset(preset)
        }
    }

    /** Reverse one logged add, subtracting exactly its amounts from the day it belongs to. */
    fun undoEntry(entryId: Long) {
        viewModelScope.launch {
            recoveryRepository.undoNutritionEntry(entryId)
            if (lastEntryId == entryId) lastEntryId = null
        }
    }

    /**
     * Undo the most recent add made in this session — what the "Added 100 kcal" Snackbar reverses.
     * Deletes the ledger entry rather than adding a negative one, so the day log stays clean.
     */
    fun undoLastEntry() {
        lastEntryId?.let(::undoEntry)
    }

    /** Edit a day to absolute values (blanks clear the field), including the creatine flag. */
    fun editDay(date: LocalDate, kcal: Double?, protein: Double?, carbs: Double?, fat: Double?, creatineTaken: Boolean) {
        viewModelScope.launch {
            recoveryRepository.setNutritionDay(date, kcal, protein, carbs, fat, creatineTaken)
        }
    }

    fun setCreatine(date: LocalDate, taken: Boolean) {
        viewModelScope.launch {
            recoveryRepository.setCreatine(date, taken)
        }
    }

    fun clearDay(date: LocalDate) {
        viewModelScope.launch {
            recoveryRepository.clearNutritionDay(date)
        }
    }

    /** Persist the (soft) nutrition targets, preserving the rest of the profile. */
    fun saveTargets(proteinTargetG: Float?, calorieTarget: Float?) {
        viewModelScope.launch {
            val current = preferencesManager.getUserProfile() ?: UserProfile()
            preferencesManager.saveUserProfile(
                current.copy(proteinTargetG = proteinTargetG, calorieTarget = calorieTarget)
            )
        }
    }
}
