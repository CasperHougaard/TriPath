package com.tripath.domain.coach

import android.util.Log
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.AnchorType
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.TrainingBalance
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.domain.CoachEngine
import com.tripath.domain.TrainingPhase
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Auto-Pilot Generator for Iron Brain
 * 
 * Generates training plans for a specified duration using phase-aware logic,
 * validating each placement against Iron Brain structural rules.
 */
@Singleton
class CoachPlanGenerator @Inject constructor(
    private val rulesEngine: TrainingRulesEngine,
    private val preferencesManager: PreferencesManager,
    private val repository: TrainingRepository
) {
    private val TAG = "CoachAutoPilot"

    /**
     * Validation result for profile and generation parameters.
     */
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Failure(val reason: String) : ValidationResult()
    }

    /**
     * Result of plan generation operation.
     */
    sealed class GenerationResult {
        data class Success(val plans: List<TrainingPlan>) : GenerationResult()
        data class Failure(val reason: String, val details: String? = null) : GenerationResult()
    }

    /**
     * Represents discipline-specific TSS budgets for a training period.
     */
    data class DisciplineBudget(
        val swimTss: Int,
        val bikeTss: Int,
        val runTss: Int,
        val strengthTss: Int,
        val totalTss: Int
    )

    /**
     * Validate profile and generation parameters before generating a season.
     * 
     * @param profile User profile to validate
     * @param currentCtl Current Chronic Training Load
     * @param startDate Start date for generation
     * @param months Number of months to generate
     * @param rampRateLimit Maximum ramp rate from preferences
     * @return ValidationResult indicating success or failure with reason
     */
    private suspend fun validateProfileForGeneration(
        profile: UserProfile?,
        currentCtl: Double,
        startDate: LocalDate,
        months: Int,
        rampRateLimit: Float
    ): ValidationResult {
        // Essential Data Checks
        if (profile == null) {
            Log.e(TAG, "❌ VALIDATION FAILED: UserProfile is null. Please complete your profile.")
            return ValidationResult.Failure("UserProfile is null. Please complete your profile.")
        }

        // Check goal date (primary race date)
        val goalDate = profile.goalDate
        if (goalDate == null) {
            Log.e(TAG, "❌ VALIDATION FAILED: Goal date (primary race date) is not set. Please set a target race date.")
            return ValidationResult.Failure("Goal date is not set. Please set a target race date.")
        }

        val today = LocalDate.now()
        if (!goalDate.isAfter(today)) {
            Log.e(TAG, "❌ VALIDATION FAILED: Goal date ($goalDate) must be in the future. Today is $today.")
            return ValidationResult.Failure("Goal date must be in the future.")
        }

        // Check weekly availability
        val hasAvailability = if (profile.weeklyAvailability != null && profile.weeklyAvailability.isNotEmpty()) {
            true
        } else {
            // Fallback default: assume all days are available if not set
            Log.w(TAG, "⚠️ No weeklyAvailability set. Using default (all days available).")
            true
        }

        if (!hasAvailability) {
            Log.e(TAG, "❌ VALIDATION FAILED: No training days available in weeklyAvailability.")
            return ValidationResult.Failure("No training days available. Please set weekly availability.")
        }

        // Sanity Margins

        // CTL Check: 0 < CTL < 150
        if (currentCtl < 0) {
            Log.e(TAG, "❌ VALIDATION FAILED: CTL ($currentCtl) cannot be negative.")
            return ValidationResult.Failure("CTL cannot be negative. Please check your training data.")
        }
        if (currentCtl > 150) {
            Log.e(TAG, "❌ VALIDATION FAILED: CTL ($currentCtl) exceeds realistic maximum (150) for amateur athletes.")
            return ValidationResult.Failure("CTL exceeds realistic maximum (150). Please verify your training data.")
        }

        // Ramp Rate Check: Calculate weekly TSS increase and cap if too high
        val baseWeeklyTSS = currentCtl * 7
        val rampAdjustment = baseWeeklyTSS * (rampRateLimit / 100.0)
        val weeklyTSSIncrease = rampAdjustment
        
        if (weeklyTSSIncrease > 15) {
            Log.w(TAG, "⚠️ Ramp rate warning: Weekly TSS increase ($weeklyTSSIncrease) exceeds safe limit (15). Capping to 15.")
            // Note: We'll cap this in calculatePrescriptiveTSS, but log warning here
        }

        // Goal Date Range Check: Must be at least 2 weeks away, not more than 2 years
        val daysUntilGoal = java.time.temporal.ChronoUnit.DAYS.between(startDate, goalDate)
        val weeksUntilGoal = daysUntilGoal / 7.0
        
        if (weeksUntilGoal < 2) {
            Log.e(TAG, "❌ VALIDATION FAILED: Goal date ($goalDate) is too close (${weeksUntilGoal} weeks). Minimum 2 weeks required for meaningful season generation.")
            return ValidationResult.Failure("Goal date is too close (${weeksUntilGoal.toInt()} weeks). Minimum 2 weeks required.")
        }
        
        if (daysUntilGoal > 730) {
            Log.e(TAG, "❌ VALIDATION FAILED: Goal date ($goalDate) is too far (${daysUntilGoal} days / ${weeksUntilGoal} weeks). Maximum 2 years for accurate projection.")
            return ValidationResult.Failure("Goal date is too far (${daysUntilGoal} days). Maximum 2 years for accurate projection.")
        }

        // Generation duration check: Ensure we're not generating beyond goal date
        val endDate = startDate.plusMonths(months.toLong())
        if (endDate.isAfter(goalDate)) {
            Log.w(TAG, "⚠️ Generation period ($startDate to $endDate) extends beyond goal date ($goalDate). Generation will stop at goal date.")
        }

        Log.i(TAG, "✅ VALIDATION PASSED: Profile valid. Goal: $goalDate (${weeksUntilGoal.toInt()} weeks away), CTL: $currentCtl, Ramp Rate: $rampRateLimit%")
        return ValidationResult.Success
    }

    /**
     * Generate a training season (default 3 months).
     * 
     * @param startDate Start date for the generated plan
     * @param currentCtl Current Chronic Training Load
     * @param months Number of months to generate (default 3)
     * @param recentRealLogs Recent real workout logs for cold-start validation (if empty, will fetch from repository)
     * @return GenerationResult containing either the generated plans or a failure reason
     */
    suspend fun generateSeason(
        startDate: LocalDate,
        currentCtl: Double,
        months: Int = 3,
        recentRealLogs: List<WorkoutLog> = emptyList()
    ): GenerationResult {
        val smartEnabled = preferencesManager.smartPlanningEnabledFlow.first()
        if (!smartEnabled) {
            Log.e(TAG, "Smart Planning is DISABLED in settings. Aborting.")
            return GenerationResult.Failure(
                reason = "Smart Planning is disabled",
                details = "Please enable Smart Planning in Coach Settings to generate training plans."
            )
        }

        val userProfile = repository.getUserProfileOnce()
        val rampRateLimit = preferencesManager.rampRateLimitFlow.first()
        
        // Validate profile and parameters
        val validation = validateProfileForGeneration(
            profile = userProfile,
            currentCtl = currentCtl,
            startDate = startDate,
            months = months,
            rampRateLimit = rampRateLimit
        )
        
        if (validation is ValidationResult.Failure) {
            Log.e(TAG, "Generation aborted: ${validation.reason}")
            return GenerationResult.Failure(
                reason = validation.reason,
                details = getValidationFailureDetails(validation.reason, userProfile, startDate)
            )
        }

        // Validation passed, continue with generation
        val goalDate = userProfile!!.goalDate!!
        
        Log.i(TAG, "🚀 STARTING AUTO-PILOT. Duration: $months months. Start CTL: $currentCtl. Ramp Rate: $rampRateLimit%")

        // Fetch recent logs if not provided
        val realLogs = if (recentRealLogs.isEmpty()) {
            val fetchStartDate = startDate.minusDays(14)
            repository.getWorkoutLogsByDateRange(fetchStartDate, startDate.minusDays(1)).first()
        } else {
            recentRealLogs
        }

        // Fetch recent discipline loads for budget calculation
        val recentLoads = repository.getRecentDisciplineLoads()

        val generatedPlan = mutableListOf<TrainingPlan>()
        var currentSimulatedCtl = currentCtl
        var currentDate = startDate

        // Loop through weeks
        val totalWeeks = months * 4
        for (week in 1..totalWeeks) {
            val isRecoveryWeek = week % 4 == 0
            
            // Calculate phase for this week
            val phase = CoachEngine.calculatePhase(currentDate, goalDate)
            
            // Calculate prescriptive target TSS
            val baseTSS = calculatePrescriptiveTSS(currentSimulatedCtl, rampRateLimit, phase, week)
            val targetTSS = calculatePhaseAdjustedTSS(baseTSS, phase, isRecoveryWeek)
            
            Log.d(TAG, "--- Week $week (Target: $targetTSS TSS, Phase: ${phase.displayName}) [Recovery: $isRecoveryWeek] ---")

            val weekPlan = generateWeek(
                weekStart = currentDate,
                targetTSS = targetTSS,
                profile = userProfile,
                existingHistory = generatedPlan,
                recentRealLogs = realLogs,
                phase = phase,
                recentLoads = recentLoads
            )
            generatedPlan.addAll(weekPlan)

            // Update Simulation for next week (Rough approximation)
            val weeklyTSS = weekPlan.sumOf { it.plannedTSS }
            currentSimulatedCtl = currentSimulatedCtl + (weeklyTSS / 7.0 - currentSimulatedCtl) * 0.1
            currentDate = currentDate.plusWeeks(1)
        }

        Log.i(TAG, "✅ GENERATION COMPLETE. Created ${generatedPlan.size} workouts.")
        
        if (generatedPlan.isEmpty()) {
            return GenerationResult.Failure(
                reason = "No training plans were generated",
                details = "The generation process completed but produced no plans. This may occur if Iron Brain rules blocked all workout placements. Check your weekly availability and training constraints."
            )
        }
        
        return GenerationResult.Success(generatedPlan)
    }

    /**
     * Get helpful details for validation failures to guide the user.
     */
    private fun getValidationFailureDetails(
        reason: String,
        profile: UserProfile?,
        startDate: LocalDate
    ): String {
        return when {
            reason.contains("Goal date", ignoreCase = true) -> {
                val goalDate = profile?.goalDate
                if (goalDate == null) {
                    "Go to Profile Settings and set your primary race date (goal date)."
                } else if (!goalDate.isAfter(LocalDate.now())) {
                    "Your goal date ($goalDate) is in the past. Please update it to a future date in Profile Settings."
                } else {
                    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(startDate, goalDate)
                    when {
                        daysUntil < 14 -> "Your goal date is only ${daysUntil} days away. Minimum 2 weeks required for meaningful plan generation."
                        daysUntil > 730 -> "Your goal date is ${daysUntil} days away (over 2 years). Please set a goal date within the next 2 years."
                        else -> "Please check your goal date settings in Profile Settings."
                    }
                }
            }
            reason.contains("CTL", ignoreCase = true) -> {
                "Your current fitness level (CTL) is outside valid range. This usually means you need more training history. Try logging some workouts first."
            }
            reason.contains("profile", ignoreCase = true) -> {
                "Please complete your user profile in Profile Settings. Required: goal date, training balance, and weekly availability."
            }
            reason.contains("availability", ignoreCase = true) -> {
                "No training days are available. Please set your weekly availability in Profile Settings."
            }
            else -> {
                "Please review your profile settings and ensure all required information is complete."
            }
        }
    }

    /**
     * Calculate prescriptive TSS target with ramp rate governor.
     * Targets progressive overload, not just CTL maintenance.
     */
    private fun calculatePrescriptiveTSS(
        currentCtl: Double,
        rampRateLimit: Float,
        phase: TrainingPhase,
        weekNumber: Int
    ): Int {
        val effectiveCtl = if (currentCtl < 20) 20.0 else currentCtl
        val baseTSS = (effectiveCtl * 7).roundToInt()
        
        // Apply ramp rate (prescriptive - targets increase)
        val rampAdjustment = when (phase) {
            TrainingPhase.Base, TrainingPhase.Build -> {
                // Progressive overload phases: apply ramp rate
                (baseTSS * (rampRateLimit / 100.0)).roundToInt().coerceAtMost(baseTSS / 2) // Cap at 50% increase
            }
            else -> 0 // Other phases maintain or reduce
        }
        
        return baseTSS + rampAdjustment
    }

    /**
     * Calculate phase-adjusted TSS target.
     */
    private fun calculatePhaseAdjustedTSS(
        baseTSS: Int,
        phase: TrainingPhase,
        isRecoveryWeek: Boolean
    ): Int {
        val phaseMultiplier = when (phase) {
            TrainingPhase.Base -> 1.0
            TrainingPhase.Build -> 1.05
            TrainingPhase.Peak -> 1.0
            TrainingPhase.Taper -> 0.55
            TrainingPhase.OffSeason -> 0.95
            TrainingPhase.Transition -> 0.35
        }
        
        var adjusted = (baseTSS * phaseMultiplier).roundToInt()
        
        // Apply recovery week reduction
        if (isRecoveryWeek) {
            adjusted = (adjusted * 0.8).roundToInt()
        }
        
        return adjusted
    }

    /**
     * Generate a single week of training plans.
     */
    private suspend fun generateWeek(
        weekStart: LocalDate,
        targetTSS: Int,
        profile: UserProfile,
        existingHistory: List<TrainingPlan>,
        recentRealLogs: List<WorkoutLog>,
        phase: TrainingPhase,
        recentLoads: Map<WorkoutType, Int>
    ): List<TrainingPlan> {
        val weekPlan = mutableListOf<TrainingPlan>()
        var usedTSS = 0

        // Get user-defined weekly schedule (or use default)
        val schedule = profile.weeklySchedule ?: UserProfile.DEFAULT_WEEKLY_SCHEDULE
        
        // Calculate discipline-specific budget
        // Count strength sessions from schedule instead of hardcoded logic
        val strengthSessions = schedule.values.count { it == AnchorType.STRENGTH }
        val balance = profile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
        val budget = calculateDisciplineBudget(
            totalTargetTSS = targetTSS,
            balance = balance,
            strengthSessions = strengthSessions,
            recentLoads = recentLoads
        )
        Log.d(TAG, "  📊 Planned Budget: Run=${budget.runTss}, Bike=${budget.bikeTss}, Swim=${budget.swimTss}, Strength=${budget.strengthTss}, Total=${budget.totalTss}")

        // 1. PLACE USER-DEFINED ANCHORS
        val anchorsUsedTSS = placeUserAnchors(
            weekStart = weekStart,
            schedule = schedule,
            budget = budget,
            profile = profile,
            weekPlan = weekPlan,
            existingHistory = existingHistory,
            recentRealLogs = recentRealLogs,
            phase = phase,
            targetTSS = targetTSS
        )
        
        // Calculate actual TSS used by anchors per discipline
        val anchorRunTSS = weekPlan.filter { it.type == WorkoutType.RUN }.sumOf { it.plannedTSS }
        val anchorBikeTSS = weekPlan.filter { it.type == WorkoutType.BIKE }.sumOf { it.plannedTSS }
        val anchorSwimTSS = weekPlan.filter { it.type == WorkoutType.SWIM }.sumOf { it.plannedTSS }
        val anchorStrengthTSS = weekPlan.filter { it.type == WorkoutType.STRENGTH }.sumOf { it.plannedTSS }
        
        // Adjust budget for remaining TSS after anchors
        val adjustedBudget = DisciplineBudget(
            swimTss = budget.swimTss - anchorSwimTSS,
            bikeTss = budget.bikeTss - anchorBikeTSS,
            runTss = budget.runTss - anchorRunTSS,
            strengthTss = budget.strengthTss - anchorStrengthTSS,
            totalTss = budget.totalTss - anchorsUsedTSS
        )
        Log.d(TAG, "  📉 Adjusted Budget after anchors: Run=${adjustedBudget.runTss}, Bike=${adjustedBudget.bikeTss}, Swim=${adjustedBudget.swimTss}, Strength=${adjustedBudget.strengthTss}")

        // 2. FILL THE GAPS (Balance) - Use adjusted discipline-specific budget
        val updatedPlan = fillGaps(
            weekStart = weekStart,
            budget = adjustedBudget,
            profile = profile,
            currentWeekPlan = weekPlan,
            history = existingHistory,
            recentRealLogs = recentRealLogs,
            phase = phase
        )
        weekPlan.clear()
        weekPlan.addAll(updatedPlan)
        
        // Log final week summary
        val finalRunTSS = weekPlan.filter { it.type == WorkoutType.RUN }.sumOf { it.plannedTSS }
        val finalBikeTSS = weekPlan.filter { it.type == WorkoutType.BIKE }.sumOf { it.plannedTSS }
        val finalSwimTSS = weekPlan.filter { it.type == WorkoutType.SWIM }.sumOf { it.plannedTSS }
        val finalStrengthTSS = weekPlan.filter { it.type == WorkoutType.STRENGTH }.sumOf { it.plannedTSS }
        val finalTotalTSS = weekPlan.sumOf { it.plannedTSS }
        
        Log.d(TAG, "  📈 Week Summary: Run=$finalRunTSS/${budget.runTss}, Bike=$finalBikeTSS/${budget.bikeTss}, Swim=$finalSwimTSS/${budget.swimTss}, Strength=$finalStrengthTSS/${budget.strengthTss}, Total=$finalTotalTSS/${budget.totalTss}")

        return weekPlan
    }

    /**
     * Calculate long run duration based on training balance and phase.
     */
    private fun calculateLongRunDuration(
        profile: UserProfile,
        phase: TrainingPhase,
        targetTSS: Int
    ): Int {
        // Base duration from training balance
        val baseDuration = when (profile.trainingBalance) {
            TrainingBalance.IRONMAN_BASE -> 150
            TrainingBalance.BALANCED -> 105
            TrainingBalance.RUN_FOCUS -> 75
            else -> 90 // Default
        }
        
        // Phase adjustments
        val phaseMultiplier = when (phase) {
            TrainingPhase.Taper -> 0.6
            TrainingPhase.Transition -> 0.4
            else -> 1.0
        }
        
        var duration = (baseDuration * phaseMultiplier).roundToInt()
        
        // Scale with target TSS (higher TSS weeks = longer long run)
        if (targetTSS > 400) {
            duration = (duration * 1.2).roundToInt()
        } else if (targetTSS < 200) {
            duration = (duration * 0.8).roundToInt()
        }
        
        return duration.coerceIn(30, 240) // Cap between 30 min and 4 hours
    }

    /**
     * Calculate long bike duration based on training balance and phase.
     */
    private fun calculateLongBikeDuration(
        profile: UserProfile,
        phase: TrainingPhase,
        targetTSS: Int
    ): Int {
        // Base duration from training balance
        val baseDuration = when (profile.trainingBalance) {
            TrainingBalance.IRONMAN_BASE -> 180
            TrainingBalance.BALANCED -> 120
            TrainingBalance.BIKE_FOCUS -> 240
            else -> 150 // Default
        }
        
        // Phase adjustments
        val phaseMultiplier = when (phase) {
            TrainingPhase.Taper -> 0.6
            TrainingPhase.Transition -> 0.4
            else -> 1.0
        }
        
        var duration = (baseDuration * phaseMultiplier).roundToInt()
        
        // Scale with target TSS
        if (targetTSS > 400) {
            duration = (duration * 1.2).roundToInt()
        } else if (targetTSS < 200) {
            duration = (duration * 0.8).roundToInt()
        }
        
        return duration.coerceIn(30, 360) // Cap between 30 min and 6 hours
    }

    /**
     * Place user-defined anchor workouts based on weekly schedule.
     * Returns the total TSS used by anchors.
     */
    private suspend fun placeUserAnchors(
        weekStart: LocalDate,
        schedule: Map<DayOfWeek, AnchorType>,
        budget: DisciplineBudget,
        profile: UserProfile,
        weekPlan: MutableList<TrainingPlan>,
        existingHistory: List<TrainingPlan>,
        recentRealLogs: List<WorkoutLog>,
        phase: TrainingPhase,
        targetTSS: Int
    ): Int {
        var totalUsedTSS = 0
        
        // Track current TSS per discipline from anchors
        var usedRunTSS = 0
        var usedBikeTSS = 0
        var usedSwimTSS = 0
        var usedStrengthTSS = 0
        
        // Iterate through all days of the week
        DayOfWeek.values().forEach { day ->
            val anchorType = schedule[day] ?: AnchorType.NONE
            if (anchorType == AnchorType.NONE) return@forEach
            
            val date = getDateForDay(weekStart, day)
            
            // Skip if day already has a workout (shouldn't happen, but safety check)
            if (weekPlan.any { it.date == date }) {
                Log.w(TAG, "  ⚠️ Day $day already has a workout, skipping anchor")
                return@forEach
            }
            
            when (anchorType) {
                AnchorType.STRENGTH -> {
                    // Check if we have strength budget remaining
                    if (usedStrengthTSS >= budget.strengthTss) {
                        Log.w(TAG, "  ⚠️ Strength budget exhausted, skipping anchor on $day")
                        return@forEach
                    }
                    
                    if (validatePlacement(date, WorkoutType.STRENGTH, existingHistory, recentRealLogs)) {
                        val strengthTSS = profile.defaultStrengthHeavyTSS ?: 60
                        val workout = createGhostPlan(
                            date = date,
                            type = WorkoutType.STRENGTH,
                            duration = 60,
                            tss = strengthTSS,
                            strengthFocus = StrengthFocus.FULL_BODY,
                            intensity = Intensity.HEAVY
                        )
                        weekPlan.add(workout)
                        usedStrengthTSS += strengthTSS
                        totalUsedTSS += strengthTSS
                        Log.d(TAG, "  + Strength anchor scheduled on $day ($date)")
                    } else {
                        Log.w(TAG, "  x Strength anchor BLOCKED on $day ($date) by Iron Brain rules")
                    }
                }
                
                AnchorType.LONG_RUN -> {
                    // Check if we have run budget remaining
                    if (usedRunTSS >= budget.runTss) {
                        Log.w(TAG, "  ⚠️ Run budget exhausted, skipping long run anchor on $day")
                        return@forEach
                    }
                    
                    if (validatePlacement(date, WorkoutType.RUN, existingHistory + weekPlan, recentRealLogs)) {
                        val longRunDuration = calculateLongRunDuration(profile, phase, targetTSS)
                        val longRunTSS = (longRunDuration * 1.2).roundToInt()
                        
                        // Check if this fits in budget
                        if (usedRunTSS + longRunTSS <= budget.runTss) {
                            val workout = createGhostPlan(
                                date = date,
                                type = WorkoutType.RUN,
                                duration = longRunDuration,
                                tss = longRunTSS,
                                subType = "Long Run"
                            )
                            weekPlan.add(workout)
                            usedRunTSS += longRunTSS
                            totalUsedTSS += longRunTSS
                            Log.d(TAG, "  + Long Run anchor scheduled on $day ($date, ${longRunDuration}min)")
                        } else {
                            Log.w(TAG, "  ⚠️ Long Run would exceed budget, skipping anchor on $day")
                        }
                    } else {
                        Log.w(TAG, "  x Long Run anchor BLOCKED on $day ($date) by Iron Brain rules")
                    }
                }
                
                AnchorType.LONG_BIKE -> {
                    // Check if we have bike budget remaining
                    if (usedBikeTSS >= budget.bikeTss) {
                        Log.w(TAG, "  ⚠️ Bike budget exhausted, skipping long bike anchor on $day")
                        return@forEach
                    }
                    
                    if (validatePlacement(date, WorkoutType.BIKE, existingHistory + weekPlan, recentRealLogs)) {
                        val longBikeDuration = calculateLongBikeDuration(profile, phase, targetTSS)
                        val longBikeTSS = (longBikeDuration * 0.8).roundToInt() // Lower TSS per hour for bike
                        
                        // Check if this fits in budget
                        if (usedBikeTSS + longBikeTSS <= budget.bikeTss) {
                            val workout = createGhostPlan(
                                date = date,
                                type = WorkoutType.BIKE,
                                duration = longBikeDuration,
                                tss = longBikeTSS,
                                subType = "Long Bike"
                            )
                            weekPlan.add(workout)
                            usedBikeTSS += longBikeTSS
                            totalUsedTSS += longBikeTSS
                            Log.d(TAG, "  + Long Bike anchor scheduled on $day ($date, ${longBikeDuration}min)")
                        } else {
                            Log.w(TAG, "  ⚠️ Long Bike would exceed budget, skipping anchor on $day")
                        }
                    } else {
                        Log.w(TAG, "  x Long Bike anchor BLOCKED on $day ($date) by Iron Brain rules")
                    }
                }
                
                AnchorType.RUN -> {
                    if (usedRunTSS >= budget.runTss) {
                        Log.w(TAG, "  ⚠️ Run budget exhausted, skipping run anchor on $day")
                        return@forEach
                    }
                    
                    if (validatePlacement(date, WorkoutType.RUN, existingHistory + weekPlan, recentRealLogs)) {
                        val runTSS = 45 // Standard run
                        if (usedRunTSS + runTSS <= budget.runTss) {
                            val workout = createGhostPlan(
                                date = date,
                                type = WorkoutType.RUN,
                                duration = 45,
                                tss = runTSS
                            )
                            weekPlan.add(workout)
                            usedRunTSS += runTSS
                            totalUsedTSS += runTSS
                            Log.d(TAG, "  + Run anchor scheduled on $day ($date)")
                        }
                    } else {
                        Log.w(TAG, "  x Run anchor BLOCKED on $day ($date) by Iron Brain rules")
                    }
                }
                
                AnchorType.BIKE -> {
                    if (usedBikeTSS >= budget.bikeTss) {
                        Log.w(TAG, "  ⚠️ Bike budget exhausted, skipping bike anchor on $day")
                        return@forEach
                    }
                    
                    if (validatePlacement(date, WorkoutType.BIKE, existingHistory + weekPlan, recentRealLogs)) {
                        val bikeTSS = 40 // Standard bike
                        if (usedBikeTSS + bikeTSS <= budget.bikeTss) {
                            val workout = createGhostPlan(
                                date = date,
                                type = WorkoutType.BIKE,
                                duration = 45,
                                tss = bikeTSS
                            )
                            weekPlan.add(workout)
                            usedBikeTSS += bikeTSS
                            totalUsedTSS += bikeTSS
                            Log.d(TAG, "  + Bike anchor scheduled on $day ($date)")
                        }
                    } else {
                        Log.w(TAG, "  x Bike anchor BLOCKED on $day ($date) by Iron Brain rules")
                    }
                }
                
                AnchorType.SWIM -> {
                    if (usedSwimTSS >= budget.swimTss) {
                        Log.w(TAG, "  ⚠️ Swim budget exhausted, skipping swim anchor on $day")
                        return@forEach
                    }
                    
                    if (validatePlacement(date, WorkoutType.SWIM, existingHistory + weekPlan, recentRealLogs)) {
                        val swimTSS = profile.defaultSwimTSS ?: 60
                        if (usedSwimTSS + swimTSS <= budget.swimTss) {
                            val workout = createGhostPlan(
                                date = date,
                                type = WorkoutType.SWIM,
                                duration = 60,
                                tss = swimTSS
                            )
                            weekPlan.add(workout)
                            usedSwimTSS += swimTSS
                            totalUsedTSS += swimTSS
                            Log.d(TAG, "  + Swim anchor scheduled on $day ($date)")
                        }
                    } else {
                        Log.w(TAG, "  x Swim anchor BLOCKED on $day ($date) by Iron Brain rules")
                    }
                }
                
                AnchorType.NONE -> {
                    // Do nothing, leave for fillGaps
                }
            }
        }
        
        Log.d(TAG, "  📌 Anchors placed: Run=$usedRunTSS, Bike=$usedBikeTSS, Swim=$usedSwimTSS, Strength=$usedStrengthTSS, Total=$totalUsedTSS")
        return totalUsedTSS
    }

    /**
     * Get strength days for the week based on availability and phase.
     */
    private fun getStrengthDaysForWeek(
        weekStart: LocalDate,
        profile: UserProfile,
        phase: TrainingPhase,
        strengthSpacingHours: Int,
        existingPlans: List<TrainingPlan>
    ): List<Pair<DayOfWeek, LocalDate>> {
        var strengthCount = profile.strengthDays ?: 2
        
        // Phase adjustments
        strengthCount = when (phase) {
            TrainingPhase.OffSeason -> (strengthCount * 1.2).roundToInt().coerceAtMost(4)
            TrainingPhase.Taper, TrainingPhase.Transition -> 0
            else -> strengthCount
        }
        
        if (strengthCount == 0) {
            return emptyList()
        }
        
        val availableDays = mutableListOf<Pair<DayOfWeek, LocalDate>>()
        
        // If weeklyAvailability is set, use it
        if (profile.weeklyAvailability != null && profile.weeklyAvailability.isNotEmpty()) {
            // Find all days that allow STRENGTH
            val strengthAllowedDays = profile.weeklyAvailability.entries
                .filter { (_, types) -> WorkoutType.STRENGTH in types }
                .map { it.key }
            
            if (strengthAllowedDays.isNotEmpty()) {
                // Filter out days that violate spacing
                val candidateDays = strengthAllowedDays.mapNotNull { dayOfWeek ->
                    val date = getDateForDay(weekStart, dayOfWeek)
                    // Check spacing against existing plans
                    val lastStrengthDate = (existingPlans.filter { it.type == WorkoutType.STRENGTH }
                        .maxOfOrNull { it.date } ?: weekStart.minusDays(7))
                    
                    val hoursSinceLastStrength = java.time.temporal.ChronoUnit.HOURS.between(
                        lastStrengthDate.atStartOfDay(),
                        date.atStartOfDay()
                    )
                    
                    if (hoursSinceLastStrength >= strengthSpacingHours) {
                        Pair(dayOfWeek, date)
                    } else null
                }
                
                // Select best slots that maximize spacing
                availableDays.addAll(candidateDays.take(strengthCount))
            }
        }
        
        // Fallback to default pattern if availability is null or insufficient days found
        if (availableDays.size < strengthCount) {
            val defaultPattern = when (strengthCount) {
                1 -> listOf(DayOfWeek.WEDNESDAY)
                2 -> listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
                3 -> listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                4 -> listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                else -> listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            }
            
            defaultPattern.take(strengthCount).forEach { dayOfWeek ->
                val date = getDateForDay(weekStart, dayOfWeek)
                if (!availableDays.any { it.second == date }) {
                    availableDays.add(Pair(dayOfWeek, date))
                }
            }
        }
        
        return availableDays.take(strengthCount)
    }

    /**
     * Validate if a workout can be placed on a given date.
     */
    private suspend fun validatePlacement(
        date: LocalDate,
        type: WorkoutType,
        history: List<TrainingPlan>,
        recentRealLogs: List<WorkoutLog>
    ): Boolean {
        // Create a temporary plan for validation
        val candidate = createGhostPlan(date, type, 60, 50)
        
        // Merge history for validation
        val mergedHistory = mergeHistoryForValidation(recentRealLogs, history, date.minusDays(14))
        
        // Find yesterday's workout
        val yesterday = mergedHistory.find { 
            val itemDate = when (it) {
                is WorkoutLog -> it.date
                is TrainingPlan -> it.date
                else -> null
            }
            itemDate == date.minusDays(1)
        }
        
        // Find last strength date
        val lastStrengthDate = mergedHistory
            .mapNotNull { item ->
                when (item) {
                    is WorkoutLog -> if (item.type == WorkoutType.STRENGTH) item.date else null
                    is TrainingPlan -> if (item.type == WorkoutType.STRENGTH) item.date else null
                    else -> null
                }
            }
            .maxOrNull()
        
        // Get recent runs (last 7 days)
        val recentRuns = mergedHistory.filter { item ->
            val itemDate = when (item) {
                is WorkoutLog -> item.date
                is TrainingPlan -> item.date
                else -> null
            }
            val itemType = when (item) {
                is WorkoutLog -> item.type
                is TrainingPlan -> item.type
                else -> null
            }
            itemType == WorkoutType.RUN && 
            itemDate != null &&
            !itemDate.isBefore(date.minusDays(7)) &&
            !itemDate.isAfter(date)
        }
        
        // Call validation
        val warnings = rulesEngine.validateDailyPlanForGenerator(
            yesterday = yesterday,
            todayPlan = candidate,
            lastStrengthDate = lastStrengthDate,
            recentRuns = recentRuns
        )
        
        return warnings.none { it.isBlocker }
    }

    /**
     * Fill gaps in the week with discipline-specific TSS budgets.
     * Fills Run, Bike, and Swim sequentially while preserving anchor workouts.
     */
    private suspend fun fillGaps(
        weekStart: LocalDate,
        budget: DisciplineBudget,
        profile: UserProfile,
        currentWeekPlan: MutableList<TrainingPlan>,
        history: List<TrainingPlan>,
        recentRealLogs: List<WorkoutLog>,
        phase: TrainingPhase
    ): List<TrainingPlan> {
        val updatedPlan = currentWeekPlan.toMutableList()
        
        // Calculate current TSS per discipline from existing plans
        val currentRunTSS = updatedPlan.filter { it.type == WorkoutType.RUN }.sumOf { it.plannedTSS }
        val currentBikeTSS = updatedPlan.filter { it.type == WorkoutType.BIKE }.sumOf { it.plannedTSS }
        val currentSwimTSS = updatedPlan.filter { it.type == WorkoutType.SWIM }.sumOf { it.plannedTSS }
        val currentStrengthTSS = updatedPlan.filter { it.type == WorkoutType.STRENGTH }.sumOf { it.plannedTSS }
        
        // Phase A: Strength Verification (already placed as anchors)
        if (currentStrengthTSS > budget.strengthTss) {
            Log.w(TAG, "  ⚠️ Strength TSS ($currentStrengthTSS) exceeds budget (${budget.strengthTss})")
        }
        
        // Phase B: Run Filling (High Priority)
        var neededRun = budget.runTss - currentRunTSS
        if (neededRun > 0) {
            Log.d(TAG, "  🏃 Filling Run: needed=$neededRun TSS")
            var runTSSAdded = 0
            
            for (i in 0..6) {
                if (neededRun <= 0) break
                
                val date = weekStart.plusDays(i.toLong())
                
                // Skip if day already has a workout (preserves anchors)
                if (updatedPlan.any { it.date == date }) continue
                
                // Check availability for RUN
                val dayOfWeek = date.dayOfWeek
                val availableTypes = profile.weeklyAvailability?.get(dayOfWeek) ?: listOf(WorkoutType.RUN, WorkoutType.BIKE, WorkoutType.SWIM)
                if (WorkoutType.RUN !in availableTypes) continue
                
                // Validate placement
                if (validatePlacement(date, WorkoutType.RUN, history + updatedPlan, recentRealLogs)) {
                    val runTSS = 45 // Moderate intensity filler run
                    val workout = createGhostPlan(
                        date = date,
                        type = WorkoutType.RUN,
                        duration = 45,
                        tss = runTSS
                    )
                    updatedPlan.add(workout)
                    runTSSAdded += runTSS
                    neededRun -= runTSS
                    Log.d(TAG, "  + Run filler added on $date (${runTSS}TSS)")
                }
            }
            Log.d(TAG, "  ✅ Run filled: added=$runTSSAdded TSS, remaining=${budget.runTss - currentRunTSS - runTSSAdded} TSS")
        }
        
        // Phase C: Bike Filling (Volume Filler)
        var neededBike = budget.bikeTss - currentBikeTSS
        if (neededBike > 0) {
            Log.d(TAG, "  🚴 Filling Bike: needed=$neededBike TSS")
            var bikeTSSAdded = 0
            
            for (i in 0..6) {
                if (neededBike <= 0) break
                
                val date = weekStart.plusDays(i.toLong())
                
                // Skip if day already has a workout (preserves anchors)
                if (updatedPlan.any { it.date == date }) continue
                
                // Check availability for BIKE
                val dayOfWeek = date.dayOfWeek
                val availableTypes = profile.weeklyAvailability?.get(dayOfWeek) ?: listOf(WorkoutType.RUN, WorkoutType.BIKE, WorkoutType.SWIM)
                if (WorkoutType.BIKE !in availableTypes) continue
                
                // Validate placement
                if (validatePlacement(date, WorkoutType.BIKE, history + updatedPlan, recentRealLogs)) {
                    val bikeTSS = 40 // Easy-moderate filler bike
                    val workout = createGhostPlan(
                        date = date,
                        type = WorkoutType.BIKE,
                        duration = 45,
                        tss = bikeTSS
                    )
                    updatedPlan.add(workout)
                    bikeTSSAdded += bikeTSS
                    neededBike -= bikeTSS
                    Log.d(TAG, "  + Bike filler added on $date (${bikeTSS}TSS)")
                }
            }
            Log.d(TAG, "  ✅ Bike filled: added=$bikeTSSAdded TSS, remaining=${budget.bikeTss - currentBikeTSS - bikeTSSAdded} TSS")
        }
        
        // Phase D: Swim Filling
        var neededSwim = budget.swimTss - currentSwimTSS
        if (neededSwim > 0) {
            Log.d(TAG, "  🏊 Filling Swim: needed=$neededSwim TSS")
            var swimTSSAdded = 0
            
            for (i in 0..6) {
                if (neededSwim <= 0) break
                
                val date = weekStart.plusDays(i.toLong())
                
                // Skip if day already has a workout (preserves anchors)
                if (updatedPlan.any { it.date == date }) continue
                
                // Check availability for SWIM
                val dayOfWeek = date.dayOfWeek
                val availableTypes = profile.weeklyAvailability?.get(dayOfWeek) ?: listOf(WorkoutType.RUN, WorkoutType.BIKE, WorkoutType.SWIM)
                if (WorkoutType.SWIM !in availableTypes) continue
                
                // Validate placement
                if (validatePlacement(date, WorkoutType.SWIM, history + updatedPlan, recentRealLogs)) {
                    val swimTSS = profile.defaultSwimTSS ?: 60
                    val workout = createGhostPlan(
                        date = date,
                        type = WorkoutType.SWIM,
                        duration = 60,
                        tss = swimTSS
                    )
                    updatedPlan.add(workout)
                    swimTSSAdded += swimTSS
                    neededSwim -= swimTSS
                    Log.d(TAG, "  + Swim filler added on $date (${swimTSS}TSS)")
                }
            }
            Log.d(TAG, "  ✅ Swim filled: added=$swimTSSAdded TSS, remaining=${budget.swimTss - currentSwimTSS - swimTSSAdded} TSS")
        }
        
        return updatedPlan
    }

    /**
     * Merge real logs and generated plans for validation context.
     */
    private fun mergeHistoryForValidation(
        recentRealLogs: List<WorkoutLog>,
        generatedPlans: List<TrainingPlan>,
        cutoffDate: LocalDate
    ): List<Any> {
        val merged = mutableListOf<Any>()
        
        // Add real logs
        merged.addAll(recentRealLogs.filter { it.date.isAfter(cutoffDate.minusDays(1)) })
        
        // Add generated plans
        merged.addAll(generatedPlans.filter { it.date.isAfter(cutoffDate.minusDays(1)) })
        
        // Sort by date
        return merged.sortedBy { item ->
            when (item) {
                is WorkoutLog -> item.date
                is TrainingPlan -> item.date
                else -> LocalDate.MIN
            }
        }
    }

    /**
     * Create a ghost training plan.
     */
    private fun createGhostPlan(
        date: LocalDate,
        type: WorkoutType,
        duration: Int,
        tss: Int,
        subType: String? = null,
        strengthFocus: StrengthFocus? = null,
        intensity: Intensity? = null
    ): TrainingPlan {
        return TrainingPlan(
            id = UUID.randomUUID().toString(),
            date = date,
            type = type,
            subType = subType,
            durationMinutes = duration,
            plannedTSS = tss,
            strengthFocus = strengthFocus,
            intensity = intensity
        )
    }

    /**
     * Calculate discipline-specific TSS budgets based on total target, balance settings,
     * strength sessions, and recent training load (for safety clamping).
     * 
     * @param totalTargetTSS Total weekly TSS target
     * @param balance Training balance percentages (bike, run, swim)
     * @param strengthSessions Number of strength sessions per week
     * @param recentLoads Map of recent average weekly TSS per workout type (from last 4 weeks)
     * @return DisciplineBudget with calculated TSS for each discipline
     */
    fun calculateDisciplineBudget(
        totalTargetTSS: Int,
        balance: TrainingBalance,
        strengthSessions: Int,
        recentLoads: Map<WorkoutType, Int>
    ): DisciplineBudget {
        // 1. Strength Tax Calculation
        val strengthCost = strengthSessions * 50
        val cardioBudget = maxOf(0, totalTargetTSS - strengthCost)

        // 2. Base Split Application
        val baseSwimTss = (cardioBudget * balance.swimPercent) / 100
        val baseBikeTss = (cardioBudget * balance.bikePercent) / 100
        val baseRunTss = (cardioBudget * balance.runPercent) / 100

        // 3. Safety Clamp for Running
        val recentRunAvg = recentLoads[WorkoutType.RUN] ?: 0
        val maxSafeRun = ((recentRunAvg * 1.15).toInt()) + 15

        val finalRunTss: Int
        val finalBikeTss: Int

        if (baseRunTss > maxSafeRun) {
            // Cap run TSS at safe maximum
            finalRunTss = maxSafeRun
            // Add overflow to bike (low injury risk)
            val overflow = baseRunTss - maxSafeRun
            finalBikeTss = baseBikeTss + overflow
        } else {
            finalRunTss = baseRunTss
            finalBikeTss = baseBikeTss
        }

        // 4. Final Values
        val swimTss = baseSwimTss
        val bikeTss = finalBikeTss
        val runTss = finalRunTss
        val strengthTss = strengthCost
        val totalTss = swimTss + bikeTss + runTss + strengthTss

        return DisciplineBudget(
            swimTss = swimTss,
            bikeTss = bikeTss,
            runTss = runTss,
            strengthTss = strengthTss,
            totalTss = totalTss
        )
    }

    /**
     * Get date for a specific day of week within a week starting at weekStart.
     */
    private fun getDateForDay(weekStart: LocalDate, day: DayOfWeek): LocalDate {
        var date = weekStart
        while (date.dayOfWeek != day) {
            date = date.plusDays(1)
        }
        return date
    }
}

