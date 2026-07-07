package com.tripath.ui.settings.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.repository.RecoveryRepository
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.WorkoutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * A single row in the unified Health Connect synced-data list.
 * Covers every data type TriPath imports: exercises, sleep and body composition.
 */
sealed interface SyncedDataPoint {
    /** Health Connect record id (connectId / metadata.id). */
    val id: String
    /** Epoch millis used for sorting the unified list (newest first). */
    val timeMillis: Long
    /** Whether this data point is excluded from analytics. */
    val isIgnored: Boolean

    data class Exercise(val log: WorkoutLog, override val timeMillis: Long) : SyncedDataPoint {
        override val id: String get() = log.connectId
        override val isIgnored: Boolean get() = log.isIgnored
        val type: WorkoutType get() = log.type
        val durationMinutes: Int get() = log.durationMinutes
    }

    data class Sleep(val log: SleepLog) : SyncedDataPoint {
        override val id: String get() = log.connectId
        override val timeMillis: Long get() = log.startTimeMillis
        override val isIgnored: Boolean get() = log.isIgnored
        val durationMinutes: Int get() = log.durationMinutes
    }

    data class Body(val log: BodyCompositionLog) : SyncedDataPoint {
        override val id: String get() = log.id
        override val timeMillis: Long get() = log.timestamp
        override val isIgnored: Boolean get() = log.isIgnored
    }
}

data class SyncedDataUiState(
    val isLoading: Boolean = true,
    val dataPoints: List<SyncedDataPoint> = emptyList()
) {
    val ignoredCount: Int get() = dataPoints.count { it.isIgnored }
}

@HiltViewModel
class SyncedExercisesViewModel @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val recoveryRepository: RecoveryRepository
) : ViewModel() {

    val uiState: StateFlow<SyncedDataUiState> =
        combine(
            trainingRepository.getAllWorkoutLogsIncludingIgnored(),
            recoveryRepository.getAllSleepLogsIncludingIgnored(),
            recoveryRepository.getAllBodyCompositionLogsIncludingIgnored()
        ) { workouts, sleeps, bodies ->
            val points = buildList {
                workouts.forEach { add(SyncedDataPoint.Exercise(it, it.date.toStartOfDayMillis())) }
                sleeps.forEach { add(SyncedDataPoint.Sleep(it)) }
                bodies.forEach { add(SyncedDataPoint.Body(it)) }
            }.sortedByDescending { it.timeMillis }

            SyncedDataUiState(isLoading = false, dataPoints = points)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SyncedDataUiState()
        )

    /**
     * Toggle whether a synced data point is ignored (excluded from analytics/training-load).
     * The record is kept; downstream consumers filter on the flag.
     */
    fun setIgnored(point: SyncedDataPoint, isIgnored: Boolean) {
        viewModelScope.launch {
            when (point) {
                is SyncedDataPoint.Exercise -> trainingRepository.setWorkoutLogIgnored(point.id, isIgnored)
                is SyncedDataPoint.Sleep -> recoveryRepository.setSleepIgnored(point.id, isIgnored)
                is SyncedDataPoint.Body -> recoveryRepository.setIgnored(point.id, isIgnored)
            }
        }
    }
}

private fun java.time.LocalDate.toStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
