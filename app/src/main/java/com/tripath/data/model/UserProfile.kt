package com.tripath.data.model

import java.time.DayOfWeek
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
    val defaultStrengthLightTSS: Int? = 30,
    
    /** Target Ironman race date (2027 goal) */
    val goalDate: LocalDate? = null,
    
    /** Weekly training hours goal */
    val weeklyHoursGoal: Float? = null,
    
    /** Lactate Threshold Heart Rate for running (bpm) */
    val lthr: Int? = null,
    
    /** Critical Swim Speed in seconds per 100m */
    val cssSecondsper100m: Int? = null,

    /** Threshold Run Pace in seconds per km */
    val thresholdRunPace: Int? = null,

    /** Map of DayOfWeek to allowed WorkoutTypes */
    val weeklyAvailability: Map<DayOfWeek, List<WorkoutType>>? = null,

    /** Preferred day for long sessions */
    val longTrainingDay: DayOfWeek? = DayOfWeek.SUNDAY,

    /** Number of strength sessions per week */
    val strengthDays: Int? = 2,

    /** Desired distribution of TSS across disciplines */
    val trainingBalance: TrainingBalance? = TrainingBalance.IRONMAN_BASE,
    
    /** Weekly schedule anchors: Map of DayOfWeek to AnchorType */
    val weeklySchedule: Map<DayOfWeek, AnchorType>? = null
) {
    companion object {
        /**
         * Default weekly schedule template.
         * Monday: Rest, Tuesday: Strength, Wednesday: Rest, Thursday: Strength,
         * Friday: Rest, Saturday: Bike, Sunday: Long Run
         */
        val DEFAULT_WEEKLY_SCHEDULE = mapOf(
            DayOfWeek.MONDAY to AnchorType.NONE,
            DayOfWeek.TUESDAY to AnchorType.STRENGTH,
            DayOfWeek.WEDNESDAY to AnchorType.NONE,
            DayOfWeek.THURSDAY to AnchorType.STRENGTH,
            DayOfWeek.FRIDAY to AnchorType.NONE,
            DayOfWeek.SATURDAY to AnchorType.BIKE,
            DayOfWeek.SUNDAY to AnchorType.LONG_RUN
        )
    }
}

