package com.tripath.domain.coach

import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.WorkoutType
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
        recentRuns: List<WorkoutLog>
    ): List<CoachWarning> {
        // Step 1: Check if Smart Planning is enabled
        val smartEnabled = preferencesManager.smartPlanningEnabledFlow.first()
        if (!smartEnabled) {
            return emptyList() // Engine is OFF
        }

        // Step 2: Load other preferences
        val allowConsecutiveRuns = preferencesManager.runConsecutiveAllowedFlow.first()
        val strengthSpacingHours = preferencesManager.strengthSpacingHoursFlow.first()
        val monitorMechLoad = preferencesManager.mechanicalLoadMonitoringFlow.first()
        val allowCommuteExemption = preferencesManager.allowCommuteExemptionFlow.first()

        val warnings = mutableListOf<CoachWarning>()

        // Early return if no plan for today
        if (todayPlan == null) {
            return warnings
        }

        // Rule 1: Run Frequency (Plate Protection)
        if (!allowConsecutiveRuns && 
            yesterday?.type == WorkoutType.RUN && 
            todayPlan.type == WorkoutType.RUN) {
            
            // Exception: Skip if commute exemption is enabled and today is a commute
            if (!(allowCommuteExemption && todayPlan.isCommute)) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RULE_VIOLATION,
                        title = "Consecutive Runs Blocked",
                        message = "Running two days in a row is disabled in settings.",
                        isBlocker = true
                    )
                )
            }
        }

        // Rule 2: Strength Spacing
        if (todayPlan.type == WorkoutType.STRENGTH && lastStrengthDate != null) {
            val hoursSinceLastStrength = ChronoUnit.HOURS.between(
                lastStrengthDate.atStartOfDay(),
                LocalDate.now().atStartOfDay()
            )
            
            if (hoursSinceLastStrength < strengthSpacingHours) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RULE_VIOLATION,
                        title = "Strength Spacing Violation",
                        message = "Strength sessions must be ${strengthSpacingHours}h apart.",
                        isBlocker = true
                    )
                )
            }
        }

        // Rule 3: Heavy Legs Protocol
        if (yesterday?.type == WorkoutType.STRENGTH && todayPlan.type != WorkoutType.SWIM) {
            val todayZone = inferZoneFromWorkoutLog(todayPlan)
            if (todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RECOVERY_ADVICE,
                        title = "Post-Strength Protocol",
                        message = "Post-Strength Rule: Consider Swim or Zone 1 Spin only.",
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

        // Rule 5: Mechanical Load (SSS) Monitor
        if (monitorMechLoad) {
            val today = LocalDate.now()
            val fourteenDaysAgo = today.minusDays(14)
            
            // Filter recent runs to only RUN type from last 14 days (for comparison)
            val recentRunLogs = recentRuns.filter { log ->
                log.type == WorkoutType.RUN && 
                !log.date.isBefore(fourteenDaysAgo) && 
                !log.date.isAfter(today)
            }.sortedBy { it.date } // Sort by date for proper week comparison
            
            if (recentRunLogs.size >= 14) {
                // Calculate 7-day rolling SSS for current week (last 7 days)
                val currentWeekRuns = recentRunLogs.takeLast(7)
                val currentWeekSSS = calculateWeekSSS(currentWeekRuns)
                
                // Calculate 7-day rolling SSS for previous week (7-14 days ago)
                val previousWeekRuns = recentRunLogs.dropLast(7).takeLast(7)
                val previousWeekSSS = calculateWeekSSS(previousWeekRuns)
                
                if (previousWeekSSS > 0 && currentWeekSSS > previousWeekSSS * 1.15) {
                    warnings.add(
                        CoachWarning(
                            type = WarningType.INJURY_RISK,
                            title = "Mechanical Load Increase",
                            message = "Mechanical load increased >15% vs previous week. Consider reducing run volume.",
                            isBlocker = false
                        )
                    )
                }
            }
        }

        return warnings
    }

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
        val smartEnabled = preferencesManager.smartPlanningEnabledFlow.first()
        if (!smartEnabled) {
            return emptyList() // Engine is OFF
        }

        // Step 2: Load other preferences
        val allowConsecutiveRuns = preferencesManager.runConsecutiveAllowedFlow.first()
        val strengthSpacingHours = preferencesManager.strengthSpacingHoursFlow.first()
        val monitorMechLoad = preferencesManager.mechanicalLoadMonitoringFlow.first()
        val allowCommuteExemption = preferencesManager.allowCommuteExemptionFlow.first()

        val warnings = mutableListOf<CoachWarning>()

        // Rule 1: Run Frequency (Plate Protection)
        val yesterdayType = extractWorkoutType(yesterday)
        if (!allowConsecutiveRuns && 
            yesterdayType == WorkoutType.RUN && 
            todayPlan.type == WorkoutType.RUN) {
            
            // Exception: Skip if commute exemption is enabled and today is a commute
            // Note: TrainingPlan doesn't have isCommute, so we skip this check for plans
            // For now, we'll be conservative and block consecutive runs for plans
            warnings.add(
                CoachWarning(
                    type = WarningType.RULE_VIOLATION,
                    title = "Consecutive Runs Blocked",
                    message = "Running two days in a row is disabled in settings.",
                    isBlocker = true
                )
            )
        }

        // Rule 2: Strength Spacing
        if (todayPlan.type == WorkoutType.STRENGTH && lastStrengthDate != null) {
            val todayDate = todayPlan.date
            val hoursSinceLastStrength = ChronoUnit.HOURS.between(
                lastStrengthDate.atStartOfDay(),
                todayDate.atStartOfDay()
            )
            
            if (hoursSinceLastStrength < strengthSpacingHours) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RULE_VIOLATION,
                        title = "Strength Spacing Violation",
                        message = "Strength sessions must be ${strengthSpacingHours}h apart.",
                        isBlocker = true
                    )
                )
            }
        }

        // Rule 3: Heavy Legs Protocol
        if (yesterdayType == WorkoutType.STRENGTH && todayPlan.type != WorkoutType.SWIM) {
            val todayZone = inferZoneFromTrainingPlan(todayPlan)
            if (todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RECOVERY_ADVICE,
                        title = "Post-Strength Protocol",
                        message = "Post-Strength Rule: Consider Swim or Zone 1 Spin only.",
                        isBlocker = false
                    )
                )
            }
        }

        // Rule 5: Mechanical Load (SSS) Monitor
        if (monitorMechLoad) {
            val todayDate = todayPlan.date
            val fourteenDaysAgo = todayDate.minusDays(14)
            
            // Filter recent runs to only RUN type from last 14 days
            val recentRunLogs = recentRuns.mapNotNull { item ->
                when (item) {
                    is WorkoutLog -> {
                        if (item.type == WorkoutType.RUN && 
                            !item.date.isBefore(fourteenDaysAgo) && 
                            !item.date.isAfter(todayDate)) {
                            item
                        } else null
                    }
                    is TrainingPlan -> {
                        if (item.type == WorkoutType.RUN && 
                            !item.date.isBefore(fourteenDaysAgo) && 
                            !item.date.isAfter(todayDate)) {
                            // Convert TrainingPlan to WorkoutLog-like structure for SSS calculation
                            // We'll use estimated distance based on duration and type
                            null // Skip plans for now in SSS calculation (no distance data)
                        } else null
                    }
                    else -> null
                }
            }.filterIsInstance<WorkoutLog>().sortedBy { it.date }
            
            if (recentRunLogs.size >= 14) {
                // Calculate 7-day rolling SSS for current week (last 7 days)
                val currentWeekRuns = recentRunLogs.takeLast(7)
                val currentWeekSSS = calculateWeekSSS(currentWeekRuns)
                
                // Calculate 7-day rolling SSS for previous week (7-14 days ago)
                val previousWeekRuns = recentRunLogs.dropLast(7).takeLast(7)
                val previousWeekSSS = calculateWeekSSS(previousWeekRuns)
                
                if (previousWeekSSS > 0 && currentWeekSSS > previousWeekSSS * 1.15) {
                    warnings.add(
                        CoachWarning(
                            type = WarningType.INJURY_RISK,
                            title = "Mechanical Load Increase",
                            message = "Mechanical load increased >15% vs previous week. Consider reducing run volume.",
                            isBlocker = false
                        )
                    )
                }
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

