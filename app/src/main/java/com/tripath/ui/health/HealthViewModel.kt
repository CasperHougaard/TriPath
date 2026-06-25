package com.tripath.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.healthconnect.BodyCompositionSyncResult
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.repository.RecoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val latestLeanMass: Double? = null,
    val latestBoneMass: Double? = null,
    val weightDelta: Double? = null,
    val fatPercentDelta: Double? = null,
    val leanMassDelta: Double? = null,
    val boneMassDelta: Double? = null
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val recoveryRepository: RecoveryRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(HealthTimePeriod.ONE_MONTH)
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncResult = MutableStateFlow<BodyCompositionSyncResult?>(null)

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

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

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = healthConnectManager.syncBodyComposition()
            _lastSyncResult.value = result.getOrNull()
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
            latestLeanMass = latest?.leanMassKg,
            latestBoneMass = latest?.boneMassKg,
            weightDelta = delta(latest?.weightKg, periodStart?.weightKg),
            fatPercentDelta = delta(latest?.bodyFatPercent, periodStart?.bodyFatPercent),
            leanMassDelta = delta(latest?.leanMassKg, periodStart?.leanMassKg),
            boneMassDelta = delta(latest?.boneMassKg, periodStart?.boneMassKg)
        )
    }
}
