package com.tripath.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.backup.BackupManager
import com.tripath.data.local.backup.ImportSummary
import com.tripath.data.model.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.healthconnect.ReprocessResult
import com.tripath.data.local.healthconnect.SleepSyncResult
import com.tripath.data.local.healthconnect.SyncResult
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Health Connect connection status.
 */
enum class HealthConnectStatus {
    NOT_AVAILABLE,      // Health Connect not installed on device
    NOT_CONNECTED,      // Available but permissions not granted
    CONNECTED           // Available and permissions granted
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val exportSuccess: Boolean = false,
    val importSuccess: Boolean = false,
    val resetSuccess: Boolean = false,
    val errorMessage: String? = null,
    val importSummary: ImportSummary? = null,
    // Health Connect state
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.NOT_AVAILABLE,
    val isSyncing: Boolean = false,
    val lastSyncResult: String? = null,
    val syncSuccess: Boolean? = null,
    val lastSyncDetails: SyncResult? = null,
    val syncedWorkouts: List<WorkoutLog> = emptyList(),
    // Theme preference
    val isDarkTheme: Boolean = true,
    // Sync days preference
    val syncDaysBack: Int = 30,
    // User Profile state
    val userProfile: UserProfile? = null,
    val isLoadingProfile: Boolean = false,
    val profileSaveSuccess: Boolean = false,
    val profileSaveError: String? = null,
    val tenKmTime: String = "",
    val calculatedThresholdPace: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val healthConnectManager: HealthConnectManager,
    private val preferencesManager: PreferencesManager,
    private val repository: TrainingRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Get the permissions set required for Health Connect.
     */
    val healthConnectPermissions: Set<String>
        get() = healthConnectManager.permissions

    init {
        // Check Health Connect status on initialization
        refreshHealthConnectStatus()
        
        // Load synced workouts
        loadSyncedWorkouts()
        
        // Observe dark theme preference
        viewModelScope.launch {
            preferencesManager.darkThemeFlow.collect { isDark ->
                _uiState.value = _uiState.value.copy(isDarkTheme = isDark)
            }
        }
        
        // Observe sync days preference
        viewModelScope.launch {
            preferencesManager.syncDaysFlow.collect { days ->
                _uiState.value = _uiState.value.copy(syncDaysBack = days)
            }
        }
        
        // Observe user profile
        loadUserProfile()
    }
    
    /**
     * Load user profile from repository.
     */
    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProfile = true)
            repository.getUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    isLoadingProfile = false
                )
            }
        }
    }

    /**
     * Load all synced workouts from the database.
     */
    private fun loadSyncedWorkouts() {
        viewModelScope.launch {
            healthConnectManager.getAllSyncedWorkouts().collect { workouts ->
                _uiState.value = _uiState.value.copy(syncedWorkouts = workouts)
            }
        }
    }

    /**
     * Toggle dark/light theme.
     */
    fun toggleTheme() {
        viewModelScope.launch {
            preferencesManager.toggleDarkTheme()
        }
    }

    /**
     * Refresh Health Connect availability and permission status.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val status = when {
                !healthConnectManager.isAvailable() -> HealthConnectStatus.NOT_AVAILABLE
                !healthConnectManager.checkPermissions() -> HealthConnectStatus.NOT_CONNECTED
                else -> HealthConnectStatus.CONNECTED
            }
            _uiState.value = _uiState.value.copy(healthConnectStatus = status)
        }
    }

    /**
     * Sync workouts and sleep from Health Connect.
     */
    fun syncHealthConnect() {
        viewModelScope.launch {
            val syncDays = _uiState.value.syncDaysBack
            
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                lastSyncResult = null,
                syncSuccess = null,
                lastSyncDetails = null
            )

            // Sync workouts
            val workoutResult = healthConnectManager.syncWorkouts(daysToLookBack = syncDays)
            
            // Sync sleep data
            val sleepResult = healthConnectManager.syncSleep(daysToLookBack = syncDays)

            // Build combined result message
            val message = buildSyncResultMessage(workoutResult, sleepResult, syncDays)
            val overallSuccess = workoutResult.isSuccess || sleepResult.isSuccess
            
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                lastSyncResult = message,
                syncSuccess = overallSuccess,
                lastSyncDetails = workoutResult.getOrNull()
            )
        }
    }
    
    /**
     * Build a combined sync result message from workout and sleep sync results.
     */
    private fun buildSyncResultMessage(
        workoutResult: Result<SyncResult>,
        sleepResult: Result<SleepSyncResult>,
        syncDays: Int
    ): String {
        val periodLabel = getSyncPeriodLabel(syncDays)
        
        return buildString {
            // Workout results
            workoutResult.fold(
                onSuccess = { syncResult ->
                    if (syncResult.foundInHealthConnect > 0) {
                        append("Workouts: ${syncResult.newlyImported} new")
                        if (syncResult.alreadySynced > 0) {
                            append(", ${syncResult.alreadySynced} existing")
                        }
                        if (syncResult.routeBackfilled > 0) {
                            append(", ${syncResult.routeBackfilled} routes added")
                        }
                        append(". ")
                    } else {
                        append("No workouts found. ")
                    }
                },
                onFailure = { error ->
                    append("Workout sync failed: ${error.message}. ")
                }
            )
            
            // Sleep results
            sleepResult.fold(
                onSuccess = { syncResult ->
                    if (syncResult.foundInHealthConnect > 0) {
                        append("Sleep: ${syncResult.newlyImported} new")
                        if (syncResult.alreadySynced > 0) {
                            append(", ${syncResult.alreadySynced} existing")
                        }
                        append(".")
                    } else {
                        append("No sleep data found.")
                    }
                },
                onFailure = { error ->
                    append("Sleep sync failed: ${error.message}")
                }
            )
        }
    }
    
    /**
     * Set the number of days to sync from Health Connect.
     */
    fun setSyncDays(days: Int) {
        viewModelScope.launch {
            preferencesManager.setSyncDays(days)
        }
    }
    
    /**
     * Get a human-readable label for the sync period.
     * Note: Health Connect limits historical data access to 60 days maximum.
     */
    private fun getSyncPeriodLabel(days: Int): String {
        return when (days) {
            7 -> "the last 7 days"
            14 -> "the last 14 days"
            30 -> "the last 30 days"
            60 -> "the last 60 days"
            else -> "the last $days days"
        }
    }

    /**
     * Called after permission request completes to refresh status.
     */
    fun onPermissionsResult() {
        refreshHealthConnectStatus()
    }

    /**
     * Reprocess all workouts in the database using the latest UserProfile settings.
     * Uses local RawWorkoutData, so it doesn't require Health Connect permissions.
     */
    fun reprocessWorkouts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                lastSyncResult = null,
                syncSuccess = null
            )

            val result = healthConnectManager.reprocessAllWorkouts()

            result.fold(
                onSuccess = { reprocessResult ->
                    val message = buildString {
                        append("Found ${reprocessResult.foundInDatabase} raw workouts. ")
                        if (reprocessResult.processed > 0) {
                            append("Reprocessed ${reprocessResult.processed}. ")
                        }
                        if (reprocessResult.errors > 0) {
                            append("${reprocessResult.errors} errors.")
                        }
                        if (reprocessResult.foundInDatabase == 0) {
                            clear()
                            append("No raw workouts found to reprocess.")
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        lastSyncResult = message,
                        syncSuccess = true
                    )
                    // Reload synced workouts to reflect changes
                    loadSyncedWorkouts()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        lastSyncResult = "Reprocess failed: ${error.message}",
                        syncSuccess = false
                    )
                }
            )
        }
    }

    /**
     * Export all data to JSON string.
     * Should be called before opening the file picker for export.
     */
    suspend fun exportData(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                val jsonString = backupManager.exportToJson()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = true,
                    errorMessage = null
                )
                Result.success(jsonString)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = false,
                    errorMessage = "Export failed: ${e.message}"
                )
                Result.failure(e)
            }
        }
    }

    /**
     * Import data from JSON string.
     */
    fun importData(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    importSuccess = false
                )
                
                val result = backupManager.importFromJson(jsonString)
                
                result.fold(
                    onSuccess = { summary ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            importSuccess = true,
                            importSummary = summary,
                            errorMessage = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            importSuccess = false,
                            importSummary = null,
                            errorMessage = "Import failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importSuccess = false,
                    importSummary = null,
                    errorMessage = "Import failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Reset all data in the database.
     * This will delete all training plans, workout logs, special periods, and user profile.
     */
    fun resetData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )
                
                val result = backupManager.clearAllData()
                
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null,
                            resetSuccess = true
                        )
                        // Reload user profile to reflect deletion
                        loadUserProfile()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Reset failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Reset failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear UI state messages (success/error).
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            exportSuccess = false,
            importSuccess = false,
            resetSuccess = false,
            errorMessage = null,
            importSummary = null,
            lastSyncResult = null,
            syncSuccess = null,
            profileSaveSuccess = false,
            profileSaveError = null
        )
    }
    
    /**
     * Update 10km time and calculate threshold pace.
     * @param time 10km race time in MM:SS or HH:MM:SS format
     */
    fun updateTenKmTime(time: String) {
        val totalSeconds = parseTimeToSeconds(time)
        val calculatedPace = if (totalSeconds != null && totalSeconds > 0) {
            val pacePerKm = totalSeconds / 10
            formatSecondsToCssTime(pacePerKm)
        } else {
            null
        }

        _uiState.value = _uiState.value.copy(
            tenKmTime = time,
            calculatedThresholdPace = calculatedPace
        )
    }

    private fun parseTimeToSeconds(timeString: String): Int? {
        if (timeString.isBlank()) return null
        
        val parts = timeString.split(":")
        return try {
            when (parts.size) {
                2 -> { // MM:SS
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    (minutes * 60) + seconds
                }
                3 -> { // HH:MM:SS
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    (hours * 3600) + (minutes * 60) + seconds
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save user profile with the provided values.
     */
    fun saveUserProfile(
        ftpBike: Int?,
        maxHeartRate: Int?,
        lthr: Int?,
        cssTimeString: String?,
        thresholdRunPaceString: String?,
        goalDate: java.time.LocalDate?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                profileSaveSuccess = false,
                profileSaveError = null
            )
            
            // Validate numeric fields (must be non-negative if provided)
            val validationErrors = mutableListOf<String>()
            ftpBike?.let { if (it < 0) validationErrors.add("FTP must be non-negative") }
            maxHeartRate?.let { if (it < 0) validationErrors.add("Max HR must be non-negative") }
            lthr?.let { if (it < 0) validationErrors.add("LTHR must be non-negative") }
            
            // Validate and convert CSS time string
            val cssSeconds = parseCssTimeToSeconds(cssTimeString)
            if (cssTimeString != null && cssTimeString.isNotBlank() && cssSeconds == null) {
                validationErrors.add("CSS must be in MM:SS format (e.g., 1:45)")
            }

            // Validate and convert Threshold Run Pace string
            val thresholdRunPace = parseCssTimeToSeconds(thresholdRunPaceString)
            if (thresholdRunPaceString != null && thresholdRunPaceString.isNotBlank() && thresholdRunPace == null) {
                validationErrors.add("Threshold Run Pace must be in MM:SS format (e.g., 5:30)")
            }
            
            if (validationErrors.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    profileSaveError = validationErrors.joinToString(", ")
                )
                return@launch
            }
            
            // Get current profile or create new one
            val currentProfile = _uiState.value.userProfile
            val updatedProfile = UserProfile(
                ftpBike = ftpBike,
                maxHeartRate = maxHeartRate,
                lthr = lthr,
                cssSecondsper100m = cssSeconds,
                thresholdRunPace = thresholdRunPace,
                goalDate = goalDate,
                // Preserve other fields from current profile
                defaultSwimTSS = currentProfile?.defaultSwimTSS,
                defaultStrengthHeavyTSS = currentProfile?.defaultStrengthHeavyTSS,
                defaultStrengthLightTSS = currentProfile?.defaultStrengthLightTSS,
                weeklyHoursGoal = currentProfile?.weeklyHoursGoal
            )
            
            try {
                withContext(Dispatchers.IO) {
                    repository.upsertUserProfile(updatedProfile)
                }
                _uiState.value = _uiState.value.copy(
                    profileSaveSuccess = true,
                    profileSaveError = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    profileSaveSuccess = false,
                    profileSaveError = "Failed to save profile: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Parse CSS time string (MM:SS format) to total seconds.
     * Returns null if format is invalid.
     */
    fun parseCssTimeToSeconds(timeString: String?): Int? {
        if (timeString.isNullOrBlank()) return null
        
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        
        return try {
            val minutes = parts[0].toIntOrNull() ?: return null
            val seconds = parts[1].toIntOrNull() ?: return null
            
            // Validate seconds are 0-59
            if (seconds < 0 || seconds > 59) return null
            if (minutes < 0) return null
            
            (minutes * 60) + seconds
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Format seconds to CSS time string (MM:SS format).
     */
    fun formatSecondsToCssTime(seconds: Int?): String? {
        if (seconds == null) return null
        
        val minutes = seconds / 60
        val secs = seconds % 60
        return "$minutes:${secs.toString().padStart(2, '0')}"
    }
}
