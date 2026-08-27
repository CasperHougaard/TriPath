package com.tripath.ui.health.nutrition.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.repository.FoodLookupRepository
import com.tripath.data.local.repository.FoodLookupResult
import com.tripath.data.local.repository.RecoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** What the scan screen is showing. */
sealed interface BarcodeScanUiState {
    /** Camera preview is live, waiting for a decodable barcode. */
    data object Scanning : BarcodeScanUiState

    /** A barcode was decoded; the cache/network lookup is in flight. */
    data class LookingUp(val barcode: String) : BarcodeScanUiState

    /**
     * A barcode was decoded and the lookup finished — successfully or not. [kcalPer100g] and
     * [proteinPer100g] are pre-filled when known, editable either way ("manual overwrite").
     */
    data class Result(
        val barcode: String,
        val name: String?,
        val kcalPer100g: Double?,
        val proteinPer100g: Double?,
        val outcome: Outcome
    ) : BarcodeScanUiState

    /**
     * How the lookup ended.
     *
     * [OFFLINE] is kept apart from [NOT_FOUND] because they call for different things from the
     * athlete: an unknown product is worth typing in once, since the correction is saved against the
     * barcode forever, whereas a failed request is worth retrying in a minute. Reporting a dropped
     * connection as "we don't have this product" invites a pointless typing job and quietly plants a
     * manual override that will then shadow the real data.
     */
    enum class Outcome { FOUND, NOT_FOUND, OFFLINE }
}

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    private val foodLookupRepository: FoodLookupRepository,
    private val recoveryRepository: RecoveryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BarcodeScanUiState>(BarcodeScanUiState.Scanning)
    val uiState: StateFlow<BarcodeScanUiState> = _uiState

    /** One-shot confirmations for the screen's Snackbar. */
    private val _addedEvents = MutableSharedFlow<String>()
    val addedEvents: SharedFlow<String> = _addedEvents

    /** Guards against the analyzer firing again while a lookup for the same code is in flight. */
    private var lookupInFlight = false

    fun onBarcodeDetected(barcode: String) {
        if (lookupInFlight || _uiState.value !is BarcodeScanUiState.Scanning) return
        lookupInFlight = true
        _uiState.value = BarcodeScanUiState.LookingUp(barcode)
        viewModelScope.launch {
            _uiState.value = when (val result = foodLookupRepository.lookup(barcode)) {
                is FoodLookupResult.Found -> BarcodeScanUiState.Result(
                    barcode = barcode,
                    name = result.name,
                    kcalPer100g = result.kcalPer100g,
                    proteinPer100g = result.proteinPer100g,
                    outcome = BarcodeScanUiState.Outcome.FOUND
                )
                FoodLookupResult.NotFound -> BarcodeScanUiState.Result(
                    barcode = barcode,
                    name = null,
                    kcalPer100g = null,
                    proteinPer100g = null,
                    outcome = BarcodeScanUiState.Outcome.NOT_FOUND
                )
                FoodLookupResult.NetworkError -> BarcodeScanUiState.Result(
                    barcode = barcode,
                    name = null,
                    kcalPer100g = null,
                    proteinPer100g = null,
                    outcome = BarcodeScanUiState.Outcome.OFFLINE
                )
            }
            lookupInFlight = false
        }
    }

    /**
     * Logs [grams] of the current result to today, scaling [kcalPer100g]/[proteinPer100g] by
     * grams/100. If either differs from what the lookup originally returned (a correction, or a
     * value the user had to fill in by hand), it's persisted against the barcode so the next scan
     * of this product uses it.
     */
    fun onAccept(grams: Double, name: String?, kcalPer100g: Double?, proteinPer100g: Double?) {
        val current = _uiState.value as? BarcodeScanUiState.Result ?: return
        viewModelScope.launch {
            val kcal = scalePer100g(kcalPer100g, grams)
            val protein = scalePer100g(proteinPer100g, grams)
            recoveryRepository.addNutrition(LocalDate.now(), kcal, protein, null, null, name)

            // Nothing is cached from an offline scan. The values are good enough to log the entry the
            // athlete is looking at, but caching them would write a manual override — and an override
            // is never overwritten by a later lookup, so one dropped connection would permanently
            // shadow the real product data with a hand-typed guess.
            val wasEdited = kcalPer100g != current.kcalPer100g || proteinPer100g != current.proteinPer100g
            if (wasEdited && current.outcome != BarcodeScanUiState.Outcome.OFFLINE) {
                foodLookupRepository.saveOverride(current.barcode, name, kcalPer100g, proteinPer100g)
            }

            _addedEvents.emit(name?.takeIf { it.isNotBlank() }?.let { "Added $it" } ?: "Added to today")
            _uiState.value = BarcodeScanUiState.Scanning
        }
    }

    /** Discards the current result without logging anything and resumes scanning. */
    fun onClose() {
        _uiState.value = BarcodeScanUiState.Scanning
    }
}

/** Scales a per-100g value to [grams] eaten; null propagates (an unknown value stays unknown). */
fun scalePer100g(valuePer100g: Double?, grams: Double): Double? = valuePer100g?.let { it * grams / 100.0 }
