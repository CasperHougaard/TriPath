package com.tripath.data.model

/**
 * What the athlete is trying to do with their body mass, and how fast.
 *
 * The rates are expressed as a percentage of body mass per week rather than absolute kilograms,
 * because the evidence scales that way: Garthe et al. (2011) found 0.7%/week preserved performance
 * and lean mass markedly better than 1.4%/week in athletes, and the same proportional logic applies
 * to gaining — faster than roughly 0.5%/week adds fat rather than muscle.
 *
 * [maxRatePctPerWeek] is a hard cap, not a suggestion: it is what stops the calorie maths handing
 * back a deficit that would cost muscle and training quality.
 */
enum class NutritionGoal(
    val label: String,
    val description: String,
    val defaultRatePctPerWeek: Double,
    val maxRatePctPerWeek: Double
) {
    LOSE_FAT(
        label = "Lose fat",
        description = "Reduce body mass while holding on to muscle and training quality",
        defaultRatePctPerWeek = -0.5,
        maxRatePctPerWeek = -1.0
    ),
    MAINTAIN(
        label = "Maintain",
        description = "Hold body mass steady and fuel the training",
        defaultRatePctPerWeek = 0.0,
        maxRatePctPerWeek = 0.0
    ),
    BUILD_MUSCLE(
        label = "Build muscle",
        description = "A controlled surplus aimed at lean mass rather than weight",
        defaultRatePctPerWeek = 0.25,
        maxRatePctPerWeek = 0.5
    ),
    RECOMPOSITION(
        label = "Recomposition",
        description = "Hold body mass while shifting fat to muscle — high protein, fuelled hard days",
        defaultRatePctPerWeek = 0.0,
        maxRatePctPerWeek = 0.0
    );

    /** True when the goal deliberately runs an energy deficit, which raises the protein target. */
    val isDeficit: Boolean get() = this == LOSE_FAT

    /**
     * [rate] clamped into the direction and magnitude this goal allows. A goal that does not move
     * body mass always returns 0, so a stale rate left over from a previous goal cannot leak in.
     */
    fun clampRate(rate: Double?): Double {
        if (maxRatePctPerWeek == 0.0) return 0.0
        val r = rate ?: defaultRatePctPerWeek
        return if (maxRatePctPerWeek < 0) {
            r.coerceIn(maxRatePctPerWeek, 0.0)
        } else {
            r.coerceIn(0.0, maxRatePctPerWeek)
        }
    }

    companion object {
        val DEFAULT = MAINTAIN

        fun fromName(name: String?): NutritionGoal? = entries.firstOrNull { it.name == name }
    }
}
