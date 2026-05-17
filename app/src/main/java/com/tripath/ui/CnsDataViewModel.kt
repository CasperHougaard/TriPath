package com.tripath.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.entities.RawWorkoutData
// TODO: CNS/importer feature incomplete: CnsDataEntry missing
// import com.tripath.data.local.importer.CnsDataEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Data class representing a CNS entry with workout information for display.
 */
data class CnsDataDisplayItem(
    val date: LocalDate,
    val cnsScore: Int,
    val connectId: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val rawCnsJson: String
)

data class CnsDataUiState(
    val cnsDataItems: List<CnsDataDisplayItem> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * ViewModel for managing CNS data display.
 * Fetches all CNS data from RawWorkoutData and prepares it for display.
 */
@HiltViewModel
class CnsDataViewModel @Inject constructor(
    private val rawWorkoutDataDao: RawWorkoutDataDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CnsDataUiState())
    val uiState: StateFlow<CnsDataUiState> = _uiState.asStateFlow()

    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    init {
        loadCnsData()
    }

    /**
     * Load all CNS data from the database.
     */
    fun loadCnsData() {
        // TODO: CNS/importer feature incomplete: getAllWithCnsData, CnsDataEntry, etc. missing
        // This function is disabled until the CNS/importer feature is complete.
        _uiState.value = CnsDataUiState(cnsDataItems = emptyList(), isLoading = false)
    }

    /**
     * Parse CNS JSON from RawWorkoutData and create a display item.
     */
    // TODO: CNS/importer feature incomplete: parseCnsData, CnsDataEntry, cnsJson, etc. missing
    // private fun parseCnsData(rawData: RawWorkoutData): CnsDataDisplayItem? { ... }
}



