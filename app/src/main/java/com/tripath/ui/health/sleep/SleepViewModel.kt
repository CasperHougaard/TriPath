package com.tripath.ui.health.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.ui.health.HealthTimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class SleepUiState(
    val logs: List<SleepLog> = emptyList(),
    val selectedPeriod: HealthTimePeriod = HealthTimePeriod.ONE_MONTH,
    val avgDurationMinutes: Int? = null,
    val avgScore: Int? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    recoveryRepository: RecoveryRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(HealthTimePeriod.ONE_MONTH)

    val uiState: StateFlow<SleepUiState> =
        combine(recoveryRepository.getSleepLogs(), _selectedPeriod) { logs, period ->
            val cutoff = LocalDate.now().minusDays(period.days)
            val filtered = logs.filter { it.date >= cutoff }.sortedByDescending { it.date }
            SleepUiState(
                logs = filtered,
                selectedPeriod = period,
                avgDurationMinutes = filtered.map { it.durationMinutes }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt(),
                avgScore = filtered.mapNotNull { it.sleepScore }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepUiState())

    fun selectPeriod(period: HealthTimePeriod) {
        _selectedPeriod.value = period
    }
}
