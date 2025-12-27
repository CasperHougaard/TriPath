package com.tripath.data.model

/**
 * Represents the intensity level for workouts.
 */
enum class Intensity {
    LOW,
    MODERATE,
    HIGH,
    LIGHT, // Maps to LOW
    HEAVY  // Maps to HIGH
}

