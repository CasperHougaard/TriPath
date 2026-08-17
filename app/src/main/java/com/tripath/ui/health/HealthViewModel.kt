package com.tripath.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.healthconnect.BodyCompositionSyncResult
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.UserProfile
import com.tripath.domain.health.CombinedAnalysis
import com.tripath.domain.health.CombinedAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class HealthTimePeriod(val label: String, val days: Long) {
    ONE_WEEK("1W", 7),
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    ONE_YEAR("1Y", 365)
}

data class HealthUiState(
    val logs: List<BodyCompositionLog> = emptyList(),
    val filteredLogs: List<BodyCompositionLog> = emptyList(),
    val selectedPeriod: HealthTimePeriod = HealthTimePeriod.ONE_MONTH,
    val isSyncing: Boolean = false,
    val lastSyncResult: BodyCompositionSyncResult? = null,
    val latestWeight: Double? = null,
    val latestFatPercent: Double? = null,
    val latestFatMassKg: Double? = null,
    val latestLeanMass: Double? = null,
    val latestBoneMass: Double? = null,
    val weightDelta: Double? = null,
    val fatPercentDelta: Double? = null,
    val fatMassDelta: Double? = null,
    val leanMassDelta: Double? = null,
    val boneMassDelta: Double? = null
)

/** Raw streams bundled before the (heterogeneous) period-aware transform builds the analysis. */
private data class AnalysisInputs(
    val workouts: List<WorkoutLog>,
    val nutrition: List<NutritionLog>,
    val sleep: List<SleepLog>,
    val bodyComposition: List<BodyCompositionLog>,
    val profile: UserProfile?
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val recoveryRepository: RecoveryRepository,
    private val trainingRepository: TrainingRepository,
    private val healthConnectManager: HealthConnectManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(HealthTimePeriod.ONE_MONTH)
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncResult = MutableStateFlow<BodyCompositionSyncResult?>(null)

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    val allLogsForManage: StateFlow<List<BodyCompositionLog>> =
        recoveryRepository.getAllBodyCompositionLogsIncludingIgnored()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recent sleep logs (newest first) used to build the summary tile. */
    val sleepLogs: StateFlow<List<SleepLog>> =
        recoveryRepository.getSleepLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recent nutrition logs (newest first) used to build the summary tile. */
    val nutritionLogs: StateFlow<List<NutritionLog>> =
        recoveryRepository.getNutritionLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Cross-domain analysis correlating training load, nutrition, weight and sleep over the
     * currently [selectedPeriod]. Five data streams are bundled first (Flow.combine tops out at
     * five typed sources) then re-combined with the period so the same chips drive both this and
     * the body-composition view. The pure [CombinedAnalytics.build] runs off the main thread.
     */
    val analysisState: StateFlow<CombinedAnalysis> =
        combine(
            trainingRepository.getAllWorkoutLogs(),
            recoveryRepository.getNutritionLogs(),
            recoveryRepository.getSleepLogs(),
            recoveryRepository.getBodyCompositionLogs(),
            preferencesManager.userProfileFlow
        ) { workouts, nutrition, sleep, body, profile ->
            AnalysisInputs(workouts, nutrition, sleep, body, profile)
        }.combine(_selectedPeriod) { inputs, period ->
            CombinedAnalytics.build(
                allWorkouts = inputs.workouts,
                nutrition = inputs.nutrition,
                sleep = inputs.sleep,
                bodyComposition = inputs.bodyComposition,
                profile = inputs.profile,
                periodDays = period.days
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CombinedAnalysis())

    init {
        viewModelScope.launch {
            combine(
                recoveryRepository.getBodyCompositionLogs(),
                _selectedPeriod,
                _isSyncing,
                _lastSyncResult
            ) { logs, period, syncing, syncResult ->
                buildUiState(logs, period, syncing, syncResult)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectPeriod(period: HealthTimePeriod) {
        _selectedPeriod.value = period
    }

    fun toggleIgnored(id: String, isIgnored: Boolean) {
        viewModelScope.launch {
            recoveryRepository.setIgnored(id, isIgnored)
        }
    }

    /**
     * Auto-sync when the Health tab is opened, but only if the last sync was more than
     * [STALE_THRESHOLD_MILLIS] ago (or never). The DB-backed UI stays instant; this just
     * refreshes in the background. No-ops silently when permissions aren't granted.
     */
    fun refreshIfStale() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            val last = preferencesManager.getHealthLastSyncMillis()
            val now = System.currentTimeMillis()
            val isStale = last == null || (now - last) > STALE_THRESHOLD_MILLIS
            if (isStale && healthConnectManager.checkPermissions()) {
                runSync()
            }
        }
    }

    /** Manual sync triggered by the user — always runs a full sync. */
    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            runSync()
        }
    }

    private suspend fun runSync() {
        _isSyncing.value = true
        try {
            val result = healthConnectManager.syncBodyComposition()
            _lastSyncResult.value = result.getOrNull()
            healthConnectManager.syncSleep()
            preferencesManager.setHealthLastSyncMillis(System.currentTimeMillis())
        } finally {
            _isSyncing.value = false
        }
    }

    private fun buildUiState(
        logs: List<BodyCompositionLog>,
        period: HealthTimePeriod,
        syncing: Boolean,
        syncResult: BodyCompositionSyncResult?
    ): HealthUiState {
        val cutoff = LocalDate.now().minusDays(period.days)
        val cutoffMillis = cutoff.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val filtered = logs.filter { it.timestamp >= cutoffMillis }.sortedBy { it.timestamp }

        val latest = logs.firstOrNull()
        val periodStart = filtered.firstOrNull()

        fun delta(latest: Double?, start: Double?): Double? {
            if (latest == null || start == null) return null
            return latest - start
        }

        return HealthUiState(
            logs = logs,
            filteredLogs = filtered,
            selectedPeriod = period,
            isSyncing = syncing,
            lastSyncResult = syncResult,
            latestWeight = latest?.weightKg,
            latestFatPercent = latest?.bodyFatPercent,
            latestFatMassKg = latest?.fatMassKg,
            latestLeanMass = latest?.leanMassKg,
            latestBoneMass = latest?.boneMassKg,
            weightDelta = delta(latest?.weightKg, periodStart?.weightKg),
            fatPercentDelta = delta(latest?.bodyFatPercent, periodStart?.bodyFatPercent),
            fatMassDelta = delta(latest?.fatMassKg, periodStart?.fatMassKg),
            leanMassDelta = delta(latest?.leanMassKg, periodStart?.leanMassKg),
            boneMassDelta = delta(latest?.boneMassKg, periodStart?.boneMassKg)
        )
    }

    companion object {
        private const val STALE_THRESHOLD_MILLIS = 30L * 60L * 1000L // 30 minutes
    }
}
