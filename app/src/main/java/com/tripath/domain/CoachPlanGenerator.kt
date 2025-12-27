package com.tripath.domain

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.repository.TrainingRepository
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.TrainingBalance
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.flow.first

class CoachPlanGenerator @Inject constructor(
    private val repository: TrainingRepository
) {

    /**
     * Generates a 4-week training block based on current fitness, phase, and user constraints.
     */
    suspend fun generateBlock(
        startDate: LocalDate, 
        currentCtl: Double, 
        ignoreExisting: Boolean = false,
        allowMultipleActivitiesPerDay: Boolean = false
    ): List<TrainingPlan> {
        val userProfile = repository.getUserProfileOnce() ?: return emptyList()
        val generatedPlans = mutableListOf<TrainingPlan>()

        // 1. Calculate TSS Budget for 4 weeks
        val effectiveCtl = if (currentCtl < 20) 20.0 else currentCtl
        val week1Target = (effectiveCtl * 7 * 1.1).roundToInt()
        val week2Target = (week1Target * 1.05).roundToInt()
        val week3Target = (week2Target * 1.05).roundToInt()
        val week4Target = (week1Target * 0.65).roundToInt() // Recovery

        val weeklyTargets = listOf(week1Target, week2Target, week3Target, week4Target)

        // 2. Generate plans for each week
        for (i in 0 until 4) {
            val weekStartDate = startDate.plusWeeks(i.toLong())
            val weekTargetTss = weeklyTargets[i]
            
            // Determine phase for this week
            val phase = CoachEngine.calculatePhase(weekStartDate, userProfile.goalDate)
            
            val weekPlans = generateSmartWeek(
                weekStartDate = weekStartDate,
                targetTss = weekTargetTss,
                userProfile = userProfile,
                phase = phase,
                isRecoveryWeek = (i == 3),
                ignoreExisting = ignoreExisting,
                allowMultipleActivitiesPerDay = allowMultipleActivitiesPerDay
            )
            generatedPlans.addAll(weekPlans)
        }

        return generatedPlans
    }

    private suspend fun generateSmartWeek(
        weekStartDate: LocalDate,
        targetTss: Int,
        userProfile: UserProfile,
        phase: TrainingPhase,
        isRecoveryWeek: Boolean,
        ignoreExisting: Boolean,
        allowMultipleActivitiesPerDay: Boolean
    ): List<TrainingPlan> {
        val newPlans = mutableListOf<TrainingPlan>()
        val weekEndDate = weekStartDate.plusDays(6)
        
        // Fetch existing plans if not ignoring them
        val existingPlans = if (ignoreExisting) emptyList() 
            else repository.getTrainingPlansByDateRange(weekStartDate, weekEndDate).first()
        
        val dailyPlans = existingPlans.groupBy { it.date }
        var currentTss = existingPlans.sumOf { it.plannedTSS }
        
        val availability = userProfile.weeklyAvailability ?: createDefaultAvailability()
        val longDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY
        val balance = userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
        
        // --- 1. MINIMUM 1 OF EACH RULE ---
        // Pre-allocate one small session for each discipline to ensure variety
        val disciplines = listOf(WorkoutType.SWIM, WorkoutType.BIKE, WorkoutType.RUN)
        for (type in disciplines) {
            if (existingPlans.none { it.type == type }) {
                val minTss = if (isRecoveryWeek) 20 else 35
                val added = scheduleSpecificSession(
                    type, minTss, "Maintenance", weekStartDate, availability, dailyPlans, newPlans, phase, Intensity.LOW, allowMultipleActivitiesPerDay
                )
                currentTss += added
            }
        }

        // --- 2. STRENGTH (Priority 1) ---
        val strengthCount = if (isRecoveryWeek) 1 else (userProfile.strengthDays ?: 2)
        currentTss += scheduleStrength(weekStartDate, strengthCount, availability, dailyPlans, newPlans, userProfile, allowMultipleActivitiesPerDay)

        // --- 3. LONG RIDE (Priority 2) ---
        if (currentTss < targetTss) {
            currentTss += scheduleLongRide(weekStartDate, longDay, availability, dailyPlans, newPlans, targetTss, isRecoveryWeek, phase, allowMultipleActivitiesPerDay)
        }

        // --- 4. BALANCE-BASED FILLERS (Priority 3) ---
        val remainingTss = (targetTss - currentTss).coerceAtLeast(0)
        if (remainingTss > 30) {
            scheduleSmartFillers(weekStartDate, remainingTss, availability, dailyPlans, newPlans, userProfile, phase, balance, allowMultipleActivitiesPerDay)
        }

        return newPlans
    }

    private fun scheduleSpecificSession(
        type: WorkoutType,
        tss: Int,
        label: String,
        weekStartDate: LocalDate,
        availability: Map<DayOfWeek, List<WorkoutType>>,
        existing: Map<LocalDate, List<TrainingPlan>>,
        newPlans: MutableList<TrainingPlan>,
        phase: TrainingPhase,
        intensity: Intensity,
        allowMultipleActivitiesPerDay: Boolean
    ): Int {
        val slot = (0..6).map { weekStartDate.plusDays(it.toLong()) }
            .firstOrNull { date ->
                val allowed = availability[date.dayOfWeek]?.contains(type) == true
                val free = if (allowMultipleActivitiesPerDay) {
                    // If multiple activities allowed, just check if the type is available on that day
                    true
                } else {
                    // Otherwise, check if the day is completely free
                    existing[date].isNullOrEmpty() && newPlans.none { it.date == date }
                }
                allowed && free
            } ?: return 0

        val tssPerHour = when(type) {
            WorkoutType.SWIM -> 60
            WorkoutType.RUN -> 55
            else -> 50
        }
        val duration = ((tss.toDouble() / tssPerHour) * 60).toInt().coerceAtLeast(20)

        newPlans.add(TrainingPlan(
            date = slot,
            type = type,
            subType = "$label (${phase.displayName})",
            durationMinutes = duration,
            plannedTSS = tss,
            intensity = intensity
        ))
        return tss
    }

    private fun scheduleStrength(
        weekStartDate: LocalDate,
        count: Int,
        availability: Map<DayOfWeek, List<WorkoutType>>,
        existing: Map<LocalDate, List<TrainingPlan>>,
        newPlans: MutableList<TrainingPlan>,
        userProfile: UserProfile,
        allowMultipleActivitiesPerDay: Boolean
    ): Int {
        var addedTss = 0
        var scheduledCount = 0
        val chosenDays = mutableListOf<LocalDate>()
        
        val suitableDays = (0..6).map { weekStartDate.plusDays(it.toLong()) }
            .filter { date ->
                val allowed = availability[date.dayOfWeek]?.contains(WorkoutType.STRENGTH) == true
                val free = if (allowMultipleActivitiesPerDay) {
                    true // Type is available, day is fine
                } else {
                    existing[date].isNullOrEmpty() && newPlans.none { it.date == date }
                }
                allowed && free
            }

        for (date in suitableDays) {
            if (scheduledCount >= count) break
            val gapOk = chosenDays.all { abs(java.time.temporal.ChronoUnit.DAYS.between(it, date)) >= 2 }
            
            if (gapOk) {
                val focus = if (scheduledCount == 0) StrengthFocus.HEAVY else StrengthFocus.STABILITY
                val tss = if (focus == StrengthFocus.HEAVY) 
                    userProfile.defaultStrengthHeavyTSS ?: 60 else userProfile.defaultStrengthLightTSS ?: 30

                newPlans.add(TrainingPlan(
                    date = date,
                    type = WorkoutType.STRENGTH,
                    subType = "Strength: ${focus.name.lowercase().capitalize()}",
                    durationMinutes = 60,
                    plannedTSS = tss,
                    strengthFocus = focus,
                    intensity = if (focus == StrengthFocus.HEAVY) Intensity.HIGH else Intensity.MODERATE
                ))
                chosenDays.add(date)
                addedTss += tss
                scheduledCount++
            }
        }
        return addedTss
    }

    private fun scheduleLongRide(
        weekStartDate: LocalDate,
        longDay: DayOfWeek,
        availability: Map<DayOfWeek, List<WorkoutType>>,
        existing: Map<LocalDate, List<TrainingPlan>>,
        newPlans: MutableList<TrainingPlan>,
        weeklyTargetTss: Int,
        isRecoveryWeek: Boolean,
        phase: TrainingPhase,
        allowMultipleActivitiesPerDay: Boolean
    ): Int {
        val date = (0..6).map { weekStartDate.plusDays(it.toLong()) }.find { it.dayOfWeek == longDay } ?: return 0
        if (availability[longDay]?.contains(WorkoutType.BIKE) != true) return 0
        if (!allowMultipleActivitiesPerDay && (existing[date]?.isNotEmpty() == true || newPlans.any { it.date == date })) return 0

        val ratio = when(phase) {
            TrainingPhase.Base -> 0.35
            TrainingPhase.Build -> 0.30
            TrainingPhase.Peak -> 0.25
            else -> 0.20
        }
        val targetLongTss = (weeklyTargetTss * (if (isRecoveryWeek) ratio * 0.6 else ratio)).toInt().coerceIn(40, 250)
        val tssPerHour = 45
        val durationMinutes = ((targetLongTss.toDouble() / tssPerHour) * 60).toInt()

        newPlans.add(TrainingPlan(
            date = date,
            type = WorkoutType.BIKE,
            subType = "Long ${if (phase == TrainingPhase.Base) "Zone 2" else "Endurance"} Ride",
            durationMinutes = durationMinutes,
            plannedTSS = targetLongTss,
            intensity = if (phase == TrainingPhase.Build || phase == TrainingPhase.Peak) Intensity.MODERATE else Intensity.LOW
        ))
        return targetLongTss
    }

    private fun scheduleSmartFillers(
        weekStartDate: LocalDate,
        budget: Int,
        availability: Map<DayOfWeek, List<WorkoutType>>,
        existing: Map<LocalDate, List<TrainingPlan>>,
        newPlans: MutableList<TrainingPlan>,
        userProfile: UserProfile,
        phase: TrainingPhase,
        balance: TrainingBalance,
        allowMultipleActivitiesPerDay: Boolean
    ) {
        val bikeBudget = (budget * (balance.bikePercent / 100.0)).toInt()
        val runBudget = (budget * (balance.runPercent / 100.0)).toInt()
        val swimBudget = (budget * (balance.swimPercent / 100.0)).toInt()

        val allDays = (0..6).map { weekStartDate.plusDays(it.toLong()) }

        // Helper to get variety based on phase
        fun getWorkoutVariety(type: WorkoutType): Pair<String, Intensity> {
            return when (type) {
                WorkoutType.RUN -> when (phase) {
                    TrainingPhase.Base -> "Easy Aerobic" to Intensity.LOW
                    TrainingPhase.Build -> "Tempo Run" to Intensity.MODERATE
                    TrainingPhase.Peak -> "Intervals / VO2 Max" to Intensity.HIGH
                    else -> "Recovery Run" to Intensity.LOW
                }
                WorkoutType.BIKE -> when (phase) {
                    TrainingPhase.Base -> "Aerobic Base" to Intensity.LOW
                    TrainingPhase.Build -> "Sweet Spot" to Intensity.MODERATE
                    TrainingPhase.Peak -> "Threshold Power" to Intensity.HIGH
                    else -> "Recovery Spin" to Intensity.LOW
                }
                WorkoutType.SWIM -> when (phase) {
                    TrainingPhase.Base -> "Technical / Drills" to Intensity.LOW
                    TrainingPhase.Build -> "CSS Intervals" to Intensity.MODERATE
                    TrainingPhase.Peak -> "Main Set: Speed" to Intensity.HIGH
                    else -> "Easy Recovery Swim" to Intensity.LOW
                }
                else -> "General" to Intensity.MODERATE
            }
        }

        fun addSession(type: WorkoutType, tss: Int) {
            if (tss < 20) return
            
            val (subType, intensity) = getWorkoutVariety(type)
            
            // Get available slots dynamically - check if day is free based on current state
            val availableSlots = allDays.filter { date ->
                val isAllowed = availability[date.dayOfWeek]?.contains(type) == true
                val isFree = if (allowMultipleActivitiesPerDay) {
                    true // Multiple activities allowed, day is always available
                } else {
                    // Check if day is completely free
                    existing[date].isNullOrEmpty() && newPlans.none { it.date == date }
                }
                isAllowed && isFree
            }
            
            if (availableSlots.isEmpty()) return
            
            // Fatigue rule: No back-to-back high intensity
            val slot = availableSlots.firstOrNull { date ->
                val yesterday = date.minusDays(1)
                val tomorrow = date.plusDays(1)
                val surroundingHigh = (newPlans.find { it.date == yesterday || it.date == tomorrow }?.intensity == Intensity.HIGH)
                !(intensity == Intensity.HIGH && surroundingHigh)
            } ?: availableSlots.firstOrNull()

            if (slot != null) {
                val tssPerHour = when(type) {
                    WorkoutType.SWIM -> userProfile.defaultSwimTSS ?: 60
                    WorkoutType.RUN -> 55
                    else -> 50
                }
                newPlans.add(TrainingPlan(
                    date = slot,
                    type = type,
                    subType = subType,
                    durationMinutes = ((tss.toDouble() / tssPerHour) * 60).toInt().coerceAtLeast(20),
                    plannedTSS = tss,
                    intensity = intensity
                ))
            }
        }

        // Add sessions (Cap each session TSS to keep variety)
        listOf(
            WorkoutType.SWIM to swimBudget,
            WorkoutType.RUN to runBudget,
            WorkoutType.BIKE to bikeBudget
        ).forEach { (type, totalTss) ->
            var remaining = totalTss
            var attempts = 0
            val maxAttempts = 20 // Prevent infinite loop
            while (remaining >= 25 && attempts < maxAttempts) {
                val previousSize = newPlans.size
                val sessionTss = remaining.coerceAtMost(if (type == WorkoutType.BIKE) 80 else 60)
                addSession(type, sessionTss)
                // If no session was added, break to avoid infinite loop
                if (newPlans.size == previousSize) break
                remaining -= sessionTss
                attempts++
            }
        }
    }

    private fun createDefaultAvailability(): Map<DayOfWeek, List<WorkoutType>> {
        return DayOfWeek.values().associateWith { day ->
            when (day) {
                DayOfWeek.MONDAY -> listOf(WorkoutType.SWIM, WorkoutType.STRENGTH)
                DayOfWeek.TUESDAY -> listOf(WorkoutType.BIKE, WorkoutType.RUN)
                DayOfWeek.WEDNESDAY -> listOf(WorkoutType.RUN, WorkoutType.STRENGTH)
                DayOfWeek.THURSDAY -> listOf(WorkoutType.BIKE, WorkoutType.SWIM)
                DayOfWeek.FRIDAY -> listOf(WorkoutType.SWIM, WorkoutType.RUN)
                DayOfWeek.SATURDAY -> listOf(WorkoutType.BIKE, WorkoutType.RUN)
                DayOfWeek.SUNDAY -> listOf(WorkoutType.BIKE, WorkoutType.RUN)
            }
        }
    }
    
    private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
