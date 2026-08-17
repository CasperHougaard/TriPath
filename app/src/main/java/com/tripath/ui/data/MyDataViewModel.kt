package com.tripath.ui.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tripath.data.local.backup.BackupManager
import com.tripath.data.local.backup.CloudSnapshotStore
import com.tripath.data.local.backup.ImportMode
import com.tripath.data.local.backup.ImportSummary
import com.tripath.data.local.backup.SnapshotMeta
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.dao.BodyCompositionDao
import com.tripath.data.local.database.dao.DayNoteDao
import com.tripath.data.local.database.dao.DayTemplateDao
import com.tripath.data.local.database.dao.NutritionEntryDao
import com.tripath.data.local.database.dao.NutritionLogDao
import com.tripath.data.local.database.dao.RawWorkoutDataDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.dao.SpecialPeriodDao
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.WellnessDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferenceEntry
import com.tripath.data.local.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MyDataUiState(
    val counts: Map<DataCategory, Int> = emptyMap(),
    val snapshot: SnapshotMeta? = null,
    val databaseBytes: Long = 0,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isLoadingCounts: Boolean = true,
    val message: String? = null,
    val errorMessage: String? = null
) {
    val totalRecords: Int get() = counts.values.sum()
}

/**
 * Backs the My Data browser: what the app stores, and the state of the user's backup.
 *
 * Reads DAOs directly rather than going through the repositories. The browser's job is to show
 * the database as it actually is, table by table, so the repository abstractions — which filter
 * ignored records and expose only what the feature screens need — would work against it here.
 */
@HiltViewModel
class MyDataViewModel @Inject constructor(
    application: Application,
    private val backupManager: BackupManager,
    private val snapshotStore: CloudSnapshotStore,
    private val preferencesManager: PreferencesManager,
    private val trainingPlanDao: TrainingPlanDao,
    private val workoutLogDao: WorkoutLogDao,
    private val rawWorkoutDataDao: RawWorkoutDataDao,
    private val sleepLogDao: SleepLogDao,
    private val specialPeriodDao: SpecialPeriodDao,
    private val dayNoteDao: DayNoteDao,
    private val dayTemplateDao: DayTemplateDao,
    private val wellnessDao: WellnessDao,
    private val bodyCompositionDao: BodyCompositionDao,
    private val nutritionLogDao: NutritionLogDao,
    private val nutritionEntryDao: NutritionEntryDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MyDataUiState())
    val uiState: StateFlow<MyDataUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) { loadCounts() }
            val dbBytes = withContext(Dispatchers.IO) {
                getApplication<Application>()
                    .getDatabasePath(AppDatabase.DATABASE_NAME)
                    .takeIf { it.exists() }
                    ?.length() ?: 0L
            }
            _uiState.value = _uiState.value.copy(
                counts = counts,
                databaseBytes = dbBytes,
                snapshot = snapshotStore.reloadMeta(),
                isLoadingCounts = false
            )
        }
    }

    private suspend fun loadCounts(): Map<DataCategory, Int> = mapOf(
        DataCategory.WORKOUTS to workoutLogDao.getCount(),
        DataCategory.RAW_SAMPLES to rawWorkoutDataDao.getCount(),
        DataCategory.PLANNED_SESSIONS to trainingPlanDao.getCount(),
        DataCategory.SLEEP to sleepLogDao.getCount(),
        DataCategory.BODY_COMPOSITION to bodyCompositionDao.getCount(),
        DataCategory.NUTRITION to nutritionLogDao.getCount(),
        DataCategory.NUTRITION_ENTRIES to nutritionEntryDao.getCount(),
        DataCategory.WELLNESS_LOGS to wellnessDao.getLogCount(),
        DataCategory.WELLNESS_TASKS to wellnessDao.getTaskCount(),
        DataCategory.SPECIAL_PERIODS to specialPeriodDao.getCount(),
        DataCategory.DAY_NOTES to dayNoteDao.getCount(),
        DataCategory.DAY_TEMPLATES to dayTemplateDao.getCount(),
        DataCategory.SETTINGS to preferencesManager.exportAll().size
    )

    /** Force-write a fresh cloud snapshot, regardless of how recent the last one is. */
    fun backUpNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBackingUp = true, message = null, errorMessage = null)
            val result = snapshotStore.writeSnapshot()
            _uiState.value = result.fold(
                onSuccess = { meta ->
                    _uiState.value.copy(
                        isBackingUp = false,
                        snapshot = meta,
                        message = "Backup prepared — ${meta.totalRecords} records, " +
                            "${formatBytes(meta.compressedBytes)}"
                    )
                },
                onFailure = { error ->
                    _uiState.value.copy(
                        isBackingUp = false,
                        errorMessage = "Could not prepare backup: ${error.message}"
                    )
                }
            )
        }
    }

    /** Restore the snapshot held on this device. */
    fun restoreFromCloudSnapshot(mode: ImportMode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, message = null, errorMessage = null)
            val result = snapshotStore.restoreFromSnapshot(mode)
            handleImportResult(result)
        }
    }

    /** Restore from a JSON file the user picked. */
    fun importFromJson(jsonString: String, mode: ImportMode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, message = null, errorMessage = null)
            val result = backupManager.importFromJson(jsonString, mode)
            handleImportResult(result)
        }
    }

    private suspend fun handleImportResult(result: Result<ImportSummary>) {
        result.fold(
            onSuccess = { summary ->
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    message = describeImport(summary)
                )
                refresh()
                // The device now holds different data than the snapshot describes.
                snapshotStore.writeSnapshot()
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    errorMessage = error.message ?: "Restore failed"
                )
            }
        )
    }

    /** Produce the JSON for an export-to-file, without touching stored data. */
    suspend fun exportJson(): Result<String> = try {
        Result.success(backupManager.exportToJson())
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }

    // ==================== Per-category record loading ====================

    /**
     * Load every record in a category, flattened for display.
     *
     * Loaded on demand per screen rather than kept in state: raw sample rows carry large JSON
     * blobs, so holding all categories in memory at once would be wasteful for a screen the user
     * visits occasionally.
     */
    suspend fun loadRecords(category: DataCategory): List<DataRecordUi> = withContext(Dispatchers.IO) {
        when (category) {
            DataCategory.WORKOUTS -> workoutLogDao.getAllOnceIncludingIgnored().map { it.toRecordUi() }
            DataCategory.RAW_SAMPLES -> rawWorkoutDataDao.getAll().map { it.toRecordUi() }
            DataCategory.PLANNED_SESSIONS -> trainingPlanDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.SLEEP -> sleepLogDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.BODY_COMPOSITION -> bodyCompositionDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.NUTRITION -> nutritionLogDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.NUTRITION_ENTRIES -> nutritionEntryDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.WELLNESS_LOGS -> wellnessDao.getAllLogsOnce().map { it.toRecordUi() }
            DataCategory.WELLNESS_TASKS -> wellnessDao.getAllTasksOnce().map { it.toRecordUi() }
            DataCategory.SPECIAL_PERIODS -> specialPeriodDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.DAY_NOTES -> dayNoteDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.DAY_TEMPLATES -> dayTemplateDao.getAllOnce().map { it.toRecordUi() }
            DataCategory.SETTINGS -> preferencesManager.exportAll().map { it.toRecordUi() }
        }
    }
}

// ==================== Formatting helpers ====================

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy")
private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")

internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024))
}

internal fun formatEpochMillis(millis: Long): String =
    DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun formatDuration(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/** Reports a blob's size instead of its contents: a screenful of raw JSON tells the user nothing. */
private fun blobField(label: String, json: String?): Pair<String, String>? =
    json?.let { label to formatBytes(it.toByteArray(Charsets.UTF_8).size.toLong()) }

private fun describeImport(summary: ImportSummary): String = buildString {
    append(
        if (summary.mode == ImportMode.REPLACE_ALL) "Replaced all data: " else "Merged backup: "
    )
    append("${summary.totalRecords} records")
    if (summary.preferencesImported > 0) {
        append(", ${summary.preferencesImported} settings")
    }
    if (summary.specialPeriodsSkipped > 0) {
        append(" (${summary.specialPeriodsSkipped} special periods skipped — unknown type)")
    }
}

// ==================== Entity -> display mappers ====================

private fun WorkoutLog.toRecordUi() = DataRecordUi(
    id = connectId,
    title = DATE_FORMATTER.format(date),
    subtitle = listOfNotNull(
        type.name,
        formatDuration(durationMinutes),
        distanceMeters?.let { "%.2f km".format(it / 1000) }
    ).joinToString(" · "),
    fields = listOfNotNull(
        "Source ID" to connectId,
        avgHeartRate?.let { "Avg HR" to "$it bpm" },
        avgPowerWatts?.let { "Avg power" to "$it W" },
        avgSpeedKmh?.let { "Avg speed" to "%.1f km/h".format(it) },
        calories?.let { "Calories" to "$it kcal" },
        computedTSS?.let { "TSS" to "$it" },
        steps?.let { "Steps" to "$it" },
        hrZoneDistribution?.let { zones ->
            "HR zones" to zones.entries.joinToString(", ") { "${it.key}: ${it.value}m" }
        },
        powerZoneDistribution?.let { zones ->
            "Power zones" to zones.entries.joinToString(", ") { "${it.key}: ${it.value}m" }
        }
    ),
    isIgnored = isIgnored
)

private fun RawWorkoutData.toRecordUi() = DataRecordUi(
    id = connectId,
    title = formatEpochMillis(startTimeMillis),
    subtitle = listOfNotNull(
        "Exercise type $rawExerciseType",
        rawDistanceMeters?.let { "%.2f km".format(it / 1000) }
    ).joinToString(" · "),
    fields = listOfNotNull(
        "Source ID" to connectId,
        "Ended" to formatEpochMillis(endTimeMillis),
        "Imported" to formatEpochMillis(importedAt),
        rawCalories?.let { "Calories" to "$it kcal" },
        rawSteps?.let { "Steps" to "$it" },
        blobField("Heart rate samples", hrSamplesJson),
        blobField("Power samples", powerSamplesJson),
        blobField("Route", routeJson),
        blobField("CNS data", cnsJson)
    )
)

private fun TrainingPlan.toRecordUi() = DataRecordUi(
    id = id,
    title = DATE_FORMATTER.format(date),
    subtitle = listOfNotNull(
        type.name,
        formatDuration(durationMinutes),
        "TSS $plannedTSS"
    ).joinToString(" · "),
    fields = listOfNotNull(
        "ID" to id,
        subType?.let { "Sub-type" to it },
        plannedDistanceMeters?.let { "Planned distance" to "%.2f km".format(it / 1000.0) },
        strengthFocus?.let { "Strength focus" to it.name },
        intensity?.let { "Intensity" to it.name }
    )
)

private fun SleepLog.toRecordUi() = DataRecordUi(
    id = connectId,
    title = DATE_FORMATTER.format(date),
    subtitle = listOfNotNull(
        formatDuration(durationMinutes),
        sleepScore?.let { "Score $it" }
    ).joinToString(" · "),
    fields = listOfNotNull(
        "Source ID" to connectId,
        "Asleep" to formatEpochMillis(startTimeMillis),
        "Awake" to formatEpochMillis(endTimeMillis),
        title?.let { "Title" to it },
        deepSleepMinutes?.let { "Deep" to formatDuration(it) },
        lightSleepMinutes?.let { "Light" to formatDuration(it) },
        remSleepMinutes?.let { "REM" to formatDuration(it) },
        awakeMinutes?.let { "Awake" to formatDuration(it) },
        "Imported" to formatEpochMillis(importedAt),
        blobField("Stages", stagesJson)
    ),
    isIgnored = isIgnored
)

private fun BodyCompositionLog.toRecordUi() = DataRecordUi(
    id = id,
    title = formatEpochMillis(timestamp),
    subtitle = listOfNotNull(
        weightKg?.let { "%.1f kg".format(it) },
        bodyFatPercent?.let { "%.1f%% fat".format(it) }
    ).joinToString(" · "),
    fields = listOfNotNull(
        "ID" to id,
        weightKg?.let { "Weight" to "%.2f kg".format(it) },
        bodyFatPercent?.let { "Body fat" to "%.2f %%".format(it) },
        leanMassKg?.let { "Lean mass" to "%.2f kg".format(it) },
        boneMassKg?.let { "Bone mass" to "%.2f kg".format(it) },
        "Imported" to formatEpochMillis(importedAt)
    ),
    isIgnored = isIgnored
)

private fun NutritionLog.toRecordUi() = DataRecordUi(
    id = date.toString(),
    title = DATE_FORMATTER.format(date),
    subtitle = listOfNotNull(
        energyKcal?.let { "%.0f kcal".format(it) },
        proteinG?.let { "%.0f g protein".format(it) }
    ).joinToString(" · ").ifBlank { "No values logged" },
    fields = listOfNotNull(
        energyKcal?.let { "Energy" to "%.0f kcal".format(it) },
        proteinG?.let { "Protein" to "%.1f g".format(it) },
        carbsG?.let { "Carbs" to "%.1f g".format(it) },
        fatG?.let { "Fat" to "%.1f g".format(it) },
        "Creatine" to if (creatineTaken) "Taken" else "Not taken",
        "Updated" to formatEpochMillis(updatedAt)
    )
)

private fun NutritionEntry.toRecordUi() = DataRecordUi(
    id = id.toString(),
    title = "${DATE_FORMATTER.format(date)} · ${formatEpochMillis(loggedAt).takeLast(5)}",
    subtitle = listOfNotNull(label, kind.name).joinToString(" · "),
    fields = listOfNotNull(
        "ID" to id.toString(),
        // Deltas are signed on purpose: an entry's meaning is the change it applied, and a
        // negative adjustment reads as an error without the sign.
        deltaKcal?.let { "Energy" to "%+.0f kcal".format(it) },
        deltaProteinG?.let { "Protein" to "%+.1f g".format(it) },
        deltaCarbsG?.let { "Carbs" to "%+.1f g".format(it) },
        deltaFatG?.let { "Fat" to "%+.1f g".format(it) },
        prevKcal?.let { "Energy before" to "%.0f kcal".format(it) },
        prevProteinG?.let { "Protein before" to "%.1f g".format(it) },
        creatineFrom?.let { from -> "Creatine" to "$from → $creatineTo" },
        "Logged" to formatEpochMillis(loggedAt)
    )
)

private fun DailyWellnessLog.toRecordUi() = DataRecordUi(
    id = date.toString(),
    title = DATE_FORMATTER.format(date),
    subtitle = listOfNotNull(
        sleepMinutes?.let { formatDuration(it) },
        hrvRmssd?.let { "HRV %.1f".format(it) }
    ).joinToString(" · ").ifBlank { "No values logged" },
    fields = listOfNotNull(
        sleepMinutes?.let { "Sleep" to formatDuration(it) },
        hrvRmssd?.let { "HRV (RMSSD)" to "%.2f".format(it) },
        morningWeight?.let { "Morning weight" to "%.2f kg".format(it) },
        sorenessIndex?.let { "Soreness" to "$it/10" },
        moodIndex?.let { "Mood" to "$it/10" },
        allergySeverity?.let { "Allergy severity" to it.name },
        completedTaskIds?.takeIf { it.isNotEmpty() }?.let {
            "Completed tasks" to it.joinToString(", ")
        }
    )
)

private fun WellnessTaskDefinition.toRecordUi() = DataRecordUi(
    id = id.toString(),
    title = title,
    subtitle = type.name,
    fields = listOfNotNull(
        "ID" to id.toString(),
        description?.let { "Description" to it },
        triggerThreshold?.let { "Trigger threshold" to "$it" }
    )
)

private fun SpecialPeriod.toRecordUi() = DataRecordUi(
    id = id,
    title = type.name,
    subtitle = "${DATE_FORMATTER.format(startDate)} – ${DATE_FORMATTER.format(endDate)}",
    fields = listOfNotNull(
        "ID" to id,
        "Start" to DATE_FORMATTER.format(startDate),
        "End" to DATE_FORMATTER.format(endDate),
        notes?.let { "Notes" to it }
    )
)

private fun DayNote.toRecordUi() = DataRecordUi(
    id = date.toString(),
    title = DATE_FORMATTER.format(date),
    subtitle = note.take(80),
    fields = listOf("Note" to note)
)

private fun DayTemplate.toRecordUi() = DataRecordUi(
    id = id,
    title = name,
    subtitle = null,
    fields = listOfNotNull(
        "ID" to id,
        blobField("Activities", activitiesJson)
    )
)

private fun PreferenceEntry.toRecordUi() = DataRecordUi(
    id = key,
    title = key,
    subtitle = value.take(120),
    fields = listOf("Type" to describePreferenceType(type), "Value" to value)
)

private fun describePreferenceType(type: String): String = when (type) {
    PreferenceEntry.TYPE_BOOLEAN -> "Boolean"
    PreferenceEntry.TYPE_INT -> "Integer"
    PreferenceEntry.TYPE_LONG -> "Long"
    PreferenceEntry.TYPE_FLOAT -> "Float"
    PreferenceEntry.TYPE_DOUBLE -> "Double"
    PreferenceEntry.TYPE_STRING -> "Text"
    PreferenceEntry.TYPE_STRING_SET -> "Text set"
    else -> type
}
