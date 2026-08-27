package com.tripath.ui.coach.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.domain.strain.LiftContributionDay
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.ReadinessService
import com.tripath.domain.strain.StrainSource
import com.tripath.domain.strain.StrainTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReadinessDetailUiState(
    val isLoading: Boolean = true,
    val assessment: ReadinessAssessment? = null,
    val liftContributions: List<LiftContributionDay> = emptyList(),
    /**
     * The full window, always the longest one the user can select. The window chips slice this
     * client-side rather than reloading — see [StrainTrend.lastDays].
     */
    val strainTrend: StrainTrend = StrainTrend(),
    /** Which data sources everything strain-derived on this screen was built from. */
    val source: StrainSource = StrainSource.BOTH
)

/**
 * Backs the "what went into this" screen behind the Coach tab's freshness card — a plain read of
 * [ReadinessService], the same source the card itself and the LiftPath bridge use.
 *
 * The assessment lands first and the trend follows in its own launch: the trend runs the strain
 * model ninety times and there is no reason to make today's numbers wait behind it.
 *
 * ## Why the source results are cached
 * Switching the source re-reads four tables and re-runs the ninety-day model. Toggling back and
 * forth to compare two sources is the entire point of the control, so paying that twice for the same
 * answer would make the comparison feel broken. Nothing here is persisted and the screen is
 * short-lived, so a plain map is the whole cache.
 */
@HiltViewModel
class ReadinessDetailViewModel @Inject constructor(
    private val readinessService: ReadinessService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadinessDetailUiState())
    val uiState: StateFlow<ReadinessDetailUiState> = _uiState.asStateFlow()

    private val today = LocalDate.now()
    private val assessments = mutableMapOf<StrainSource, ReadinessAssessment?>()
    private val trends = mutableMapOf<StrainSource, StrainTrend>()

    init {
        load(StrainSource.BOTH)
        viewModelScope.launch {
            val liftContributions = runCatching { readinessService.recentLiftContributions(today) }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(liftContributions = liftContributions) }
        }
    }

    fun onSourceSelected(source: StrainSource) {
        if (source == _uiState.value.source) return
        load(source)
    }

    private fun load(source: StrainSource) {
        // Applied immediately so the chips respond even when the model work behind them does not.
        // The outgoing source's numbers are dropped rather than left on screen under the new label:
        // a score belongs to the source it was computed from.
        val cached = assessments.containsKey(source)
        _uiState.update {
            it.copy(
                source = source,
                isLoading = !cached,
                assessment = if (cached) assessments[source] else null,
                strainTrend = trends[source] ?: StrainTrend()
            )
        }

        viewModelScope.launch {
            // Guarded, and `isLoading` cleared in every outcome. An unguarded failure here left the
            // screen spinning forever, which is a worse answer than "no readiness data yet".
            val assessment = assessments.getOrPut(source) {
                runCatching { readinessService.currentReadiness(today, source) }
                    .onFailure { Log.w(TAG, "readiness unavailable", it) }
                    .getOrNull()
            }
            _uiState.update {
                if (it.source != source) it
                else it.copy(isLoading = false, assessment = assessment)
            }
        }
        viewModelScope.launch {
            val trend = trends.getOrPut(source) {
                runCatching { readinessService.strainTrend(today = today, source = source) }
                    .onFailure { Log.w(TAG, "strain trend unavailable", it) }
                    .getOrDefault(StrainTrend())
            }
            // A slow load for a source the user has already switched away from must not overwrite
            // the one they are looking at.
            _uiState.update { if (it.source != source) it else it.copy(strainTrend = trend) }
        }
    }

    // Both launches write to the same state, so they update it rather than reading, copying and
    // overwriting — whichever finishes second would otherwise discard the other's result.
    private companion object {
        const val TAG = "ReadinessDetailVM"
    }
}
