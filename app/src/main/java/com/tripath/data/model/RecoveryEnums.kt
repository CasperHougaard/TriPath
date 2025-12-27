package com.tripath.data.model

/**
 * Enum representing the severity level of allergies.
 */
enum class AllergySeverity {
    NONE,
    MILD,
    MODERATE,
    SEVERE
}

/**
 * Enum representing the type of trigger for wellness tasks.
 */
enum class TaskTriggerType {
    DAILY,
    TRIGGER_STRENGTH,
    TRIGGER_LONG_DURATION,
    TRIGGER_HIGH_TSS
}
