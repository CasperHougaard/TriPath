package com.tripath.data.local.backup

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSessionLog
import com.tripath.data.local.database.entities.LiftSetLog
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionEntryKind
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.NutritionPreset
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.ScannedFoodCache
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferenceEntry
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.BiologicalSex
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.TaskTriggerType
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Serializable snapshot of everything the app stores.
 *
 * ## Coverage contract
 * This must contain one collection per entity in
 * [com.tripath.data.local.database.AppDatabase], plus [preferences] for the DataStore.
 * Anything omitted here is data the user permanently loses on a device transfer — and, because
 * a replace-all restore wipes first, omitting a table also means restoring *deletes* it.
 * When you add a Room entity, add it here in the same commit.
 *
 * ## Compatibility
 * Every collection is defaulted, so backups written by older app versions still deserialize.
 * Readers must tolerate a *lower* [version]; a higher one is rejected by [BackupManager].
 */
@Serializable
data class AppBackupData(
    val version: Int = BackupManager.BACKUP_VERSION,
    val timestamp: Long,
    val appVersionCode: Long? = null,
    val appVersionName: String? = null,
    val trainingPlans: List<TrainingPlanDto> = emptyList(),
    val workoutLogs: List<WorkoutLogDto> = emptyList(),
    val rawWorkoutData: List<RawWorkoutDataDto> = emptyList(),
    val sleepLogs: List<SleepLogDto> = emptyList(),
    val specialPeriods: List<SpecialPeriodDto> = emptyList(),
    val dayNotes: List<DayNoteDto> = emptyList(),
    val dayTemplates: List<DayTemplateDto> = emptyList(),
    val wellnessLogs: List<DailyWellnessLogDto> = emptyList(),
    val wellnessTasks: List<WellnessTaskDto> = emptyList(),
    val bodyCompositionLogs: List<BodyCompositionLogDto> = emptyList(),
    val nutritionLogs: List<NutritionLogDto> = emptyList(),
    val nutritionEntries: List<NutritionEntryDto> = emptyList(),
    val nutritionPresets: List<NutritionPresetDto> = emptyList(),
    val dailyActivityLogs: List<DailyActivityLogDto> = emptyList(),
    val liftSessionLogs: List<LiftSessionLogDto> = emptyList(),
    val liftSetLogs: List<LiftSetLogDto> = emptyList(),
    val liftExerciseCatalog: List<LiftExerciseCatalogEntryDto> = emptyList(),
    val scannedFoods: List<ScannedFoodDto> = emptyList(),
    val preferences: List<PreferenceEntryDto> = emptyList(),
    /**
     * Profile fields as written by backup versions <= 4, when the profile lived in its own DTO
     * instead of the generic [preferences] list. Retained read-only so old files still restore.
     */
    val userProfile: UserProfileDto? = null
)

// ==================== Preferences ====================

@Serializable
data class PreferenceEntryDto(
    val key: String,
    val type: String,
    val value: String
)

fun PreferenceEntry.toDto() = PreferenceEntryDto(key = key, type = type, value = value)

fun PreferenceEntryDto.toEntry() = PreferenceEntry(key = key, type = type, value = value)

// ==================== Training plans ====================

@Serializable
data class TrainingPlanDto(
    val id: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val type: String,
    val subType: String?,
    val durationMinutes: Int,
    val plannedTSS: Int,
    val plannedDistanceMeters: Int? = null,
    val strengthFocus: String?,
    val intensity: String?
)

fun TrainingPlan.toDto() = TrainingPlanDto(
    id = id,
    date = date,
    type = type.name,
    subType = subType,
    durationMinutes = durationMinutes,
    plannedTSS = plannedTSS,
    plannedDistanceMeters = plannedDistanceMeters,
    strengthFocus = strengthFocus?.name,
    intensity = intensity?.name
)

fun TrainingPlanDto.toEntity() = TrainingPlan(
    id = id,
    date = date,
    type = enumOrNull<WorkoutType>(type) ?: WorkoutType.OTHER,
    subType = subType,
    durationMinutes = durationMinutes,
    plannedTSS = plannedTSS,
    plannedDistanceMeters = plannedDistanceMeters,
    strengthFocus = strengthFocus?.let { enumOrNull<StrengthFocus>(it) },
    intensity = intensity?.let { enumOrNull<Intensity>(it) }
)

// ==================== Workout logs ====================

@Serializable
data class WorkoutLogDto(
    val connectId: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val type: String,
    val durationMinutes: Int,
    val avgHeartRate: Int?,
    val calories: Int?,
    val computedTSS: Int?,
    val distanceMeters: Double?,
    val avgSpeedKmh: Double?,
    val avgPowerWatts: Int?,
    val steps: Int?,
    val hrZoneDistribution: Map<String, Int>? = null,
    val powerZoneDistribution: Map<String, Int>? = null,
    val isIgnored: Boolean = false
)

fun WorkoutLog.toDto() = WorkoutLogDto(
    connectId = connectId,
    date = date,
    type = type.name,
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    calories = calories,
    computedTSS = computedTSS,
    distanceMeters = distanceMeters,
    avgSpeedKmh = avgSpeedKmh,
    avgPowerWatts = avgPowerWatts,
    steps = steps,
    hrZoneDistribution = hrZoneDistribution,
    powerZoneDistribution = powerZoneDistribution,
    isIgnored = isIgnored
)

fun WorkoutLogDto.toEntity() = WorkoutLog(
    connectId = connectId,
    date = date,
    type = enumOrNull<WorkoutType>(type) ?: WorkoutType.OTHER,
    durationMinutes = durationMinutes,
    avgHeartRate = avgHeartRate,
    calories = calories,
    computedTSS = computedTSS,
    distanceMeters = distanceMeters,
    avgSpeedKmh = avgSpeedKmh,
    avgPowerWatts = avgPowerWatts,
    steps = steps,
    hrZoneDistribution = hrZoneDistribution,
    powerZoneDistribution = powerZoneDistribution,
    isIgnored = isIgnored
)

// ==================== Raw workout samples ====================

@Serializable
data class RawWorkoutDataDto(
    val connectId: String,
    val rawExerciseType: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val hrSamplesJson: String?,
    val powerSamplesJson: String?,
    val rawCalories: Int?,
    val rawDistanceMeters: Double?,
    val rawSteps: Int?,
    val routeJson: String? = null,
    val cnsJson: String? = null,
    val importedAt: Long
)

fun RawWorkoutData.toDto() = RawWorkoutDataDto(
    connectId = connectId,
    rawExerciseType = rawExerciseType,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    hrSamplesJson = hrSamplesJson,
    powerSamplesJson = powerSamplesJson,
    rawCalories = rawCalories,
    rawDistanceMeters = rawDistanceMeters,
    rawSteps = rawSteps,
    routeJson = routeJson,
    cnsJson = cnsJson,
    importedAt = importedAt
)

fun RawWorkoutDataDto.toEntity() = RawWorkoutData(
    connectId = connectId,
    rawExerciseType = rawExerciseType,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    hrSamplesJson = hrSamplesJson,
    powerSamplesJson = powerSamplesJson,
    rawCalories = rawCalories,
    rawDistanceMeters = rawDistanceMeters,
    rawSteps = rawSteps,
    routeJson = routeJson,
    cnsJson = cnsJson,
    importedAt = importedAt
)

// ==================== Sleep ====================

@Serializable
data class SleepLogDto(
    val connectId: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val title: String?,
    val stagesJson: String?,
    val deepSleepMinutes: Int?,
    val lightSleepMinutes: Int?,
    val remSleepMinutes: Int?,
    val awakeMinutes: Int?,
    val sleepScore: Int? = null,
    val importedAt: Long,
    val isIgnored: Boolean = false
)

fun SleepLog.toDto() = SleepLogDto(
    connectId = connectId,
    date = date,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMinutes = durationMinutes,
    title = title,
    stagesJson = stagesJson,
    deepSleepMinutes = deepSleepMinutes,
    lightSleepMinutes = lightSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    awakeMinutes = awakeMinutes,
    sleepScore = sleepScore,
    importedAt = importedAt,
    isIgnored = isIgnored
)

fun SleepLogDto.toEntity() = SleepLog(
    connectId = connectId,
    date = date,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMinutes = durationMinutes,
    title = title,
    stagesJson = stagesJson,
    deepSleepMinutes = deepSleepMinutes,
    lightSleepMinutes = lightSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    awakeMinutes = awakeMinutes,
    sleepScore = sleepScore,
    importedAt = importedAt,
    isIgnored = isIgnored
)

// ==================== Special periods ====================

@Serializable
data class SpecialPeriodDto(
    val id: String,
    val type: String,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate,
    val notes: String?
)

fun SpecialPeriod.toDto() = SpecialPeriodDto(
    id = id,
    type = type.name,
    startDate = startDate,
    endDate = endDate,
    notes = notes
)

/**
 * Returns null when [type] names a period kind this build doesn't have. Unlike a workout, a
 * special period has no neutral fallback — mislabelling an injury as a holiday would silently
 * change how the coach plans around it — so an unresolvable record is skipped instead.
 */
fun SpecialPeriodDto.toEntity(): SpecialPeriod? {
    val periodType = enumOrNull<SpecialPeriodType>(type) ?: return null
    return SpecialPeriod(
        id = id,
        type = periodType,
        startDate = startDate,
        endDate = endDate,
        notes = notes
    )
}

// ==================== Day notes ====================

@Serializable
data class DayNoteDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val note: String
)

fun DayNote.toDto() = DayNoteDto(date = date, note = note)

fun DayNoteDto.toEntity() = DayNote(date = date, note = note)

// ==================== Day templates ====================

@Serializable
data class DayTemplateDto(
    val id: String,
    val name: String,
    val activitiesJson: String
)

fun DayTemplate.toDto() = DayTemplateDto(id = id, name = name, activitiesJson = activitiesJson)

fun DayTemplateDto.toEntity() = DayTemplate(id = id, name = name, activitiesJson = activitiesJson)

// ==================== Wellness ====================

@Serializable
data class DailyWellnessLogDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val hrvRmssd: Double? = null,
    val morningWeight: Double? = null,
    val sorenessIndex: Int? = null,
    val moodIndex: Int? = null,
    val allergySeverity: String? = null,
    val completedTaskIds: List<Long>? = null
)

fun DailyWellnessLog.toDto() = DailyWellnessLogDto(
    date = date,
    sleepMinutes = sleepMinutes,
    hrvRmssd = hrvRmssd,
    morningWeight = morningWeight,
    sorenessIndex = sorenessIndex,
    moodIndex = moodIndex,
    allergySeverity = allergySeverity?.name,
    completedTaskIds = completedTaskIds
)

fun DailyWellnessLogDto.toEntity() = DailyWellnessLog(
    date = date,
    sleepMinutes = sleepMinutes,
    hrvRmssd = hrvRmssd,
    morningWeight = morningWeight,
    sorenessIndex = sorenessIndex,
    moodIndex = moodIndex,
    allergySeverity = allergySeverity?.let { enumOrNull<AllergySeverity>(it) },
    completedTaskIds = completedTaskIds
)

@Serializable
data class WellnessTaskDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    val type: String,
    val triggerThreshold: Int? = null
)

fun WellnessTaskDefinition.toDto() = WellnessTaskDto(
    id = id,
    title = title,
    description = description,
    type = type.name,
    triggerThreshold = triggerThreshold
)

fun WellnessTaskDto.toEntity() = WellnessTaskDefinition(
    id = id,
    title = title,
    description = description,
    type = enumOrNull<TaskTriggerType>(type) ?: TaskTriggerType.DAILY,
    triggerThreshold = triggerThreshold
)

// ==================== Body composition ====================

@Serializable
data class BodyCompositionLogDto(
    val id: String,
    val timestamp: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val boneMassKg: Double?,
    val leanMassKg: Double?,
    val importedAt: Long,
    val isIgnored: Boolean = false
)

fun BodyCompositionLog.toDto() = BodyCompositionLogDto(
    id = id,
    timestamp = timestamp,
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    boneMassKg = boneMassKg,
    leanMassKg = leanMassKg,
    importedAt = importedAt,
    isIgnored = isIgnored
)

fun BodyCompositionLogDto.toEntity() = BodyCompositionLog(
    id = id,
    timestamp = timestamp,
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    boneMassKg = boneMassKg,
    leanMassKg = leanMassKg,
    importedAt = importedAt,
    isIgnored = isIgnored
)

// ==================== Nutrition ====================

@Serializable
data class NutritionLogDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val energyKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val creatineTaken: Boolean = false,
    val updatedAt: Long
)

fun NutritionLog.toDto() = NutritionLogDto(
    date = date,
    energyKcal = energyKcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    creatineTaken = creatineTaken,
    updatedAt = updatedAt
)

fun NutritionLogDto.toEntity() = NutritionLog(
    date = date,
    energyKcal = energyKcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    creatineTaken = creatineTaken,
    updatedAt = updatedAt
)

// ==================== Nutrition entries (itemised adds behind the totals) ====================

@Serializable
data class NutritionEntryDto(
    val id: Long,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val loggedAt: Long,
    val kind: String,
    val label: String? = null,
    val deltaKcal: Double? = null,
    val deltaProteinG: Double? = null,
    val deltaCarbsG: Double? = null,
    val deltaFatG: Double? = null,
    val prevKcal: Double? = null,
    val prevProteinG: Double? = null,
    val creatineFrom: Boolean? = null,
    val creatineTo: Boolean? = null
)

fun NutritionEntry.toDto() = NutritionEntryDto(
    id = id,
    date = date,
    loggedAt = loggedAt,
    kind = kind.name,
    label = label,
    deltaKcal = deltaKcal,
    deltaProteinG = deltaProteinG,
    deltaCarbsG = deltaCarbsG,
    deltaFatG = deltaFatG,
    prevKcal = prevKcal,
    prevProteinG = prevProteinG,
    creatineFrom = creatineFrom,
    creatineTo = creatineTo
)

fun NutritionEntryDto.toEntity() = NutritionEntry(
    id = id,
    date = date,
    loggedAt = loggedAt,
    // An unknown kind only affects how the row is labelled in the day log, so fall back to the
    // generic one rather than dropping a record the totals were built from.
    kind = enumOrNull<NutritionEntryKind>(kind) ?: NutritionEntryKind.CUSTOM_ADD,
    label = label,
    deltaKcal = deltaKcal,
    deltaProteinG = deltaProteinG,
    deltaCarbsG = deltaCarbsG,
    deltaFatG = deltaFatG,
    prevKcal = prevKcal,
    prevProteinG = prevProteinG,
    creatineFrom = creatineFrom,
    creatineTo = creatineTo
)

// ==================== Nutrition presets (the "library") ====================

@Serializable
data class NutritionPresetDto(
    val id: String,
    val label: String,
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val createdAt: Long
)

fun NutritionPreset.toDto() = NutritionPresetDto(
    id = id,
    label = label,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    createdAt = createdAt
)

fun NutritionPresetDto.toEntity() = NutritionPreset(
    id = id,
    label = label,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    createdAt = createdAt
)

// ==================== Daily activity (whole-day steps, calories, HRV) ====================

@Serializable
data class DailyActivityLogDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val steps: Int? = null,
    val workoutSteps: Int? = null,
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val hrvRmssd: Double? = null,
    val importedAt: Long
)

fun DailyActivityLog.toDto() = DailyActivityLogDto(
    date = date,
    steps = steps,
    workoutSteps = workoutSteps,
    activeCaloriesKcal = activeCaloriesKcal,
    totalCaloriesKcal = totalCaloriesKcal,
    hrvRmssd = hrvRmssd,
    importedAt = importedAt
)

fun DailyActivityLogDto.toEntity() = DailyActivityLog(
    date = date,
    steps = steps,
    workoutSteps = workoutSteps,
    activeCaloriesKcal = activeCaloriesKcal,
    totalCaloriesKcal = totalCaloriesKcal,
    hrvRmssd = hrvRmssd,
    importedAt = importedAt
)

// ==================== LiftPath sync (sessions, sets, exercise catalog) ====================

@Serializable
data class LiftSessionLogDto(
    val id: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val startMillis: Long? = null,
    val durationSeconds: Long? = null,
    val planName: String? = null,
    val dominantIntent: String? = null,
    val totalSets: Int = 0,
    val importedAt: Long
)

fun LiftSessionLog.toDto() = LiftSessionLogDto(
    id = id,
    date = date,
    startMillis = startMillis,
    durationSeconds = durationSeconds,
    planName = planName,
    dominantIntent = dominantIntent,
    totalSets = totalSets,
    importedAt = importedAt
)

fun LiftSessionLogDto.toEntity() = LiftSessionLog(
    id = id,
    date = date,
    startMillis = startMillis,
    durationSeconds = durationSeconds,
    planName = planName,
    dominantIntent = dominantIntent,
    totalSets = totalSets,
    importedAt = importedAt
)

@Serializable
data class LiftSetLogDto(
    val sessionId: String,
    val exerciseId: Int,
    val setNumber: Int,
    val kg: Float,
    val reps: Int,
    val rpe: Float? = null,
    val isWarmup: Boolean = false,
    val intent: String? = null,
    val durationSeconds: Int? = null,
    val bodyweightKg: Float? = null
)

fun LiftSetLog.toDto() = LiftSetLogDto(
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    kg = kg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    intent = intent,
    durationSeconds = durationSeconds,
    bodyweightKg = bodyweightKg
)

fun LiftSetLogDto.toEntity() = LiftSetLog(
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    kg = kg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    intent = intent,
    durationSeconds = durationSeconds,
    bodyweightKg = bodyweightKg
)

@Serializable
data class LiftExerciseCatalogEntryDto(
    val id: Int,
    val name: String,
    val region: String? = null,
    val tier: String? = null,
    val pattern: String? = null,
    val mechanics: String? = null,
    val primaryTargets: String = "",
    val secondaryTargets: String = "",
    val importedAt: Long
)

fun LiftExerciseCatalogEntry.toDto() = LiftExerciseCatalogEntryDto(
    id = id,
    name = name,
    region = region,
    tier = tier,
    pattern = pattern,
    mechanics = mechanics,
    primaryTargets = primaryTargets,
    secondaryTargets = secondaryTargets,
    importedAt = importedAt
)

fun LiftExerciseCatalogEntryDto.toEntity() = LiftExerciseCatalogEntry(
    id = id,
    name = name,
    region = region,
    tier = tier,
    pattern = pattern,
    mechanics = mechanics,
    primaryTargets = primaryTargets,
    secondaryTargets = secondaryTargets,
    importedAt = importedAt
)

// ==================== Scanned food cache (barcode -> per-100g nutrition) ====================

@Serializable
data class ScannedFoodDto(
    val barcode: String,
    val name: String? = null,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val isManualOverride: Boolean = false,
    val updatedAt: Long
)

fun ScannedFoodCache.toDto() = ScannedFoodDto(
    barcode = barcode,
    name = name,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    isManualOverride = isManualOverride,
    updatedAt = updatedAt
)

fun ScannedFoodDto.toEntity() = ScannedFoodCache(
    barcode = barcode,
    name = name,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    isManualOverride = isManualOverride,
    updatedAt = updatedAt
)

// ==================== Legacy user profile (backup version <= 4) ====================

/**
 * Profile shape used by backup versions <= 4. Version 5 onwards stores the profile inside the
 * generic [AppBackupData.preferences] list, so this is read-only: it is parsed when restoring an
 * older file and never written.
 *
 * `id` was present in some old files and is ignored.
 */
@Serializable
data class UserProfileDto(
    val id: Int? = null,
    val ftpBike: Int? = null,
    val maxHeartRate: Int? = null,
    val defaultSwimTSS: Int? = null,
    val defaultStrengthHeavyTSS: Int? = null,
    val defaultStrengthLightTSS: Int? = null,
    @Serializable(with = LocalDateSerializer::class)
    val goalDate: LocalDate? = null,
    val weeklyHoursGoal: Float? = null,
    val annualVolumeGoalHours: Float? = null,
    val lthr: Int? = null,
    val cssSecondsper100m: Int? = null,
    val thresholdRunPace: Int? = null,
    val biologicalSex: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate? = null,
    val heightCm: Int? = null
)

fun UserProfileDto.toEntity() = UserProfile(
    ftpBike = ftpBike,
    maxHeartRate = maxHeartRate,
    defaultSwimTSS = defaultSwimTSS,
    defaultStrengthHeavyTSS = defaultStrengthHeavyTSS,
    defaultStrengthLightTSS = defaultStrengthLightTSS,
    goalDate = goalDate,
    weeklyHoursGoal = weeklyHoursGoal,
    annualVolumeGoalHours = annualVolumeGoalHours,
    lthr = lthr,
    cssSecondsper100m = cssSecondsper100m,
    thresholdRunPace = thresholdRunPace,
    biologicalSex = biologicalSex?.let { enumOrNull<BiologicalSex>(it) },
    birthDate = birthDate,
    heightCm = heightCm
)

/**
 * Enum names are stored as text, so a backup can name a constant this build no longer has
 * (a renamed workout type, a removed period type). Returning null lets each call site fall back
 * to a sensible default and keep the rest of the record, instead of aborting the whole restore.
 */
private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    try {
        enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        null
    }
