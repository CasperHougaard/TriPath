package com.tripath.domain.running

import kotlin.math.roundToInt

object RunningPaceProgressionRules {
    private const val MIN_PACE_SEC_PER_KM = 180

    data class PaceRange(
        val low: Int,
        val high: Int
    )

    fun adjustRange(
        baselinePaceSecPerKm: Int?,
        sessionType: RunningSessionType,
        weekIndex: Int,
        totalWeeks: Int,
        baseLow: Int,
        baseHigh: Int
    ): PaceRange? {
        val baseline = baselinePaceSecPerKm ?: return null
        val improvementSeconds = (baseline * improvementFraction(sessionType, weekIndex, totalWeeks)).roundToInt()
        val adjustedLow = (baseLow - improvementSeconds).coerceAtLeast(MIN_PACE_SEC_PER_KM)
        val adjustedHigh = (baseHigh - improvementSeconds).coerceAtLeast(adjustedLow)
        return PaceRange(low = adjustedLow, high = adjustedHigh)
    }

    fun improvementFraction(
        sessionType: RunningSessionType,
        weekIndex: Int,
        totalWeeks: Int
    ): Double {
        val normalizedProgress = when {
            totalWeeks <= 1 -> 0.0
            else -> ((weekIndex - 1).coerceIn(0, totalWeeks - 1)).toDouble() / (totalWeeks - 1).toDouble()
        }
        return horizonCap(totalWeeks).coerceAtMost(sessionCap(sessionType)) * normalizedProgress
    }

    private fun horizonCap(totalWeeks: Int): Double {
        return when {
            totalWeeks <= 8 -> 0.02
            totalWeeks <= 16 -> 0.04
            totalWeeks <= 24 -> 0.05
            totalWeeks <= 52 -> 0.06
            else -> 0.06
        }
    }

    private fun sessionCap(sessionType: RunningSessionType): Double {
        return when (sessionType) {
            RunningSessionType.RECOVERY -> 0.01
            RunningSessionType.EASY -> 0.02
            RunningSessionType.LONG_RUN -> 0.02
            RunningSessionType.TEMPO -> 0.04
            RunningSessionType.THRESHOLD -> 0.05
            RunningSessionType.RACE_PACE -> 0.05
            RunningSessionType.INTERVALS -> 0.06
            RunningSessionType.PROGRESSION -> 0.04
        }
    }
}