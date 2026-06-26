package com.tripath.domain.running

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Represents a user's running goal for the Coach Planner.
 * Baseline fields are included directly for MVP simplicity.
 */
data class RunningGoal(
    val type: RunningGoalType,
    val targetDistanceMeters: Int? = null,      // For COMPLETE_DISTANCE/ENDURANCE
    val targetDate: LocalDate? = null,          // For COMPLETE_DISTANCE/ENDURANCE
    val runsPerWeek: Int? = null,               // For CONSISTENCY/ENDURANCE
    val preferredDays: List<DayOfWeek>? = null, // Optional, for scheduling
    // Baseline fields (included directly)
    val baselineLongestRunMeters: Int? = null,  // User's current longest run
    val baselineWeeklyRunMeters: Int? = null,   // User's current weekly run volume
    // Progression safety (null = engine default 15%)
    val maxWeeklyProgressPercent: Float? = null
)
