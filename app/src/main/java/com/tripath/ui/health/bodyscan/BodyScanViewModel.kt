package com.tripath.ui.health.bodyscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.model.BiologicalSex
import com.tripath.domain.health.BodyCompositionAnalytics
import com.tripath.domain.health.HealthReference
import com.tripath.ui.health.HealthTimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class BodyScanUiState(
    val logs: List<BodyCompositionLog> = emptyList(),
    val filteredLogs: List<BodyCompositionLog> = emptyList(),
    val selectedPeriod: HealthTimePeriod = HealthTimePeriod.ONE_MONTH,
    val latestWeight: Double? = null,
    val latestFatPercent: Double? = null,
    val latestFatMassKg: Double? = null,
    val latestLeanMass: Double? = null,
    val latestBoneMass: Double? = null,
    val weightDelta: Double? = null,
    val fatPercentDelta: Double? = null,
    val leanMassDelta: Double? = null,
    val boneMassDelta: Double? = null,
    // Demographics-derived reference info (null when profile is incomplete).
    val sex: BiologicalSex? = null,
    val heightCm: Int? = null,
    val bodyFatBand: HealthReference.Band? = null,
    val bodyFatCategory: String? = null,
    val latestBmi: Double? = null,
    val bmiCategory: String? = null,
    // Robust, derived analytics for the selected period (null when there is no data).
    val stats: BodyCompositionAnalytics.BodyCompositionStats? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class BodyScanViewModel @Inject constructor(
    private val recoveryRepository: RecoveryRepository,
    private val preferencesManager: PreferencesManager,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(HealthTimePeriod.ONE_MONTH)
    private val _isSyncing = MutableStateFlow(false)

    val uiState: StateFlow<BodyScanUiState> = combine(
        recoveryRepository.getBodyCompositionLogs(),
        _selectedPeriod,
        preferencesManager.userProfileFlow,
        _isSyncing
    ) { logs, period, profile, isSyncing ->
        val cutoff = LocalDate.now().minusDays(period.days)
        val cutoffMillis = cutoff.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val filtered = logs.filter { it.timestamp >= cutoffMillis }.sortedBy { it.timestamp }

        val latest = logs.firstOrNull()
        val periodStart = filtered.firstOrNull()

        fun delta(now: Double?, start: Double?): Double? =
            if (now == null || start == null) null else now - start

        val sex = profile?.biologicalSex
        val age = profile?.ageOn()
        val heightCm = profile?.heightCm
        val latestFat = latest?.bodyFatPercent
        val bmi = HealthReference.bmi(latest?.weightKg, heightCm)

        val stats = if (filtered.isEmpty()) null else {
            BodyCompositionAnalytics.analyze(
                periodLogs = filtered,
                periodDays = period.days,
                sex = sex,
                age = age,
                heightCm = heightCm
            )
        }

        BodyScanUiState(
            logs = logs,
            filteredLogs = filtered,
            selectedPeriod = period,
            latestWeight = latest?.weightKg,
            latestFatPercent = latestFat,
            latestFatMassKg = latest?.let { fatMassOf(it) },
            latestLeanMass = latest?.leanMassKg,
            latestBoneMass = latest?.boneMassKg,
            weightDelta = delta(latest?.weightKg, periodStart?.weightKg),
            fatPercentDelta = delta(latest?.bodyFatPercent, periodStart?.bodyFatPercent),
            leanMassDelta = delta(latest?.leanMassKg, periodStart?.leanMassKg),
            boneMassDelta = delta(latest?.boneMassKg, periodStart?.boneMassKg),
            sex = sex,
            heightCm = heightCm,
            bodyFatBand = HealthReference.bodyFatHealthyBand(sex, age),
            bodyFatCategory = latestFat?.let { HealthReference.bodyFatCategory(sex, it) },
            latestBmi = bmi,
            bmiCategory = bmi?.let { HealthReference.bmiCategory(it) },
            stats = stats,
            isSyncing = isSyncing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BodyScanUiState())

    fun selectPeriod(period: HealthTimePeriod) {
        _selectedPeriod.value = period
    }

    /** Manual sync from Health Connect (body composition, plus sleep for the tiles). */
    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                healthConnectManager.syncBodyComposition()
                healthConnectManager.syncSleep()
                preferencesManager.setHealthLastSyncMillis(System.currentTimeMillis())
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun fatMassOf(log: BodyCompositionLog): Double? {
        val w = log.weightKg ?: return null
        val fat = log.bodyFatPercent ?: return null
        return w * fat / 100.0
    }
}
