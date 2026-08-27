package com.tripath.domain.strain

/**
 * The four tissues-and-systems a session can tax, each with its own recovery clock.
 *
 * A single global fatigue number cannot answer the question that actually matters on a given
 * morning — "my legs feel hammered, can I still swim?" — because it has no way to tell *what* is
 * hammered. Splitting load into channels does, and the split that matters most is
 * [LOWER_IMPACT] against [LOWER_MUSCULAR]: quads emptied by a long ride and shins beaten up by a
 * long run both read as "tired legs" and recover on completely different timescales.
 *
 * ## The time constants
 * [tauHours] is the exponential decay constant — the time to fall to about 37% of a session's
 * initial cost. They are ordered by how quickly each system clears:
 *
 * - Connective tissue and bone remodel over days to weeks, so mechanical loading lingers longest.
 * - Muscular soreness from a hard session broadly resolves inside two to three days.
 * - Metabolic and central fatigue clears fastest of the four.
 *
 * These are informed starting points, not measured constants. They live here as named values so
 * they can be retuned in one place, and because strain is derived at read time (never persisted),
 * retuning them never requires a re-sync.
 */
enum class StrainChannel(
    val label: String,
    val tauHours: Double,
    val description: String
) {
    /** Bone, tendon and joint loading: running, hiking, plyometrics, heavy axial/eccentric lifting. */
    LOWER_IMPACT(
        label = "Legs — impact",
        tauHours = 96.0,
        description = "Bone, tendon and joint loading from running and heavy lower-body lifting"
    ),

    /** Contractile cost in the legs: cycling, running, squat/hinge/lunge patterns. */
    LOWER_MUSCULAR(
        label = "Legs — muscular",
        tauHours = 48.0,
        description = "Muscular cost in the legs from cycling, running and lower-body lifting"
    ),

    /** Contractile cost above the waist: swimming, pressing, pulling, carrying. */
    UPPER_MUSCULAR(
        label = "Upper body",
        tauHours = 48.0,
        description = "Muscular cost in the upper body from swimming and upper-body lifting"
    ),

    /** Whole-body metabolic and central cost, driven by total load and heavy lifting volume. */
    SYSTEMIC(
        label = "Systemic",
        tauHours = 36.0,
        description = "Whole-body metabolic and central nervous cost"
    );

    companion object {
        /** Only the regional channels — the three that answer "which body part?". */
        val regional: List<StrainChannel>
            get() = listOf(LOWER_IMPACT, LOWER_MUSCULAR, UPPER_MUSCULAR)
    }
}

/**
 * A strain value per channel. Immutable and additive, so a day's sessions combine by [plus] and a
 * timeline decays by [times] without either operation needing to know what the channels mean.
 */
data class StrainVector(
    val lowerImpact: Double = 0.0,
    val lowerMuscular: Double = 0.0,
    val upperMuscular: Double = 0.0,
    val systemic: Double = 0.0
) {
    operator fun get(channel: StrainChannel): Double = when (channel) {
        StrainChannel.LOWER_IMPACT -> lowerImpact
        StrainChannel.LOWER_MUSCULAR -> lowerMuscular
        StrainChannel.UPPER_MUSCULAR -> upperMuscular
        StrainChannel.SYSTEMIC -> systemic
    }

    operator fun plus(other: StrainVector) = StrainVector(
        lowerImpact = lowerImpact + other.lowerImpact,
        lowerMuscular = lowerMuscular + other.lowerMuscular,
        upperMuscular = upperMuscular + other.upperMuscular,
        systemic = systemic + other.systemic
    )

    operator fun times(factor: Double) = StrainVector(
        lowerImpact = lowerImpact * factor,
        lowerMuscular = lowerMuscular * factor,
        upperMuscular = upperMuscular * factor,
        systemic = systemic * factor
    )

    /** Applies a per-channel factor — used to decay each channel on its own clock. */
    fun scaledPerChannel(factor: (StrainChannel) -> Double) = StrainVector(
        lowerImpact = lowerImpact * factor(StrainChannel.LOWER_IMPACT),
        lowerMuscular = lowerMuscular * factor(StrainChannel.LOWER_MUSCULAR),
        upperMuscular = upperMuscular * factor(StrainChannel.UPPER_MUSCULAR),
        systemic = systemic * factor(StrainChannel.SYSTEMIC)
    )

    val isEmpty: Boolean
        get() = lowerImpact == 0.0 && lowerMuscular == 0.0 && upperMuscular == 0.0 && systemic == 0.0

    fun asMap(): Map<StrainChannel, Double> =
        StrainChannel.entries.associateWith { this[it] }

    companion object {
        val ZERO = StrainVector()

        fun of(channel: StrainChannel, value: Double): StrainVector = when (channel) {
            StrainChannel.LOWER_IMPACT -> StrainVector(lowerImpact = value)
            StrainChannel.LOWER_MUSCULAR -> StrainVector(lowerMuscular = value)
            StrainChannel.UPPER_MUSCULAR -> StrainVector(upperMuscular = value)
            StrainChannel.SYSTEMIC -> StrainVector(systemic = value)
        }
    }
}
