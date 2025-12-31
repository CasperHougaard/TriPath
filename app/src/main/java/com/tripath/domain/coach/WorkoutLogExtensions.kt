package com.tripath.domain.coach

import com.tripath.data.local.database.entities.WorkoutLog

/**
 * Extension property to determine if a workout is a commute.
 * 
 * Returns true if the workout title contains "commute", "transport", or "work" (case insensitive).
 * 
 * Note: WorkoutLog doesn't currently have a title field in the database schema.
 * This extension will return false as a placeholder until the title field is added.
 * When the title field is added, update this to check: this.title?.lowercase()?.contains(...)
 */
val WorkoutLog.isCommute: Boolean
    get() {
        // TODO: When WorkoutLog.title field is added, replace with:
        // return this.title?.lowercase()?.let { title ->
        //     title.contains("commute") || title.contains("transport") || title.contains("work")
        // } ?: false
        return false // Placeholder until title field is added
    }

/**
 * Extension property for Rate of Perceived Exertion (RPE).
 * 
 * Returns a default value of 5 (Moderate) for now, so calculations don't break.
 * 
 * TODO: When RPE is added as a field to WorkoutLog, replace this with the actual field.
 */
val WorkoutLog.rpe: Int
    get() = 5 // Default moderate RPE until RPE field is added


