package com.tripath.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.tripath.data.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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
        private val SYNC_DAYS_KEY = intPreferencesKey("sync_days_back")
        
        // UserProfile keys
        private val FTP_BIKE_KEY = intPreferencesKey("ftp_bike")
        private val MAX_HEART_RATE_KEY = intPreferencesKey("max_heart_rate")
        private val DEFAULT_SWIM_TSS_KEY = intPreferencesKey("default_swim_tss")
        private val DEFAULT_STRENGTH_HEAVY_TSS_KEY = intPreferencesKey("default_strength_heavy_tss")
        private val DEFAULT_STRENGTH_LIGHT_TSS_KEY = intPreferencesKey("default_strength_light_tss")
        private val GOAL_DATE_KEY = longPreferencesKey("goal_date") // Stored as epoch day
        private val WEEKLY_HOURS_GOAL_KEY = floatPreferencesKey("weekly_hours_goal")
        private val LTHR_KEY = intPreferencesKey("lthr")
        private val CSS_SECONDS_PER_100M_KEY = intPreferencesKey("css_seconds_per_100m")
        
        /** Default sync period in days */
        const val DEFAULT_SYNC_DAYS = 30
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

    // ==================== User Profile Operations ====================

    /**
     * Flow that emits the current user profile.
     * Returns null if no profile has been saved yet.
     */
    val userProfileFlow: Flow<UserProfile?> = dataStore.data.map { preferences ->
        val ftpBike = preferences[FTP_BIKE_KEY]
        val maxHeartRate = preferences[MAX_HEART_RATE_KEY]
        val defaultSwimTSS = preferences[DEFAULT_SWIM_TSS_KEY]
        val defaultStrengthHeavyTSS = preferences[DEFAULT_STRENGTH_HEAVY_TSS_KEY]
        val defaultStrengthLightTSS = preferences[DEFAULT_STRENGTH_LIGHT_TSS_KEY]
        val goalDateEpochDay = preferences[GOAL_DATE_KEY]
        val weeklyHoursGoal = preferences[WEEKLY_HOURS_GOAL_KEY]
        val lthr = preferences[LTHR_KEY]
        val cssSecondsper100m = preferences[CSS_SECONDS_PER_100M_KEY]

        // If no fields are set, return null
        if (ftpBike == null && maxHeartRate == null && defaultSwimTSS == null &&
            defaultStrengthHeavyTSS == null && defaultStrengthLightTSS == null &&
            goalDateEpochDay == null && weeklyHoursGoal == null && lthr == null &&
            cssSecondsper100m == null
        ) {
            null
        } else {
            UserProfile(
                ftpBike = ftpBike,
                maxHeartRate = maxHeartRate,
                defaultSwimTSS = defaultSwimTSS ?: 60,
                defaultStrengthHeavyTSS = defaultStrengthHeavyTSS ?: 60,
                defaultStrengthLightTSS = defaultStrengthLightTSS ?: 40,
                goalDate = goalDateEpochDay?.let { LocalDate.ofEpochDay(it) },
                weeklyHoursGoal = weeklyHoursGoal,
                lthr = lthr,
                cssSecondsper100m = cssSecondsper100m
            )
        }
    }

    /**
     * Get the user profile as a one-shot value.
     * Returns null if no profile has been saved yet.
     */
    suspend fun getUserProfile(): UserProfile? {
        val preferences = dataStore.data.first()
        val ftpBike = preferences[FTP_BIKE_KEY]
        val maxHeartRate = preferences[MAX_HEART_RATE_KEY]
        val defaultSwimTSS = preferences[DEFAULT_SWIM_TSS_KEY]
        val defaultStrengthHeavyTSS = preferences[DEFAULT_STRENGTH_HEAVY_TSS_KEY]
        val defaultStrengthLightTSS = preferences[DEFAULT_STRENGTH_LIGHT_TSS_KEY]
        val goalDateEpochDay = preferences[GOAL_DATE_KEY]
        val weeklyHoursGoal = preferences[WEEKLY_HOURS_GOAL_KEY]
        val lthr = preferences[LTHR_KEY]
        val cssSecondsper100m = preferences[CSS_SECONDS_PER_100M_KEY]

        // If no fields are set, return null
        return if (ftpBike == null && maxHeartRate == null && defaultSwimTSS == null &&
            defaultStrengthHeavyTSS == null && defaultStrengthLightTSS == null &&
            goalDateEpochDay == null && weeklyHoursGoal == null && lthr == null &&
            cssSecondsper100m == null
        ) {
            null
        } else {
            UserProfile(
                ftpBike = ftpBike,
                maxHeartRate = maxHeartRate,
                defaultSwimTSS = defaultSwimTSS ?: 60,
                defaultStrengthHeavyTSS = defaultStrengthHeavyTSS ?: 60,
                defaultStrengthLightTSS = defaultStrengthLightTSS ?: 40,
                goalDate = goalDateEpochDay?.let { LocalDate.ofEpochDay(it) },
                weeklyHoursGoal = weeklyHoursGoal,
                lthr = lthr,
                cssSecondsper100m = cssSecondsper100m
            )
        }
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
            profile.lthr?.let { preferences[LTHR_KEY] = it } 
                ?: preferences.remove(LTHR_KEY)
            profile.cssSecondsper100m?.let { preferences[CSS_SECONDS_PER_100M_KEY] = it } 
                ?: preferences.remove(CSS_SECONDS_PER_100M_KEY)
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
            preferences.remove(LTHR_KEY)
            preferences.remove(CSS_SECONDS_PER_100M_KEY)
        }
    }
}

