package com.tripath.domain.health

import com.tripath.data.model.BiologicalSex

/**
 * Healthy reference ranges and derived targets for body-composition and nutrition metrics.
 *
 * Ranges are sex- and age-aware where the science supports it. Sources are widely used
 * population guidelines (ACE / ACSM body-fat bands, WHO BMI cut-offs, Mifflin–St Jeor BMR).
 * These are guidance bands for a general adult population, not medical advice.
 */
object HealthReference {

    /** A healthy band `[min, max]` plus a short human label describing where a value falls. */
    data class Band(val min: Double, val max: Double) {
        val range: ClosedFloatingPointRange<Double> get() = min..max
    }

    // ---------------------------------------------------------------------------------------
    // Body fat %
    // ---------------------------------------------------------------------------------------

    /**
     * Healthy body-fat percentage band by sex and age. Age bands follow the commonly cited
     * ACSM / American Council on Exercise "fitness → healthy" reference tables.
     * Returns null when sex is unknown (age falls back to the 20–39 band).
     */
    fun bodyFatHealthyBand(sex: BiologicalSex?, age: Int?): Band? {
        sex ?: return null
        val a = age ?: 30
        return when (sex) {
            BiologicalSex.MALE -> when {
                a < 40 -> Band(8.0, 19.0)
                a < 60 -> Band(11.0, 21.0)
                else -> Band(13.0, 24.0)
            }
            BiologicalSex.FEMALE -> when {
                a < 40 -> Band(21.0, 32.0)
                a < 60 -> Band(23.0, 33.0)
                else -> Band(24.0, 35.0)
            }
        }
    }

    /**
     * Descriptive category for a body-fat percentage. Uses ACE categories, sex-specific.
     * Returns null when sex is unknown.
     */
    fun bodyFatCategory(sex: BiologicalSex?, percent: Double): String? {
        sex ?: return null
        return when (sex) {
            BiologicalSex.MALE -> when {
                percent < 6 -> "Essential"
                percent < 14 -> "Athletic"
                percent < 18 -> "Fitness"
                percent < 25 -> "Average"
                else -> "Above healthy"
            }
            BiologicalSex.FEMALE -> when {
                percent < 14 -> "Essential"
                percent < 21 -> "Athletic"
                percent < 25 -> "Fitness"
                percent < 32 -> "Average"
                else -> "Above healthy"
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // BMI
    // ---------------------------------------------------------------------------------------

    /** WHO healthy BMI band for adults (sex- and age-independent). */
    val bmiHealthyBand = Band(18.5, 24.9)

    /** Body mass index (kg/m²) from weight in kg and height in cm. Null on missing inputs. */
    fun bmi(weightKg: Double?, heightCm: Int?): Double? {
        val w = weightKg ?: return null
        val h = heightCm ?: return null
        if (h <= 0) return null
        val m = h / 100.0
        return w / (m * m)
    }

    /** WHO BMI category for a computed BMI value. */
    fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Healthy"
        bmi < 30.0 -> "Overweight"
        else -> "Obese"
    }

    // ---------------------------------------------------------------------------------------
    // FFMI (Fat-Free Mass Index)
    // ---------------------------------------------------------------------------------------

    /**
     * Fat-Free Mass Index (kg/m²) = fat-free mass / height². A body-composition analogue of
     * BMI that scales lean mass for height, useful for tracking muscular development.
     * Null on any missing input.
     */
    fun ffmi(fatFreeMassKg: Double?, heightCm: Int?): Double? {
        val ffm = fatFreeMassKg ?: return null
        val h = heightCm ?: return null
        if (h <= 0) return null
        val m = h / 100.0
        return ffm / (m * m)
    }

    /**
     * Descriptive FFMI category, sex-specific. Rough population guidance (natural trained
     * ranges): higher FFMI reflects more lean mass for height. Returns null when sex is unknown.
     */
    fun ffmiCategory(sex: BiologicalSex?, ffmi: Double): String? {
        sex ?: return null
        return when (sex) {
            BiologicalSex.MALE -> when {
                ffmi < 18.0 -> "Low"
                ffmi < 20.0 -> "Average"
                ffmi < 22.0 -> "Fit"
                ffmi < 25.0 -> "Athletic"
                else -> "Very muscular"
            }
            BiologicalSex.FEMALE -> when {
                ffmi < 15.0 -> "Low"
                ffmi < 17.0 -> "Average"
                ffmi < 19.0 -> "Fit"
                ffmi < 22.0 -> "Athletic"
                else -> "Very muscular"
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Nutrition targets
    // ---------------------------------------------------------------------------------------

    /**
     * Daily protein target band in grams for an active/endurance athlete: 1.6–2.2 g per kg
     * of body weight (ISSN / ACSM range for training adults). Null when weight is unknown.
     */
    fun proteinTargetGrams(weightKg: Double?): Band? {
        val w = weightKg ?: return null
        return Band(w * 1.6, w * 2.2)
    }

    /**
     * Basal metabolic rate (kcal/day) via Mifflin–St Jeor. Null on any missing input.
     */
    fun basalMetabolicRate(
        sex: BiologicalSex?,
        age: Int?,
        weightKg: Double?,
        heightCm: Int?
    ): Double? {
        val s = sex ?: return null
        val a = age ?: return null
        val w = weightKg ?: return null
        val h = heightCm ?: return null
        val base = 10 * w + 6.25 * h - 5 * a
        return when (s) {
            BiologicalSex.MALE -> base + 5
            BiologicalSex.FEMALE -> base - 161
        }
    }

    /**
     * Estimated maintenance calories (kcal/day) = BMR × activity factor. Defaults to a
     * "moderately active" 1.55 multiplier, reasonable for a training triathlete on average.
     * Null when BMR cannot be computed.
     */
    fun maintenanceCalories(
        sex: BiologicalSex?,
        age: Int?,
        weightKg: Double?,
        heightCm: Int?,
        activityFactor: Double = 1.55
    ): Double? = basalMetabolicRate(sex, age, weightKg, heightCm)?.let { it * activityFactor }
}
