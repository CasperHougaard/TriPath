package com.tripath.ui.health.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.NutritionMacro
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.model.UserProfile
import com.tripath.domain.health.HealthReference
import com.tripath.ui.health.HealthTimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val maintenanceCalories: Double? = null
) {
    /** Effective protein target for the progress bar: user's value, else the derived minimum. */
    val effectiveProteinTargetG: Double?
        get() = userProteinTargetG?.toDouble() ?: proteinTarget?.min

    /** Effective calorie target: user's value, else the derived maintenance estimate. */
    val effectiveCalorieTarget: Double?
        get() = userCalorieTarget?.toDouble() ?: maintenanceCalories
}

/**
 * Progress fraction for a soft target bar: clamped to [0, 1] and never signals failure.
 * Returns 0 when there is no positive target. Values over the target read as full (1f).
 */
fun softProgressFraction(value: Double, target: Double?): Float =
    if (target != null && target > 0) (value / target).coerceIn(0.0, 1.0).toFloat() else 0f

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val recoveryRepository: RecoveryRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val _selectedPeriod = MutableStateFlow(HealthTimePeriod.ONE_MONTH)
    private val _selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Ledger id of the most recent add, so the Snackbar can undo exactly that one. */
    private var lastEntryId: Long? = null

    val uiState: StateFlow<NutritionUiState> = combine(
        recoveryRepository.getNutritionLogs(),
        _selectedPeriod,
        recoveryRepository.getBodyCompositionLogs(),
        preferencesManager.userProfileFlow
    ) { logs, period, bodyLogs, profile ->
        val cutoff = today.minusDays(period.days)
        val filtered = logs.filter { it.date >= cutoff }.sortedByDescending { it.date }
        fun avg(selector: (NutritionLog) -> Double?): Double? =
            filtered.mapNotNull(selector).takeIf { it.isNotEmpty() }?.average()

        val weightKg = bodyLogs.firstOrNull { it.weightKg != null }?.weightKg
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
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionUiState())

    /** The day whose itemised log is open, or null when no day sheet is showing. */
    val selectedDay: StateFlow<LocalDate?> = _selectedDay

    /** Entries of the open day, newest first; empty for days logged before the ledger existed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayEntries: StateFlow<List<NutritionEntry>> = _selectedDay
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyList()) else recoveryRepository.getNutritionEntries(date)
        }
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

    /** Custom add: increment several of today's fields at once. Null args leave that field alone. */
    fun addCustom(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?, label: String? = null) {
        viewModelScope.launch {
            lastEntryId = recoveryRepository.addNutrition(today, kcal, protein, carbs, fat, label)
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
