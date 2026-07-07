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

    /** Annual training volume goal in hours */
    val annualVolumeGoalHours: Float? = null,
    
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

    /** Biological sex — drives sex-specific healthy reference ranges (body-fat %, BMR). */
    val biologicalSex: BiologicalSex? = null,

    /** Birth date — age is derived from this so reference ranges stay current over time. */
    val birthDate: LocalDate? = null,

    /** Height in centimetres — needed for BMI and calorie estimates. */
    val heightCm: Int? = null,

    /** Daily protein target in grams — the primary nutrition goal (soft progress, no pass/fail). */
    val proteinTargetG: Float? = null,

    /** Optional daily calorie target in kcal — progress only shown when set. */
    val calorieTarget: Float? = null
) {
    /** Age in whole years as of [today], or null when [birthDate] is unset. */
    fun ageOn(today: LocalDate = LocalDate.now()): Int? =
        birthDate?.let { java.time.Period.between(it, today).years }
}

