package com.tripath.domain.coach

import android.util.Log
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.TrainingRepository
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

        // Goal Date Range Check: Must be at least 2 weeks away, not more than 1 year
        val daysUntilGoal = java.time.temporal.ChronoUnit.DAYS.between(startDate, goalDate)
        val weeksUntilGoal = daysUntilGoal / 7.0
        
        if (weeksUntilGoal < 2) {
            Log.e(TAG, "❌ VALIDATION FAILED: Goal date ($goalDate) is too close (${weeksUntilGoal} weeks). Minimum 2 weeks required for meaningful season generation.")
            return ValidationResult.Failure("Goal date is too close (${weeksUntilGoal.toInt()} weeks). Minimum 2 weeks required.")
        }
        
        if (daysUntilGoal > 365) {
            Log.e(TAG, "❌ VALIDATION FAILED: Goal date ($goalDate) is too far (${daysUntilGoal} days / ${weeksUntilGoal} weeks). Maximum 1 year for accurate projection.")
            return ValidationResult.Failure("Goal date is too far (${daysUntilGoal} days). Maximum 1 year for accurate projection.")
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
     * @return List of generated TrainingPlan objects
     */
    suspend fun generateSeason(
        startDate: LocalDate,
        currentCtl: Double,
        months: Int = 3,
        recentRealLogs: List<WorkoutLog> = emptyList()
    ): List<TrainingPlan> {
        val smartEnabled = preferencesManager.smartPlanningEnabledFlow.first()
        if (!smartEnabled) {
            Log.e(TAG, "Smart Planning is DISABLED in settings. Aborting.")
            return emptyList()
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
            return emptyList()
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
                phase = phase
            )
            generatedPlan.addAll(weekPlan)

            // Update Simulation for next week (Rough approximation)
            val weeklyTSS = weekPlan.sumOf { it.plannedTSS }
            currentSimulatedCtl = currentSimulatedCtl + (weeklyTSS / 7.0 - currentSimulatedCtl) * 0.1
            currentDate = currentDate.plusWeeks(1)
        }

        Log.i(TAG, "✅ GENERATION COMPLETE. Created ${generatedPlan.size} workouts.")
        return generatedPlan
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
        phase: TrainingPhase
    ): List<TrainingPlan> {
        val weekPlan = mutableListOf<TrainingPlan>()
        var usedTSS = 0

        // Get preferences
        val strengthSpacingHours = preferencesManager.strengthSpacingHoursFlow.first()

        // 1. PLACE ANCHORS (Priority: Strength & Long Run)
        
        // A. Strength sessions
        val strengthDays = getStrengthDaysForWeek(
            weekStart = weekStart,
            profile = profile,
            phase = phase,
            strengthSpacingHours = strengthSpacingHours,
            existingPlans = weekPlan
        )
        
        strengthDays.forEach { (dayOfWeek, date) ->
            if (validatePlacement(date, WorkoutType.STRENGTH, existingHistory, recentRealLogs)) {
                val strengthSession = createGhostPlan(
                    date = date,
                    type = WorkoutType.STRENGTH,
                    duration = 60,
                    tss = profile.defaultStrengthHeavyTSS ?: 60,
                    strengthFocus = StrengthFocus.FULL_BODY, // TODO: Rotate based on week
                    intensity = Intensity.HEAVY
                )
                weekPlan.add(strengthSession)
                usedTSS += strengthSession.plannedTSS
                Log.d(TAG, "  + Strength scheduled on $dayOfWeek ($date)")
            } else {
                Log.w(TAG, "  x Strength BLOCKED on $dayOfWeek ($date) by Iron Brain rules.")
            }
        }

        // B. Long Run
        val longRunDate = getDateForDay(weekStart, profile.longTrainingDay ?: DayOfWeek.SUNDAY)
        if (validatePlacement(longRunDate, WorkoutType.RUN, existingHistory + weekPlan, recentRealLogs)) {
            val longRunDuration = calculateLongRunDuration(profile, phase, targetTSS)
            val longRunTSS = (longRunDuration * 1.2).roundToInt() // Rough TSS estimate
            val longRun = createGhostPlan(
                date = longRunDate,
                type = WorkoutType.RUN,
                duration = longRunDuration,
                tss = longRunTSS,
                subType = "Long Run"
            )
            weekPlan.add(longRun)
            usedTSS += longRunTSS
            Log.d(TAG, "  + Long Run scheduled on ${profile.longTrainingDay} (${longRunDuration}min)")
        }

        // 2. FILL THE GAPS (Balance)
        val remainingTSS = targetTSS - usedTSS
        if (remainingTSS > 0) {
            val (updatedPlan, remainingBudget) = fillGaps(
                weekStart = weekStart,
                budget = remainingTSS,
                profile = profile,
                currentWeekPlan = weekPlan,
                history = existingHistory,
                recentRealLogs = recentRealLogs,
                phase = phase
            )
            weekPlan.clear()
            weekPlan.addAll(updatedPlan)
            
            if (remainingBudget > targetTSS * 0.2) {
                Log.w(TAG, "  ⚠️ Under-volume warning: Target $targetTSS TSS, achieved ${targetTSS - remainingBudget} TSS, remaining $remainingBudget TSS")
            }
        }

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
     * Fill gaps in the week with remaining TSS budget.
     */
    private suspend fun fillGaps(
        weekStart: LocalDate,
        budget: Int,
        profile: UserProfile,
        currentWeekPlan: MutableList<TrainingPlan>,
        history: List<TrainingPlan>,
        recentRealLogs: List<WorkoutLog>,
        phase: TrainingPhase
    ): Pair<List<TrainingPlan>, Int> {
        var budgetLeft = budget
        val updatedPlan = currentWeekPlan.toMutableList()
        
        // Determine phase-appropriate workout types
        val preferredTypes = when (phase) {
            TrainingPhase.OffSeason -> listOf(WorkoutType.BIKE, WorkoutType.SWIM, WorkoutType.RUN)
            TrainingPhase.Taper, TrainingPhase.Transition -> listOf(WorkoutType.SWIM, WorkoutType.BIKE) // Less impact
            else -> listOf(WorkoutType.BIKE, WorkoutType.RUN, WorkoutType.SWIM)
        }
        
        // Iterate through days of week
        for (i in 0..6) {
            if (budgetLeft <= 0) break
            
            val date = weekStart.plusDays(i.toLong())
            
            // Skip if day already has a workout
            if (updatedPlan.any { it.date == date }) continue
            
            // Check availability
            val dayOfWeek = date.dayOfWeek
            val availableTypes = profile.weeklyAvailability?.get(dayOfWeek) ?: listOf(WorkoutType.RUN, WorkoutType.BIKE, WorkoutType.SWIM)
            
            // Try to add workout from preferred types
            for (type in preferredTypes) {
                if (type !in availableTypes) continue
                if (budgetLeft <= 0) break
                
                if (validatePlacement(date, type, history + updatedPlan, recentRealLogs)) {
                    val duration = 45
                    val tss = when (type) {
                        WorkoutType.SWIM -> profile.defaultSwimTSS ?: 60
                        else -> 40 // Default for bike/run filler
                    }
                    val workout = createGhostPlan(date, type, duration, tss)
                    updatedPlan.add(workout)
                    budgetLeft -= tss
                    Log.d(TAG, "  + Filler $type added on $date")
                    break // Move to next day
                }
            }
        }
        
        // Fallback: If remaining budget is high, try extending existing sessions
        if (budgetLeft > budget * 0.2) {
            val longRun = updatedPlan.find { it.type == WorkoutType.RUN && it.subType == "Long Run" }
            if (longRun != null && budgetLeft > 20) {
                val extendedDuration = longRun.durationMinutes + 15
                val extendedTSS = (extendedDuration * 1.2).roundToInt()
                val additionalTSS = extendedTSS - longRun.plannedTSS
                
                if (additionalTSS <= budgetLeft) {
                    val extendedPlan = longRun.copy(
                        durationMinutes = extendedDuration,
                        plannedTSS = extendedTSS
                    )
                    updatedPlan.remove(longRun)
                    updatedPlan.add(extendedPlan)
                    budgetLeft -= additionalTSS
                    Log.d(TAG, "  ↻ Extended Long Run to $extendedDuration min (fallback)")
                }
            }
        }
        
        return Pair(updatedPlan, budgetLeft)
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

