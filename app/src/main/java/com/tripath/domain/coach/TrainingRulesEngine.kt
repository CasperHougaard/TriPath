package com.tripath.domain.coach

import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.WorkoutType
import com.tripath.domain.health.EnergyAvailabilityBand
import com.tripath.domain.strain.ReadinessAssessment
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Training Rules Engine ("Iron Brain")
 * 
 * Validates training plans against user-defined rules and calculates readiness scores.
 * Reads configuration from PreferencesManager to apply user-customizable rules.
 */
@Singleton
class TrainingRulesEngine @Inject constructor(
    private val preferencesManager: PreferencesManager
) {

    /**
     * Calculate readiness status from multiple recovery metrics.
     * 
     * @param tsb Training Stress Balance (CTL - ATL)
     * @param sleepScore Sleep score (1-100) from Recovery Trends
     * @param soreness Subjective soreness (1-10 scale)
     * @param mood Subjective mood (1-10 scale)
     * @param allergy Allergy severity level
     * @return ReadinessStatus with score, color, and breakdown
     */
    suspend fun calculateReadiness(
        tsb: Int,
        sleepScore: Int?,
        soreness: Int?,
        mood: Int?,
        allergy: AllergySeverity
    ): ReadinessStatus {
        // TSB Component (50%)
        val tsbScore = when {
            tsb > 5 -> 100
            tsb < -30 -> 0
            else -> {
                // Linear interpolation: -30 -> 0, 5 -> 100
                val range = 5 - (-30) // 35
                val position = tsb - (-30) // 0 to 35
                ((position.toDouble() / range) * 100).toInt().coerceIn(0, 100)
            }
        }

        // Subjective Component (30%) - Average of soreness and mood
        val (subjectiveScore, subjectiveRawValue) = if (soreness != null && mood != null) {
            val avg = (soreness + mood) / 2.0
            val score = ((avg - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
            Pair(score, avg)
        } else if (soreness != null) {
            val score = ((soreness - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
            Pair(score, soreness.toDouble())
        } else if (mood != null) {
            val score = ((mood - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
            Pair(score, mood.toDouble())
        } else {
            Pair(50, null) // Default middle score if neither available
        }

        // Sleep Component (20%) - Use sleep score directly (already 0-100 scale)
        val sleepScoreComponent = sleepScore ?: 50 // Default middle score if not available

        // Calculate weighted score
        val weightedScore = (tsbScore * 0.5 + subjectiveScore * 0.3 + sleepScoreComponent * 0.2).roundToInt()

        // Apply allergy penalty
        val allergyPenalty = when (allergy) {
            AllergySeverity.MODERATE -> 10
            AllergySeverity.SEVERE -> 30
            else -> 0
        }

        val finalScore = (weightedScore - allergyPenalty).coerceIn(0, 100)

        // Determine color
        val color = when {
            finalScore > 75 -> ReadinessColor.GREEN
            finalScore >= 40 -> ReadinessColor.YELLOW
            else -> ReadinessColor.RED
        }

        // Build breakdown string with raw values → scores
        val breakdown = buildString {
            append("TSB: $tsb → $tsbScore")
            if (sleepScore != null) {
                append(", Sleep: $sleepScore")
            }
            if (subjectiveRawValue != null) {
                val subjectiveFormatted = String.format("%.1f", subjectiveRawValue)
                append(", Subjective: ${subjectiveFormatted}/10 → $subjectiveScore")
            }
        }

        return ReadinessStatus(
            score = finalScore,
            color = color,
            breakdown = breakdown,
            allergyPenalty = allergyPenalty
        )
    }

    /**
     * Calculate Structural Stress Score (SSS) for mechanical load monitoring.
     * 
     * @param distanceKm Distance in kilometers
     * @param avgZone Average zone number (1-5)
     * @return SSS value
     */
    fun calculateSSS(distanceKm: Double, avgZone: Int): Double {
        return distanceKm * (1.0 + (avgZone * 0.2))
    }

    /**
     * Validate a daily training plan against user-defined rules.
     * 
     * @param yesterday Completed workout from yesterday (if any)
     * @param todayPlan Planned workout for today (if any)
     * @param todayWellness Today's wellness log
     * @param lastStrengthDate Date of last strength session (if any)
     * @param currentPhase Current training phase
     * @param recentRuns List of recent run workouts (will be filtered to last 7 days, RUN type only)
     * @return List of warnings, empty if engine is disabled or no violations
     */
    suspend fun validateDailyPlan(
        yesterday: WorkoutLog?,
        todayPlan: WorkoutLog?,
        todayWellness: DailyWellnessLog,
        lastStrengthDate: LocalDate?,
        currentPhase: TrainingPhase,
        recentRuns: List<WorkoutLog>,
        /**
         * Today's readiness, when it can be computed. Null falls back to the date-and-type
         * heuristics below, so an install with no strain history behaves exactly as before.
         */
        readiness: ReadinessAssessment? = null,
        energyAvailability: EnergyAvailabilityBand = EnergyAvailabilityBand.UNKNOWN
    ): List<CoachWarning> {
        // Step 1: Check if Smart Planning is enabled
        val smartEnabled = preferencesManager.autoPlannerEnabledFlow.first()
        if (!smartEnabled) {
            return emptyList() // Engine is OFF
        }

        val warnings = mutableListOf<CoachWarning>()

        // Early return if no plan for today
        if (todayPlan == null) {
            return warnings
        }

        // Rule 3: is the tissue this session needs actually recovered?
        //
        // Supersedes the old "yesterday was strength, so go easy unless it's a swim" rule. That was
        // the same instinct expressed with the only information available at the time — a proxy for
        // tissue state. Now that per-channel strain exists, ask the real question, which both
        // catches what the proxy missed (a long run three days ago) and stops firing where it was
        // wrong (a heavy upper-body day has no bearing on a ride).
        val hasChannelData = readiness?.strain?.hasData == true
        if (hasChannelData) {
            warnings += ReadinessPlanRules.evaluate(
                plan = todayPlan.asPlanShape(),
                plannedZone = inferZoneFromWorkoutLog(todayPlan),
                readiness = readiness
            )
            warnings += ReadinessPlanRules.evaluateFuelling(
                readiness = readiness,
                energyAvailability = energyAvailability,
                weeklyRampPct = readiness?.weeklyLoadRampPct
            )
        } else if (yesterday?.type == WorkoutType.STRENGTH && todayPlan.type != WorkoutType.SWIM) {
            val todayZone = inferZoneFromWorkoutLog(todayPlan)
            if (todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RECOVERY_ADVICE,
                        title = "Post-Strength Protocol",
                        message = "Post-Strength Rule: Prefer recovery work only, such as walking or very easy aerobic movement.",
                        isBlocker = false
                    )
                )
            }
        }

        // Rule 4: Severe Allergy Protocol
        if (todayWellness.allergySeverity == AllergySeverity.SEVERE) {
            val todayZone = inferZoneFromWorkoutLog(todayPlan)
            if (todayPlan.type == WorkoutType.STRENGTH || todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.INJURY_RISK,
                        title = "Severe Allergy Active",
                        message = "Severe Allergy Active. Only Zone 1 Active Recovery allowed.",
                        isBlocker = true
                    )
                )
            }
        }

        return warnings
    }

    /**
     * The readiness rules describe a *planned* session, but this entry point is handed a
     * [WorkoutLog]. Only the type and load are read, so a minimal stand-in is enough and avoids
     * duplicating the rules for two shapes of the same thing.
     */
    private fun WorkoutLog.asPlanShape(): TrainingPlan = TrainingPlan(
        date = date,
        type = type,
        durationMinutes = durationMinutes,
        plannedTSS = computedTSS ?: 0
    )

    /**
     * Infer zone number from WorkoutLog data.
     * Uses zone distributions or falls back to TSS-based inference.
     * 
     * @param log WorkoutLog to analyze
     * @return Zone number (1-5), defaults to 2 if unable to determine
     */
    private fun inferZoneFromWorkoutLog(log: WorkoutLog): Int {
        // Try HR zone distribution first
        val hrZoneDistribution = log.hrZoneDistribution
        if (hrZoneDistribution != null && hrZoneDistribution.isNotEmpty()) {
            return calculateAverageZone(hrZoneDistribution)
        }

        // Try power zone distribution
        val powerZoneDistribution = log.powerZoneDistribution
        if (powerZoneDistribution != null && powerZoneDistribution.isNotEmpty()) {
            return calculateAverageZone(powerZoneDistribution)
        }

        // Fallback: Infer from TSS (rough estimate)
        val tss = log.computedTSS ?: return 2
        return when {
            tss < 30 -> 1 // Low intensity
            tss < 60 -> 2 // Moderate intensity
            tss < 90 -> 3 // Tempo
            tss < 120 -> 4 // Threshold
            else -> 5 // High intensity
        }
    }

    /**
     * Calculate average zone from zone distribution map.
     * Uses weighted average based on time spent in each zone.
     * 
     * @param distribution Map of zone names ("Z1", "Z2", etc.) to seconds
     * @return Average zone number (1-5)
     */
    private fun calculateAverageZone(distribution: Map<String, Int>): Int {
        var totalTime = 0
        var weightedSum = 0.0

        distribution.forEach { (zone, seconds) ->
            val zoneNumber = when (zone.uppercase()) {
                "Z1" -> 1
                "Z2" -> 2
                "Z3" -> 3
                "Z4" -> 4
                "Z5" -> 5
                else -> 0
            }
            if (zoneNumber > 0) {
                totalTime += seconds
                weightedSum += zoneNumber * seconds
            }
        }

        return if (totalTime > 0) {
            (weightedSum / totalTime).roundToInt().coerceIn(1, 5)
        } else {
            2 // Default to zone 2 if no data
        }
    }

    /**
     * Calculate 7-day rolling SSS sum for a list of run workouts.
     * 
     * @param runs List of run workouts (should be 7 days)
     * @return Total SSS for the week
     */
    private fun calculateWeekSSS(runs: List<WorkoutLog>): Double {
        return runs.sumOf { run ->
            val distanceKm = (run.distanceMeters ?: 0.0) / 1000.0
            if (distanceKm > 0) {
                val avgZone = inferZoneFromWorkoutLog(run)
                calculateSSS(distanceKm, avgZone)
            } else {
                0.0
            }
        }
    }

    /**
     * Validate a daily training plan for generator use (polymorphic - accepts both WorkoutLog and TrainingPlan).
     * Validates structural rules without requiring wellness data.
     * 
     * @param yesterday Completed workout from yesterday (can be WorkoutLog? or TrainingPlan?)
     * @param todayPlan Planned workout for today (TrainingPlan)
     * @param lastStrengthDate Date of last strength session (if any)
     * @param recentRuns List of recent run workouts (can be List<WorkoutLog> or List<TrainingPlan>)
     * @return List of warnings, empty if engine is disabled or no violations
     */
    suspend fun validateDailyPlanForGenerator(
        yesterday: Any?,
        todayPlan: TrainingPlan,
        lastStrengthDate: LocalDate?,
        recentRuns: List<Any>
    ): List<CoachWarning> {
        // Step 1: Check if Smart Planning is enabled
        val smartEnabled = preferencesManager.autoPlannerEnabledFlow.first()
        if (!smartEnabled) {
            return emptyList() // Engine is OFF
        }

        val warnings = mutableListOf<CoachWarning>()
        val yesterdayType = extractWorkoutType(yesterday)

        // Rule 3: Heavy Legs Protocol
        if (yesterdayType == WorkoutType.STRENGTH && todayPlan.type != WorkoutType.SWIM) {
            val todayZone = inferZoneFromTrainingPlan(todayPlan)
            if (todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RECOVERY_ADVICE,
                        title = "Post-Strength Protocol",
                        message = "Post-Strength Rule: Prefer recovery work only, such as walking or very easy aerobic movement.",
                        isBlocker = false
                    )
                )
            }
        }

        return warnings
    }

    /**
     * Extract workout type from polymorphic workout object (WorkoutLog or TrainingPlan).
     */
    private fun extractWorkoutType(any: Any?): WorkoutType? {
        return when (any) {
            is WorkoutLog -> any.type
            is TrainingPlan -> any.type
            else -> null
        }
    }

    /**
     * Extract date from polymorphic workout object (WorkoutLog or TrainingPlan).
     */
    private fun extractDate(any: Any?): LocalDate? {
        return when (any) {
            is WorkoutLog -> any.date
            is TrainingPlan -> any.date
            else -> null
        }
    }

    /**
     * Infer zone number from TrainingPlan data.
     * Uses subType field if available, otherwise defaults based on workout type.
     * 
     * @param plan TrainingPlan to analyze
     * @return Zone number (1-5), defaults to 2 if unable to determine
     */
    fun inferZoneFromTrainingPlan(plan: TrainingPlan): Int {
        // Try subType first
        val subType = plan.subType?.lowercase()
        if (subType != null) {
            when {
                subType.contains("tempo") || subType.contains("threshold") -> return 3
                subType.contains("interval") || subType.contains("vo2") || subType.contains("sprint") -> return 4
                subType.contains("easy") || subType.contains("recovery") || subType.contains("zone 1") -> return 1
                subType.contains("long") || subType.contains("endurance") -> return 2
            }
        }

        // Fallback: Infer from TSS (rough estimate)
        val tss = plan.plannedTSS
        return when {
            tss < 30 -> 1 // Low intensity
            tss < 60 -> 2 // Moderate intensity
            tss < 90 -> 3 // Tempo
            tss < 120 -> 4 // Threshold
            else -> 5 // High intensity
        }
    }
}

