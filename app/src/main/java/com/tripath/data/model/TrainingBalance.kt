package com.tripath.data.model

import kotlinx.serialization.Serializable

/**
 * Represents the desired distribution of training stress (TSS) across disciplines.
 */
@Serializable
data class TrainingBalance(
    val bikePercent: Int = 50,
    val runPercent: Int = 30,
    val swimPercent: Int = 20
) {
    companion object {
        val IRONMAN_BASE = TrainingBalance(50, 30, 20)
        val BALANCED = TrainingBalance(34, 33, 33)
        val RUN_FOCUS = TrainingBalance(30, 50, 20)
        val BIKE_FOCUS = TrainingBalance(60, 25, 15)
    }

    /**
     * Ensures percentages sum to 100.
     */
    fun isValid(): Boolean = (bikePercent + runPercent + swimPercent) == 100
}

