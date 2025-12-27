package com.tripath.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant

/**
 * Custom serializer for java.time.Instant using epoch milliseconds.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)
    
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }
    
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.ofEpochMilli(decoder.decodeLong())
    }
}

/**
 * Data class for heart rate samples from Health Connect.
 */
@Serializable
data class HeartRateSample(
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val bpm: Int
)

/**
 * Data class for power samples from Health Connect.
 */
@Serializable
data class PowerSample(
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val watts: Int
)

/**
 * Engine for calculating training zone distribution based on raw samples.
 * Implements the "Sample Hold" rule and handles sensor dropouts/gaps.
 */
object ZoneCalculator {

    private const val GAP_THRESHOLD_SECONDS = 30L

    /**
     * Calculate heart rate zone distribution using the Banister/Garmin 5-zone model.
     * Uses the "Sample Hold" rule: Intensity is assumed constant from a sample until the next one.
     */
    suspend fun calculateHrZoneDistribution(
        samples: List<HeartRateSample>,
        maxHr: Int
    ): Map<String, Int> = withContext(Dispatchers.Default) {
        if (samples.size < 2 || maxHr <= 0) return@withContext emptyMap()

        val distribution = mutableMapOf(
            "Z1" to 0,
            "Z2" to 0,
            "Z3" to 0,
            "Z4" to 0,
            "Z5" to 0
        )

        // Thresholds based on plan: [50%, 60%), [60%, 70%), [70%, 80%), [80%, 90%), [90%, 100%]
        val z1Limit = (maxHr * 0.50).toInt()
        val z2Limit = (maxHr * 0.60).toInt()
        val z3Limit = (maxHr * 0.70).toInt()
        val z4Limit = (maxHr * 0.80).toInt()
        val z5Limit = (maxHr * 0.90).toInt()

        for (i in 0 until samples.size - 1) {
            val current = samples[i]
            val next = samples[i + 1]
            val durationSeconds = Duration.between(current.timestamp, next.timestamp).seconds

            // Gap Handling: Ignore segments where the sensor likely dropped out
            if (durationSeconds <= GAP_THRESHOLD_SECONDS && durationSeconds > 0) {
                val zone = when {
                    current.bpm >= z5Limit -> "Z5"
                    current.bpm >= z4Limit -> "Z4"
                    current.bpm >= z3Limit -> "Z3"
                    current.bpm >= z2Limit -> "Z2"
                    current.bpm >= z1Limit -> "Z1"
                    else -> null // Below Z1
                }
                
                zone?.let {
                    distribution[it] = distribution.getOrDefault(it, 0) + durationSeconds.toInt()
                }
            }
        }

        distribution.filterValues { it > 0 }
    }

    /**
     * Calculate power zone distribution based on cycling FTP.
     * Uses the Coggan 5-zone power model (simplified for triathlon focus).
     */
    suspend fun calculatePowerZoneDistribution(
        samples: List<PowerSample>,
        ftp: Int
    ): Map<String, Int> = withContext(Dispatchers.Default) {
        if (samples.size < 2 || ftp <= 0) return@withContext emptyMap()

        val distribution = mutableMapOf(
            "Z1" to 0,
            "Z2" to 0,
            "Z3" to 0,
            "Z4" to 0,
            "Z5" to 0
        )

        // Thresholds based on plan: Z1: 0-55%, Z2: 55-75%, Z3: 75-90%, Z4: 90-105%, Z5: 105%+
        val z2Limit = (ftp * 0.55).toInt()
        val z3Limit = (ftp * 0.75).toInt()
        val z4Limit = (ftp * 0.90).toInt()
        val z5Limit = (ftp * 1.05).toInt()

        for (i in 0 until samples.size - 1) {
            val current = samples[i]
            val next = samples[i + 1]
            val durationSeconds = Duration.between(current.timestamp, next.timestamp).seconds

            if (durationSeconds <= GAP_THRESHOLD_SECONDS && durationSeconds > 0) {
                val zone = when {
                    current.watts >= z5Limit -> "Z5"
                    current.watts >= z4Limit -> "Z4"
                    current.watts >= z3Limit -> "Z3"
                    current.watts >= z2Limit -> "Z2"
                    else -> "Z1" // 0-55% is active recovery Z1
                }
                
                distribution[zone] = distribution.getOrDefault(zone, 0) + durationSeconds.toInt()
            }
        }

        distribution.filterValues { it > 0 }
    }
}

