package com.tripath.ui.settings.healthconnect

import android.util.Log
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.RoutePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ExerciseImportDetailUiState(
    val isLoading: Boolean = false,
    val exercise: ExerciseSessionRecord? = null,
    val rawData: Map<String, Any?> = emptyMap(),
    val routeData: RouteDataInfo? = null,
    val errorMessage: String? = null
)

data class RouteDataInfo(
    val hasRoute: Boolean,
    val pointCount: Int = 0,
    val routeJson: String? = null
)

@HiltViewModel
class ExerciseImportDetailViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val repository: TrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseImportDetailUiState())
    val uiState: StateFlow<ExerciseImportDetailUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun loadDetails(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
                val exercise = healthConnectManager.getExerciseSession(sessionId)
            if (exercise != null) {
                val rawData = healthConnectManager.getSessionRawData(exercise.startTime, exercise.endTime)
                
                // Check route/GPX data from both stored data AND directly from Health Connect
                val routeDataInfo = try {
                    // First, check if we have it stored locally
                    val rawWorkoutData = repository.getRawWorkoutData(sessionId)
                    if (rawWorkoutData?.routeJson != null) {
                        val routePoints = try {
                            json.decodeFromString<List<RoutePoint>>(rawWorkoutData.routeJson)
                        } catch (e: Exception) {
                            Log.d("ExerciseImportDetail", "Failed to parse stored route JSON: ${e.message}")
                            emptyList()
                        }
                        RouteDataInfo(
                            hasRoute = true,
                            pointCount = routePoints.size,
                            routeJson = rawWorkoutData.routeJson
                        )
                    } else {
                        // Check if route data is available directly from Health Connect
                        try {
                            val routeResult = exercise.exerciseRouteResult
                            Log.d("ExerciseImportDetail", "Route result type: ${routeResult?.javaClass?.simpleName}, is null: ${routeResult == null}")
                            
                            when (routeResult) {
                                is ExerciseRoute -> {
                                    val route = routeResult
                                    val pointCount = route.route.size
                                    Log.d("ExerciseImportDetail", "Found route with $pointCount points")
                                    if (pointCount > 0) {
                                        RouteDataInfo(
                                            hasRoute = true,
                                            pointCount = pointCount,
                                            routeJson = null // Not stored yet, but available in Health Connect
                                        )
                                    } else {
                                        Log.d("ExerciseImportDetail", "Route exists but has no points")
                                        RouteDataInfo(hasRoute = false)
                                    }
                                }
                                null -> {
                                    Log.d("ExerciseImportDetail", "No route data in exerciseRouteResult")
                                    RouteDataInfo(hasRoute = false)
                                }
                                else -> {
                                    Log.d("ExerciseImportDetail", "Unknown route result type: ${routeResult.javaClass.simpleName}")
                                    RouteDataInfo(hasRoute = false)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ExerciseImportDetail", "Error checking route data: ${e.message}", e)
                            RouteDataInfo(hasRoute = false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ExerciseImportDetail", "Error in route data check: ${e.message}", e)
                    RouteDataInfo(hasRoute = false)
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exercise = exercise,
                    rawData = rawData,
                    routeData = routeDataInfo
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Exercise session not found."
                )
            }
        }
    }
}

