package com.tripath.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Singleton object for calculating training phases based on a goal date.
 * Implements the periodization logic for the Coach Engine.
 */
object CoachEngine {

    /**
     * Calculates the current training phase based on the user's goal date.
     * 
     * @param currentDate The current date.
     * @param goalDate The target race date (e.g., Ironman).
     * @return The determined [TrainingPhase].
     */
    fun calculatePhase(currentDate: LocalDate, goalDate: LocalDate?): TrainingPhase {
        if (goalDate == null) {
            // Default to Base if no goal is set, or OffSeason if it's that time of year (e.g., Dec)
            // For simplicity, let's default to Base as a safe "training" mode, 
            // but if we strictly follow "Off-Season if goal > 6 months or December", 
            // a null goal is technically "undefined". Let's assume Base for general fitness.
            // However, request says "Off-Season... when goal > 6 months". 
            // Infinite goal distance -> OffSeason? Or Base?
            // Let's stick to a safe default.
            return TrainingPhase.Base
        }

        if (currentDate.isAfter(goalDate)) {
             // Post-race logic
            val weeksPostRace = ChronoUnit.WEEKS.between(goalDate, currentDate)
            if (weeksPostRace <= 4) {
                return TrainingPhase.Transition
            } else {
                // After transition, if no new goal, what is it?
                // Probably Base or OffSeason depending on time of year.
                // Let's default to Base for now to keep them moving.
                return TrainingPhase.Base
            }
        }

        val weeksUntilGoal = ChronoUnit.WEEKS.between(currentDate, goalDate)
        val monthsUntilGoal = ChronoUnit.MONTHS.between(currentDate, goalDate)

        // "Off-Season/Strength Focus (When goal >6 months out or december if goal is >6 months)"
        // Note: Logic says "or december if goal is >6 months" - this implies "Off-Season if goal > 6 months" covers it,
        // but maybe they mean "Even if goal is < 6 months but it's December?" 
        // The prompt says: "Off-Season/Strength Focus (When goal >6 months out or december if goal is >6 months)"
        // This phrasing is slightly redundant ("When A or (B if A)"). It effectively means "When A".
        // Let's assume the user meant: "Off-Season if goal > 6 months away. ALSO, regardless of goal (maybe?), if it's December?"
        // Re-reading: "When goal >6 months out or december if goal is >6 months" -> exact wording.
        // This implies the condition is strictly: if (goal > 6 months) -> OffSeason.
        // Let's implement that.
        
        if (monthsUntilGoal > 6) {
            return TrainingPhase.OffSeason
        }

        // Phases calculated backwards from goalDate:
        // Transition: Post-race (Handled above)
        // Taper: 2-3 weeks before race (0-3 weeks out)
        // Peak: 4-6 weeks before taper (3-9 weeks out)
        // Build: 8-12 weeks before peak (9-21 weeks out)
        // Base: All time leading up to Build (> 21 weeks out)

        return when {
            weeksUntilGoal <= 3 -> TrainingPhase.Taper
            weeksUntilGoal <= 9 -> TrainingPhase.Peak
            weeksUntilGoal <= 21 -> TrainingPhase.Build
            else -> TrainingPhase.Base
        }
    }
}

