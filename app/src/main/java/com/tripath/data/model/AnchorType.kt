package com.tripath.data.model

/**
 * Represents the type of anchor workout scheduled for a specific day of the week.
 * Anchors are "must have" workouts that define the weekly training structure.
 */
enum class AnchorType {
    /** Flexible/Rest day - no anchor workout (filled by fillGaps) */
    NONE,
    
    /** Standard run workout */
    RUN,
    
    /** Standard bike workout */
    BIKE,
    
    /** Standard swim workout */
    SWIM,
    
    /** Strength training session */
    STRENGTH,
    
    /** Long run workout (typically the longest run of the week) */
    LONG_RUN,
    
    /** Long bike workout (typically the longest bike of the week) */
    LONG_BIKE
}

