package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Represents a sleep session synced from Health Connect.
 * Used for recovery tracking and training load management.
 */
@Entity(tableName = "sleep_logs")
data class SleepLog(
    /** Unique identifier from Health Connect session metadata.id */
    @PrimaryKey
    val connectId: String,
    
    /** The date of the sleep session (start date) */
    val date: LocalDate,
    
    /** Start time of sleep in epoch milliseconds */
    val startTimeMillis: Long,
    
    /** End time of sleep in epoch milliseconds */
    val endTimeMillis: Long,
    
    /** Total sleep duration in minutes */
    val durationMinutes: Int,
    
    /** Title/notes from the sleep session (optional) */
    val title: String? = null,
    
    /** 
     * JSON serialized list of sleep stages.
     * Format: [{"stage": "deep", "startMillis": 123, "endMillis": 456}, ...]
     * Stages: awake, sleeping, out_of_bed, light, deep, rem, awake_in_bed, unknown
     */
    val stagesJson: String? = null,
    
    /** Time spent in deep sleep in minutes */
    val deepSleepMinutes: Int? = null,
    
    /** Time spent in light sleep in minutes */
    val lightSleepMinutes: Int? = null,
    
    /** Time spent in REM sleep in minutes */
    val remSleepMinutes: Int? = null,
    
    /** Time spent awake during the sleep session in minutes */
    val awakeMinutes: Int? = null,
    
    /** Timestamp when this record was imported into TriPath database */
    val importedAt: Long = System.currentTimeMillis()
)

