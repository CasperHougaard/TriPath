package com.tripath.domain

import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Data class for intensity advice result.
 */
data class IntensityAdvice(
    val ifFactor: Double,
    val zoneLabel: String,
    val advice: String,
    val isRealistic: Boolean,
    val warning: String? = null,
    val tagColor: IntensityTagColor
)

enum class IntensityTagColor {
    GREEN, ORANGE, RED
}

/**
 * Singleton object for context-aware intensity factor calculation and coaching advice.
 */
object IntensityCalculator {

    private const val MAX_IF_THRESHOLD = 1.15

    /**
     * Calculate Intensity Factor (IF) and provide descriptive advice.
     */
    fun getAdvice(
        workoutType: WorkoutType,
        tss: Int,
        durationMinutes: Int,
        userProfile: UserProfile?
    ): IntensityAdvice {
        if (durationMinutes <= 0) {
            return IntensityAdvice(0.0, "N/A", "Invalid duration", true, null, IntensityTagColor.GREEN)
        }

        val ifFactor = sqrt((tss * 60.0) / (durationMinutes * 100.0))
        val isRealistic = ifFactor <= MAX_IF_THRESHOLD
        val warning = if (!isRealistic) "Intensity too high for duration" else null

        val (zoneLabel, tagColor) = when {
            ifFactor < 0.75 -> "Steady / Endurance" to IntensityTagColor.GREEN
            ifFactor <= 0.85 -> "Tempo / Sweet Spot" to IntensityTagColor.ORANGE
            else -> "Interval Focus" to IntensityTagColor.RED
        }

        val advice = generateAdvice(workoutType, ifFactor, userProfile)

        return IntensityAdvice(
            ifFactor = ifFactor,
            zoneLabel = zoneLabel,
            advice = advice,
            isRealistic = isRealistic,
            warning = warning,
            tagColor = tagColor
        )
    }

    private fun generateAdvice(
        workoutType: WorkoutType,
        ifFactor: Double,
        userProfile: UserProfile?
    ): String {
        return when (workoutType) {
            WorkoutType.BIKE -> {
                val ftp = userProfile?.ftpBike ?: 250
                val targetWatts = (ftp * ifFactor).roundToNearest5()
                
                when {
                    ifFactor < 0.75 -> "Steady ${targetWatts}W"
                    ifFactor <= 0.85 -> "Tempo Effort at ${targetWatts}W"
                    else -> {
                        val intervalWatts = (targetWatts * 1.1).roundToNearest5()
                        "Interval Power: ~${intervalWatts}W (Avg: ${targetWatts}W)"
                    }
                }
            }
            WorkoutType.RUN -> {
                val thresholdPace = userProfile?.thresholdRunPace ?: 300 // Default 5:00/km
                val avgPaceSeconds = (thresholdPace / ifFactor).toInt()
                
                when {
                    ifFactor < 0.75 -> "Steady ${formatPace(avgPaceSeconds)}/km"
                    ifFactor <= 0.85 -> "Tempo Pace ${formatPace(avgPaceSeconds)}/km"
                    else -> {
                        val intervalPaceSeconds = (avgPaceSeconds * 0.9).toInt()
                        "Interval Pace: ~${formatPace(intervalPaceSeconds)}/km"
                    }
                }
            }
            WorkoutType.SWIM -> {
                val css = userProfile?.cssSecondsper100m ?: 100 // Default 1:40/100m
                val targetSwimPace = (css / ifFactor).toInt()
                
                // For swimming, focus on work pace
                if (ifFactor > 0.85) {
                    "Work Pace: ${formatPace(targetSwimPace)}/100m"
                } else {
                    "Pace: ${formatPace(targetSwimPace)}/100m"
                }
            }
            WorkoutType.STRENGTH -> {
                when {
                    ifFactor < 0.75 -> "Light / Recovery Focus"
                    ifFactor <= 0.85 -> "Moderate / Hypertrophy"
                    else -> "Heavy Strength / Power"
                }
            }
            WorkoutType.OTHER -> "General Activity"
        }
    }

    private fun Int.roundToNearest5(): Int {
        return (this / 5.0).roundToInt() * 5
    }

    private fun Double.roundToNearest5(): Int {
        return (this / 5.0).roundToInt() * 5
    }

    private fun formatPace(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }
}

