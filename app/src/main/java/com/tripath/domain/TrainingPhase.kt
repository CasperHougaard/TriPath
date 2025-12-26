package com.tripath.domain

/**
 * Represents the different training phases in the periodization cycle.
 * Each phase has specific goals and focus areas.
 */
sealed class TrainingPhase(
    val displayName: String,
    val description: String,
    val focusAreas: List<String>
) {
    object Transition : TrainingPhase(
        displayName = "Transition",
        description = "Recovery and mental break post-race.",
        focusAreas = listOf("Active Recovery", "Mental Reset", "Unstructured Training")
    )

    object Taper : TrainingPhase(
        displayName = "Taper",
        description = "Reducing volume to shed fatigue while maintaining intensity.",
        focusAreas = listOf("Fatigue Management", "Race Pace Sharpening", "Logistics Planning")
    )

    object Peak : TrainingPhase(
        displayName = "Peak",
        description = "Highest specificity training closer to race demands.",
        focusAreas = listOf("Race Specificity", "Threshold Work", "Simulation Days")
    )

    object Build : TrainingPhase(
        displayName = "Build",
        description = "Increasing volume and intensity to build race fitness.",
        focusAreas = listOf("Muscular Endurance", "Tempo Work", "Volume Accumulation")
    )

    object Base : TrainingPhase(
        displayName = "Base",
        description = "Developing aerobic foundation and efficiency.",
        focusAreas = listOf("Aerobic Capacity", "Technique", "Consistency")
    )

    object OffSeason : TrainingPhase(
        displayName = "Off-Season / Strength",
        description = "Focus on structural integrity and building raw strength.",
        focusAreas = listOf("Heavy Strength", "Mobility", "Structural Integrity")
    )
}

