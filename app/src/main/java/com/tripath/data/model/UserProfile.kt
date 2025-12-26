package com.tripath.data.model

import java.time.LocalDate

/**
 * Represents user settings and goals.
 * Stored in DataStore Preferences to persist across database migrations.
 */
data class UserProfile(
    /** Functional Threshold Power for cycling (watts) */
    val ftpBike: Int? = null,
    
    /** Maximum Heart Rate for TSS calculations (bpm) */
    val maxHeartRate: Int? = null,
    
    /** Default TSS for swimming per hour of activity */
    val defaultSwimTSS: Int? = 60,
    
    /** Default TSS for heavy strength sessions per hour */
    val defaultStrengthHeavyTSS: Int? = 60,
    
    /** Default TSS for light strength sessions per hour */
    val defaultStrengthLightTSS: Int? = 40,
    
    /** Target Ironman race date (2027 goal) */
    val goalDate: LocalDate? = null,
    
    /** Weekly training hours goal */
    val weeklyHoursGoal: Float? = null,
    
    /** Lactate Threshold Heart Rate for running (bpm) */
    val lthr: Int? = null,
    
    /** Critical Swim Speed in seconds per 100m */
    val cssSecondsper100m: Int? = null
)

