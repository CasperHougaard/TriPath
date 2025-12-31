package com.tripath.domain

import com.tripath.domain.coach.TrainingPhase as CoachTrainingPhase

/**
 * Extension function to convert the sealed class TrainingPhase to the enum TrainingPhase used by the Iron Brain.
 */
fun TrainingPhase.toCoachPhase(): CoachTrainingPhase {
    return when (this) {
        is TrainingPhase.OffSeason -> CoachTrainingPhase.OFF_SEASON
        is TrainingPhase.Base -> CoachTrainingPhase.BASE
        is TrainingPhase.Build -> CoachTrainingPhase.BUILD
        is TrainingPhase.Peak -> CoachTrainingPhase.PEAK
        is TrainingPhase.Taper -> CoachTrainingPhase.TAPER
        is TrainingPhase.Transition -> CoachTrainingPhase.OFF_SEASON // Default to OFF_SEASON for transition
    }
}


