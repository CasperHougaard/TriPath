package com.tripath.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.tripath.data.model.ActivityLevel
import com.tripath.data.model.NutritionGoal
import com.tripath.data.model.ProjectionMode
import com.tripath.data.model.TrainingBalance
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.domain.running.RunningGoal
import com.tripath.ui.theme.AppearanceMode
import com.tripath.ui.theme.TriPathPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tripath_preferences")

/**
 * Manager for app-wide user preferences.
 * Uses DataStore for efficient, asynchronous preference storage.
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")

        /**
         * Appearance. Not in [TRANSIENT_KEY_NAMES] on purpose — a palette choice is user data,
         * so it rides along in the backup via [exportAll] and returns on a phone swap.
         */
        private val APPEARANCE_MODE_KEY = stringPreferencesKey("appearance_mode")
        private val LIGHT_PALETTE_KEY = stringPreferencesKey("light_palette")
        private val DARK_PALETTE_KEY = stringPreferencesKey("dark_palette")

        private val SYNC_DAYS_KEY = intPreferencesKey("sync_days_back")
        private val INCLUDE_IMPORTED_ACTIVITIES_KEY = booleanPreferencesKey("include_imported_activities")
        private val SLEEP_SCORE_BACKFILL_DONE_KEY = booleanPreferencesKey("sleep_score_backfill_done")
        private val HEALTH_LAST_SYNC_KEY = longPreferencesKey("health_last_sync_millis")
        
        // UserProfile keys
        private val FTP_BIKE_KEY = intPreferencesKey("ftp_bike")
        private val MAX_HEART_RATE_KEY = intPreferencesKey("max_heart_rate")
        private val DEFAULT_SWIM_TSS_KEY = intPreferencesKey("default_swim_tss")
        private val DEFAULT_STRENGTH_HEAVY_TSS_KEY = intPreferencesKey("default_strength_heavy_tss")
        private val DEFAULT_STRENGTH_LIGHT_TSS_KEY = intPreferencesKey("default_strength_light_tss")
        private val GOAL_DATE_KEY = longPreferencesKey("goal_date") // Stored as epoch day
        private val WEEKLY_HOURS_GOAL_KEY = floatPreferencesKey("weekly_hours_goal")
        private val ANNUAL_VOLUME_GOAL_HOURS_KEY = floatPreferencesKey("annual_volume_goal_hours")
        private val LTHR_KEY = intPreferencesKey("lthr")
        private val CSS_SECONDS_PER_100M_KEY = intPreferencesKey("css_seconds_per_100m")
        private val THRESHOLD_RUN_PACE_KEY = intPreferencesKey("threshold_run_pace")
        private val WEEKLY_AVAILABILITY_KEY = stringPreferencesKey("weekly_availability")
        private val LONG_TRAINING_DAY_KEY = stringPreferencesKey("long_training_day")
        private val STRENGTH_DAYS_KEY = intPreferencesKey("strength_days")
        private val TRAINING_BALANCE_KEY = stringPreferencesKey("training_balance")
        private val BIOLOGICAL_SEX_KEY = stringPreferencesKey("biological_sex")
        private val BIRTH_DATE_KEY = longPreferencesKey("birth_date") // epoch day
        private val HEIGHT_CM_KEY = intPreferencesKey("height_cm")
        private val PROTEIN_TARGET_G_KEY = floatPreferencesKey("protein_target_g")
        private val CALORIE_TARGET_KEY = floatPreferencesKey("calorie_target")

        // Fuel model keys. All optional — the model falls back to documented defaults so an
        // existing install keeps working before the user has been near the goal screen.
        private val NUTRITION_GOAL_KEY = stringPreferencesKey("nutrition_goal")
        private val GOAL_RATE_PCT_PER_WEEK_KEY = floatPreferencesKey("goal_rate_pct_per_week")
        private val RMR_OVERRIDE_KCAL_KEY = intPreferencesKey("rmr_override_kcal")
        private val ACTIVITY_LEVEL_KEY = stringPreferencesKey("activity_level")
        private val SLEEP_NEED_MINUTES_KEY = intPreferencesKey("sleep_need_minutes")
        private val PROJECTION_MODE_KEY = stringPreferencesKey("projection_mode")
        private val ACTIVE_RUNNING_GOAL_KEY = stringPreferencesKey("active_running_goal")
        
        // Planner Auto-planner Settings keys
        private val AUTO_PLANNER_ENABLED_KEY = booleanPreferencesKey("planner_auto_planner_enabled")

        // Auto strength planner keys
        private val AUTO_PLAN_STRENGTH_ENABLED_KEY = booleanPreferencesKey("auto_plan_strength_enabled")
        private val RUNNING_CONSIDERS_STRENGTH_KEY = booleanPreferencesKey("running_considers_strength")
        private val STRENGTH_FIRST_WORKOUT_DATE_KEY = longPreferencesKey("strength_first_workout_date") // epoch day

        // Legacy coach planning keys kept for migration from previous versions
        private val LEGACY_IS_SMART_PLANNING_ENABLED_KEY = booleanPreferencesKey("is_smart_planning_enabled")
        
        /** Default sync period in days */
        const val DEFAULT_SYNC_DAYS = 30

        /**
         * Preferences that describe *this device's* sync state rather than the user's data.
         * Carrying them into a backup would make a freshly restored phone believe it had already
         * synced with Health Connect, so it would skip the first import.
         */
        private val TRANSIENT_KEY_NAMES = setOf(
            "health_last_sync_millis",
            "sleep_score_backfill_done"
        )
    }

    /**
     * Flow that emits the current dark theme preference.
     * Default is true (dark theme enabled).
     */
    val darkThemeFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY] ?: true // Default to dark theme
    }

    /**
     * Set the dark theme preference.
     * @param enabled true for dark theme, false for light theme
     */
    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = enabled
        }
    }

    /**
     * Toggle the dark theme preference.
     */
    suspend fun toggleDarkTheme() {
        dataStore.edit { preferences ->
            val current = preferences[DARK_THEME_KEY] ?: true
            preferences[DARK_THEME_KEY] = !current
        }
    }

    // ==================== Appearance ====================

    /**
     * The light/dark mode: follow the system, or force one.
     *
     * Migrates the pre-design-system `dark_theme` boolean rather than ignoring it — `true`
     * becomes [AppearanceMode.DARK] and `false` becomes [AppearanceMode.LIGHT], so an existing
     * install keeps launching exactly as it did. Reading the legacy key as a fallback (rather
     * than rewriting it once at startup) means there is no migration step that can half-run.
     */
    val appearanceModeFlow: Flow<AppearanceMode> = dataStore.data.map { preferences ->
        preferences[APPEARANCE_MODE_KEY]?.let(AppearanceMode::fromPrefValue)
            ?: when (preferences[DARK_THEME_KEY]) {
                true -> AppearanceMode.DARK
                false -> AppearanceMode.LIGHT
                null -> AppearanceMode.DEFAULT
            }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        dataStore.edit { preferences ->
            preferences[APPEARANCE_MODE_KEY] = mode.prefValue
            // Keep the legacy key consistent: it is still what `darkThemeFlow` reads, and the
            // widget and privacy-policy activity have no reason to learn the richer model.
            if (mode != AppearanceMode.SYSTEM) {
                preferences[DARK_THEME_KEY] = mode == AppearanceMode.DARK
            }
        }
    }

    /** The palette used when light mode is in effect. */
    val lightPaletteFlow: Flow<TriPathPalette> = dataStore.data.map { preferences ->
        TriPathPalette.fromPrefValue(preferences[LIGHT_PALETTE_KEY])
    }

    /** The palette used when dark mode is in effect. Need not match [lightPaletteFlow]. */
    val darkPaletteFlow: Flow<TriPathPalette> = dataStore.data.map { preferences ->
        TriPathPalette.fromPrefValue(preferences[DARK_PALETTE_KEY])
    }

    suspend fun setLightPalette(palette: TriPathPalette) {
        dataStore.edit { it[LIGHT_PALETTE_KEY] = palette.prefValue }
    }

    suspend fun setDarkPalette(palette: TriPathPalette) {
        dataStore.edit { it[DARK_PALETTE_KEY] = palette.prefValue }
    }

    /**
     * Flow that emits the current sync days preference.
     * Default is 30 days.
     */
    val syncDaysFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SYNC_DAYS_KEY] ?: DEFAULT_SYNC_DAYS
    }

    /**
     * Set the number of days to sync from Health Connect.
     * @param days Number of days to look back when syncing
     */
    suspend fun setSyncDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[SYNC_DAYS_KEY] = days
        }
    }

    /**
     * Flow that emits the current include imported activities preference for the planner.
     * Default is false (planned only).
     */
    val includeImportedActivitiesFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[INCLUDE_IMPORTED_ACTIVITIES_KEY] ?: false // Default to planned only
    }

    /**
     * Set the include imported activities preference for the planner.
     * @param include true to include imported activities, false for planned only
     */
    suspend fun setIncludeImportedActivities(include: Boolean) {
        dataStore.edit { preferences ->
            preferences[INCLUDE_IMPORTED_ACTIVITIES_KEY] = include
        }
    }

    // ==================== User Profile Operations ====================

    /**
     * Flow that emits the current user profile.
     * Returns null if no profile has been saved yet.
     */
    val userProfileFlow: Flow<UserProfile?> = dataStore.data.map { preferences ->
        mapPreferencesToUserProfile(preferences)
    }

    /**
     * Get the user profile as a one-shot value.
     * Returns null if no profile has been saved yet.
     */
    suspend fun getUserProfile(): UserProfile? {
        val preferences = dataStore.data.first()
        return mapPreferencesToUserProfile(preferences)
    }

    private fun mapPreferencesToUserProfile(preferences: Preferences): UserProfile? {
        val ftpBike = preferences[FTP_BIKE_KEY]
        val maxHeartRate = preferences[MAX_HEART_RATE_KEY]
        val defaultSwimTSS = preferences[DEFAULT_SWIM_TSS_KEY]
        val defaultStrengthHeavyTSS = preferences[DEFAULT_STRENGTH_HEAVY_TSS_KEY]
        val defaultStrengthLightTSS = preferences[DEFAULT_STRENGTH_LIGHT_TSS_KEY]
        val goalDateEpochDay = preferences[GOAL_DATE_KEY]
        val weeklyHoursGoal = preferences[WEEKLY_HOURS_GOAL_KEY]
        val annualVolumeGoalHours = preferences[ANNUAL_VOLUME_GOAL_HOURS_KEY]
        val lthr = preferences[LTHR_KEY]
        val cssSecondsper100m = preferences[CSS_SECONDS_PER_100M_KEY]
        val thresholdRunPace = preferences[THRESHOLD_RUN_PACE_KEY]
        val weeklyAvailabilityJson = preferences[WEEKLY_AVAILABILITY_KEY]
        val longTrainingDayName = preferences[LONG_TRAINING_DAY_KEY]
        val strengthDays = preferences[STRENGTH_DAYS_KEY]
        val trainingBalanceJson = preferences[TRAINING_BALANCE_KEY]
        val biologicalSexName = preferences[BIOLOGICAL_SEX_KEY]
        val birthDateEpochDay = preferences[BIRTH_DATE_KEY]
        val heightCm = preferences[HEIGHT_CM_KEY]
        val proteinTargetG = preferences[PROTEIN_TARGET_G_KEY]
        val calorieTarget = preferences[CALORIE_TARGET_KEY]
        val nutritionGoalName = preferences[NUTRITION_GOAL_KEY]
        val goalRatePctPerWeek = preferences[GOAL_RATE_PCT_PER_WEEK_KEY]
        val rmrOverrideKcal = preferences[RMR_OVERRIDE_KCAL_KEY]
        val activityLevelName = preferences[ACTIVITY_LEVEL_KEY]
        val sleepNeedMinutes = preferences[SLEEP_NEED_MINUTES_KEY]
        val projectionModeName = preferences[PROJECTION_MODE_KEY]

        // If no fields are set, return null
        if (ftpBike == null && maxHeartRate == null && defaultSwimTSS == null &&
            defaultStrengthHeavyTSS == null && defaultStrengthLightTSS == null &&
            goalDateEpochDay == null && weeklyHoursGoal == null && annualVolumeGoalHours == null && lthr == null &&
            cssSecondsper100m == null && thresholdRunPace == null && weeklyAvailabilityJson == null &&
            longTrainingDayName == null && strengthDays == null && trainingBalanceJson == null &&
            biologicalSexName == null && birthDateEpochDay == null && heightCm == null &&
            proteinTargetG == null && calorieTarget == null &&
            nutritionGoalName == null && goalRatePctPerWeek == null && rmrOverrideKcal == null &&
            activityLevelName == null && sleepNeedMinutes == null && projectionModeName == null
        ) {
            return null
        }

        val biologicalSex = biologicalSexName?.let {
            try {
                com.tripath.data.model.BiologicalSex.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }

        val weeklyAvailability = weeklyAvailabilityJson?.let { json ->
            try {
                val map = Json.decodeFromString<Map<String, List<String>>>(json)
                map.entries.associate { (day, types) ->
                    DayOfWeek.valueOf(day) to types.map { WorkoutType.valueOf(it) }
                }
            } catch (e: Exception) {
                null
            }
        }

        val trainingBalance = trainingBalanceJson?.let { json ->
            try {
                Json.decodeFromString<TrainingBalance>(json)
            } catch (e: Exception) {
                TrainingBalance.IRONMAN_BASE
            }
        } ?: TrainingBalance.IRONMAN_BASE

        val longTrainingDay = longTrainingDayName?.let {
            try {
                DayOfWeek.valueOf(it)
            } catch (e: Exception) {
                DayOfWeek.SUNDAY
            }
        } ?: DayOfWeek.SUNDAY

        return UserProfile(
            ftpBike = ftpBike,
            maxHeartRate = maxHeartRate,
            defaultSwimTSS = defaultSwimTSS ?: 60,
            defaultStrengthHeavyTSS = defaultStrengthHeavyTSS ?: 60,
            defaultStrengthLightTSS = defaultStrengthLightTSS ?: 30,
            goalDate = goalDateEpochDay?.let { LocalDate.ofEpochDay(it) },
            weeklyHoursGoal = weeklyHoursGoal,
            annualVolumeGoalHours = annualVolumeGoalHours,
            lthr = lthr,
            cssSecondsper100m = cssSecondsper100m,
            thresholdRunPace = thresholdRunPace,
            weeklyAvailability = weeklyAvailability,
            longTrainingDay = longTrainingDay,
            strengthDays = strengthDays ?: 2,
            trainingBalance = trainingBalance,
            biologicalSex = biologicalSex,
            birthDate = birthDateEpochDay?.let { LocalDate.ofEpochDay(it) },
            heightCm = heightCm,
            proteinTargetG = proteinTargetG,
            calorieTarget = calorieTarget,
            // Unrecognised names decode to null rather than throwing, so a backup written by a
            // newer build restores cleanly onto an older one and simply falls back to the default.
            nutritionGoal = NutritionGoal.fromName(nutritionGoalName),
            goalRatePctPerWeek = goalRatePctPerWeek,
            rmrOverrideKcal = rmrOverrideKcal,
            activityLevel = ActivityLevel.fromName(activityLevelName),
            sleepNeedMinutes = sleepNeedMinutes,
            projectionMode = ProjectionMode.fromName(projectionModeName)
        )
    }

    /**
     * Save or update the user profile.
     * @param profile The user profile to save
     */
    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { preferences ->
            profile.ftpBike?.let { preferences[FTP_BIKE_KEY] = it } 
                ?: preferences.remove(FTP_BIKE_KEY)
            profile.maxHeartRate?.let { preferences[MAX_HEART_RATE_KEY] = it } 
                ?: preferences.remove(MAX_HEART_RATE_KEY)
            profile.defaultSwimTSS?.let { preferences[DEFAULT_SWIM_TSS_KEY] = it } 
                ?: preferences.remove(DEFAULT_SWIM_TSS_KEY)
            profile.defaultStrengthHeavyTSS?.let { preferences[DEFAULT_STRENGTH_HEAVY_TSS_KEY] = it } 
                ?: preferences.remove(DEFAULT_STRENGTH_HEAVY_TSS_KEY)
            profile.defaultStrengthLightTSS?.let { preferences[DEFAULT_STRENGTH_LIGHT_TSS_KEY] = it } 
                ?: preferences.remove(DEFAULT_STRENGTH_LIGHT_TSS_KEY)
            profile.goalDate?.let { preferences[GOAL_DATE_KEY] = it.toEpochDay() } 
                ?: preferences.remove(GOAL_DATE_KEY)
            profile.weeklyHoursGoal?.let { preferences[WEEKLY_HOURS_GOAL_KEY] = it } 
                ?: preferences.remove(WEEKLY_HOURS_GOAL_KEY)
            profile.annualVolumeGoalHours?.let { preferences[ANNUAL_VOLUME_GOAL_HOURS_KEY] = it }
                ?: preferences.remove(ANNUAL_VOLUME_GOAL_HOURS_KEY)
            profile.lthr?.let { preferences[LTHR_KEY] = it } 
                ?: preferences.remove(LTHR_KEY)
            profile.cssSecondsper100m?.let { preferences[CSS_SECONDS_PER_100M_KEY] = it } 
                ?: preferences.remove(CSS_SECONDS_PER_100M_KEY)
            profile.thresholdRunPace?.let { preferences[THRESHOLD_RUN_PACE_KEY] = it } 
                ?: preferences.remove(THRESHOLD_RUN_PACE_KEY)
            
            profile.weeklyAvailability?.let { map ->
                val stringMap = map.entries.associate { (day, types) ->
                    day.name to types.map { it.name }
                }
                preferences[WEEKLY_AVAILABILITY_KEY] = Json.encodeToString(stringMap)
            } ?: preferences.remove(WEEKLY_AVAILABILITY_KEY)

            profile.longTrainingDay?.let { preferences[LONG_TRAINING_DAY_KEY] = it.name }
                ?: preferences.remove(LONG_TRAINING_DAY_KEY)
            
            profile.strengthDays?.let { preferences[STRENGTH_DAYS_KEY] = it }
                ?: preferences.remove(STRENGTH_DAYS_KEY)

            profile.trainingBalance?.let { balance ->
                preferences[TRAINING_BALANCE_KEY] = Json.encodeToString(balance)
            } ?: preferences.remove(TRAINING_BALANCE_KEY)

            profile.biologicalSex?.let { preferences[BIOLOGICAL_SEX_KEY] = it.name }
                ?: preferences.remove(BIOLOGICAL_SEX_KEY)
            profile.birthDate?.let { preferences[BIRTH_DATE_KEY] = it.toEpochDay() }
                ?: preferences.remove(BIRTH_DATE_KEY)
            profile.heightCm?.let { preferences[HEIGHT_CM_KEY] = it }
                ?: preferences.remove(HEIGHT_CM_KEY)
            profile.proteinTargetG?.let { preferences[PROTEIN_TARGET_G_KEY] = it }
                ?: preferences.remove(PROTEIN_TARGET_G_KEY)
            profile.calorieTarget?.let { preferences[CALORIE_TARGET_KEY] = it }
                ?: preferences.remove(CALORIE_TARGET_KEY)

            profile.nutritionGoal?.let { preferences[NUTRITION_GOAL_KEY] = it.name }
                ?: preferences.remove(NUTRITION_GOAL_KEY)
            profile.goalRatePctPerWeek?.let { preferences[GOAL_RATE_PCT_PER_WEEK_KEY] = it }
                ?: preferences.remove(GOAL_RATE_PCT_PER_WEEK_KEY)
            profile.rmrOverrideKcal?.let { preferences[RMR_OVERRIDE_KCAL_KEY] = it }
                ?: preferences.remove(RMR_OVERRIDE_KCAL_KEY)
            profile.activityLevel?.let { preferences[ACTIVITY_LEVEL_KEY] = it.name }
                ?: preferences.remove(ACTIVITY_LEVEL_KEY)
            profile.sleepNeedMinutes?.let { preferences[SLEEP_NEED_MINUTES_KEY] = it }
                ?: preferences.remove(SLEEP_NEED_MINUTES_KEY)
            profile.projectionMode?.let { preferences[PROJECTION_MODE_KEY] = it.name }
                ?: preferences.remove(PROJECTION_MODE_KEY)
        }
    }

    /**
     * Delete the user profile (clears all profile fields).
     */
    suspend fun deleteUserProfile() {
        dataStore.edit { preferences ->
            preferences.remove(FTP_BIKE_KEY)
            preferences.remove(MAX_HEART_RATE_KEY)
            preferences.remove(DEFAULT_SWIM_TSS_KEY)
            preferences.remove(DEFAULT_STRENGTH_HEAVY_TSS_KEY)
            preferences.remove(DEFAULT_STRENGTH_LIGHT_TSS_KEY)
            preferences.remove(GOAL_DATE_KEY)
            preferences.remove(WEEKLY_HOURS_GOAL_KEY)
            preferences.remove(ANNUAL_VOLUME_GOAL_HOURS_KEY)
            preferences.remove(LTHR_KEY)
            preferences.remove(CSS_SECONDS_PER_100M_KEY)
            preferences.remove(THRESHOLD_RUN_PACE_KEY)
            preferences.remove(WEEKLY_AVAILABILITY_KEY)
            preferences.remove(LONG_TRAINING_DAY_KEY)
            preferences.remove(STRENGTH_DAYS_KEY)
            preferences.remove(TRAINING_BALANCE_KEY)
            preferences.remove(BIOLOGICAL_SEX_KEY)
            preferences.remove(BIRTH_DATE_KEY)
            preferences.remove(HEIGHT_CM_KEY)
            preferences.remove(PROTEIN_TARGET_G_KEY)
            preferences.remove(CALORIE_TARGET_KEY)
            preferences.remove(NUTRITION_GOAL_KEY)
            preferences.remove(GOAL_RATE_PCT_PER_WEEK_KEY)
            preferences.remove(RMR_OVERRIDE_KCAL_KEY)
            preferences.remove(ACTIVITY_LEVEL_KEY)
            preferences.remove(SLEEP_NEED_MINUTES_KEY)
            preferences.remove(PROJECTION_MODE_KEY)
        }
    }

    // ==================== Running Goal Operations ====================

    /**
     * Flow that emits the persisted active running goal.
     * Returns null when no running goal has been saved.
     */
    val activeRunningGoalFlow: Flow<RunningGoal?> = dataStore.data.map { preferences ->
        RunningGoalPreferencesCodec.decode(preferences[ACTIVE_RUNNING_GOAL_KEY])
    }

    /**
     * Get the persisted active running goal as a one-shot value.
     */
    suspend fun getActiveRunningGoal(): RunningGoal? {
        val preferences = dataStore.data.first()
        return RunningGoalPreferencesCodec.decode(preferences[ACTIVE_RUNNING_GOAL_KEY])
    }

    /**
     * Save or replace the active running goal.
     */
    suspend fun saveActiveRunningGoal(goal: RunningGoal) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_RUNNING_GOAL_KEY] = RunningGoalPreferencesCodec.encode(goal)
        }
    }

    /**
     * Clear the active running goal.
     */
    suspend fun clearActiveRunningGoal() {
        dataStore.edit { preferences ->
            preferences.remove(ACTIVE_RUNNING_GOAL_KEY)
        }
    }

    // ==================== Planner Auto-planner Settings Operations ====================

    val autoPlannerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_PLANNER_ENABLED_KEY]
            ?: preferences[LEGACY_IS_SMART_PLANNING_ENABLED_KEY]
            ?: true
    }

    suspend fun setAutoPlannerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_PLANNER_ENABLED_KEY] = enabled
            preferences.remove(LEGACY_IS_SMART_PLANNING_ENABLED_KEY)
        }
    }

    // Legacy aliases retained to avoid breaking existing callers during refactor rollout.
    val smartPlanningEnabledFlow: Flow<Boolean> = autoPlannerEnabledFlow
    suspend fun setSmartPlanningEnabled(enabled: Boolean) = setAutoPlannerEnabled(enabled)

    // ==================== Auto Strength Planner Operations ====================

    /** Whether the auto-planner injects strength sessions on an every-3rd-day cadence. */
    val autoPlanStrengthEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_PLAN_STRENGTH_ENABLED_KEY] ?: false
    }

    suspend fun setAutoPlanStrengthEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_PLAN_STRENGTH_ENABLED_KEY] = enabled
        }
    }

    /** Whether the running plan avoids strength days and prefers the day before them. */
    val runningConsidersStrengthFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RUNNING_CONSIDERS_STRENGTH_KEY] ?: false
    }

    suspend fun setRunningConsidersStrength(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RUNNING_CONSIDERS_STRENGTH_KEY] = enabled
        }
    }

    /**
     * The date of the first strength session. The every-3rd-day cadence counts from here.
     * Defaults to the next (or current) Monday when unset.
     */
    val strengthFirstWorkoutDateFlow: Flow<LocalDate> = dataStore.data.map { preferences ->
        preferences[STRENGTH_FIRST_WORKOUT_DATE_KEY]?.let { LocalDate.ofEpochDay(it) }
            ?: defaultStrengthFirstWorkoutDate()
    }

    suspend fun getStrengthFirstWorkoutDate(): LocalDate {
        val preferences = dataStore.data.first()
        return preferences[STRENGTH_FIRST_WORKOUT_DATE_KEY]?.let { LocalDate.ofEpochDay(it) }
            ?: defaultStrengthFirstWorkoutDate()
    }

    suspend fun setStrengthFirstWorkoutDate(date: LocalDate) {
        dataStore.edit { preferences ->
            preferences[STRENGTH_FIRST_WORKOUT_DATE_KEY] = date.toEpochDay()
        }
    }

    private fun defaultStrengthFirstWorkoutDate(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    
    /**
     * Check if sleep score backfill has been completed.
     */
    suspend fun isSleepScoreBackfillDone(): Boolean {
        return dataStore.data.first()[SLEEP_SCORE_BACKFILL_DONE_KEY] ?: false
    }
    
    /**
     * Mark sleep score backfill as completed.
     */
    suspend fun setSleepScoreBackfillDone(done: Boolean = true) {
        dataStore.edit { preferences ->
            preferences[SLEEP_SCORE_BACKFILL_DONE_KEY] = done
        }
    }

    /**
     * Get the timestamp (epoch millis) of the last automatic Health data sync,
     * or null if a sync has never run. Used to skip auto-sync when data is fresh.
     */
    suspend fun getHealthLastSyncMillis(): Long? {
        return dataStore.data.first()[HEALTH_LAST_SYNC_KEY]
    }

    /**
     * Record the timestamp (epoch millis) of the most recent Health data sync.
     */
    suspend fun setHealthLastSyncMillis(millis: Long) {
        dataStore.edit { preferences ->
            preferences[HEALTH_LAST_SYNC_KEY] = millis
        }
    }

    // ==================== Backup / Restore Operations ====================

    /**
     * Export every stored preference as a flat, type-tagged list.
     *
     * Reading the DataStore generically (rather than mapping each known key by hand) means
     * preferences added in the future are backed up automatically, with no risk of a new
     * setting silently falling out of the backup.
     *
     * Device-local sync bookkeeping is deliberately omitted — see [TRANSIENT_KEY_NAMES].
     */
    suspend fun exportAll(): List<PreferenceEntry> {
        val preferences = dataStore.data.first()
        return preferences.asMap()
            .filterKeys { it.name !in TRANSIENT_KEY_NAMES }
            .mapNotNull { (key, value) -> PreferenceEntry.of(key.name, value) }
            .sortedBy { it.key }
    }

    /**
     * Restore preferences produced by [exportAll].
     *
     * @param entries the entries to write.
     * @param replace when true, every preference not present in [entries] is cleared first;
     *   when false, existing preferences are kept and only the supplied keys are overwritten.
     * @return the number of entries applied. Entries with an unrecognised type tag are skipped
     *   rather than failing the whole restore.
     */
    suspend fun importAll(entries: List<PreferenceEntry>, replace: Boolean): Int {
        var applied = 0
        dataStore.edit { preferences ->
            if (replace) {
                // Preserve this device's sync bookkeeping across a replace-all restore, so a
                // restore doesn't re-trigger work that already ran on this phone.
                val retained = preferences.asMap()
                    .filterKeys { it.name in TRANSIENT_KEY_NAMES }
                    .mapNotNull { (key, value) -> PreferenceEntry.of(key.name, value) }
                preferences.clear()
                retained.forEach { applyEntry(preferences, it) }
            }
            entries.forEach { entry ->
                if (entry.key in TRANSIENT_KEY_NAMES) return@forEach
                if (applyEntry(preferences, entry)) applied++
            }
        }
        return applied
    }

    private fun applyEntry(preferences: MutablePreferences, entry: PreferenceEntry): Boolean {
        return when (entry.type) {
            PreferenceEntry.TYPE_BOOLEAN ->
                entry.value.toBooleanStrictOrNull()?.let { preferences[booleanPreferencesKey(entry.key)] = it } != null
            PreferenceEntry.TYPE_INT ->
                entry.value.toIntOrNull()?.let { preferences[intPreferencesKey(entry.key)] = it } != null
            PreferenceEntry.TYPE_LONG ->
                entry.value.toLongOrNull()?.let { preferences[longPreferencesKey(entry.key)] = it } != null
            PreferenceEntry.TYPE_FLOAT ->
                entry.value.toFloatOrNull()?.let { preferences[floatPreferencesKey(entry.key)] = it } != null
            PreferenceEntry.TYPE_DOUBLE ->
                entry.value.toDoubleOrNull()?.let { preferences[doublePreferencesKey(entry.key)] = it } != null
            PreferenceEntry.TYPE_STRING -> {
                preferences[stringPreferencesKey(entry.key)] = entry.value
                true
            }
            PreferenceEntry.TYPE_STRING_SET -> {
                val decoded = try {
                    Json.decodeFromString<Set<String>>(entry.value)
                } catch (e: Exception) {
                    null
                }
                decoded?.let { preferences[stringSetPreferencesKey(entry.key)] = it } != null
            }
            else -> false
        }
    }
}
