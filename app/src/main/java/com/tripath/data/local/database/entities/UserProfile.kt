package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Represents user settings and goals.
 * This is a single-row table - only one profile exists at a time.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    /** Fixed ID to ensure single-row table */
    @PrimaryKey
    val id: Int = 1,
    
    /** Functional Threshold Power for cycling (watts) */
    val ftp: Int? = null,
    
    /** Target Ironman race date (2027 goal) */
    val goalDate: LocalDate? = null,
    
    /** Weekly training hours goal */
    val weeklyHoursGoal: Float? = null,
    
    /** Lactate Threshold Heart Rate for running (bpm) */
    val lthr: Int? = null,
    
    /** Critical Swim Speed in seconds per 100m */
    val cssSecondsper100m: Int? = null
)

