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
    val calorieTarget: Float? = null,

    /** What the athlete is trying to do with body mass. Drives every energy and macro target. */
    val nutritionGoal: NutritionGoal? = null,

    /**
     * Target rate of body-mass change, as a percentage of body mass per week (negative to lose).
     * Always read through [NutritionGoal.clampRate] so a rate left over from a previous goal
     * cannot survive a goal change.
     */
    val goalRatePctPerWeek: Float? = null,

    /**
     * A *measured* resting metabolic rate (indirect calorimetry), in kcal/day. Overrides every
     * prediction equation — see [com.tripath.domain.health.MetabolicModel.restingMetabolicRate].
     */
    val rmrOverrideKcal: Int? = null,

    /** Non-exercise activity level, used only on days with no step data. */
    val activityLevel: ActivityLevel? = null,

    /** Nightly sleep the athlete needs, in minutes. Drives the rolling sleep-debt term. */
    val sleepNeedMinutes: Int? = null,

    /** Where forward-looking figures get their future training from. */
    val projectionMode: ProjectionMode? = null
) {
    /** Age in whole years as of [today], or null when [birthDate] is unset. */
    fun ageOn(today: LocalDate = LocalDate.now()): Int? =
        birthDate?.let { java.time.Period.between(it, today).years }

    /** The active goal, defaulting to maintenance so the fuel model always has something to size to. */
    val effectiveGoal: NutritionGoal get() = nutritionGoal ?: NutritionGoal.DEFAULT

    /** The goal rate, clamped to what [effectiveGoal] permits. Percent of body mass per week. */
    val effectiveGoalRatePctPerWeek: Double
        get() = effectiveGoal.clampRate(goalRatePctPerWeek?.toDouble())

    val effectiveActivityLevel: ActivityLevel get() = activityLevel ?: ActivityLevel.DEFAULT

    val effectiveProjectionMode: ProjectionMode get() = projectionMode ?: ProjectionMode.DEFAULT

    /** Nightly sleep need in minutes, defaulting to 8 h. */
    val effectiveSleepNeedMinutes: Int get() = sleepNeedMinutes ?: DEFAULT_SLEEP_NEED_MINUTES

    companion object {
        /**
         * 8 hours. Milewski et al. (2014) found under 8 h associated with roughly 1.7× the injury
         * rate in athletes; Mah et al. (2011) found performance gains from extending toward it.
         */
        const val DEFAULT_SLEEP_NEED_MINUTES = 480
    }
}

