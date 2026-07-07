package com.tripath.ui.health

import com.tripath.data.local.database.entities.BodyCompositionLog

/**
 * Derived body-composition estimates that are computed from stored values rather than
 * persisted. Fat mass is estimated from weight and body-fat percentage; lean and bone
 * mass are already stored directly in kilograms by Health Connect.
 */

/** Estimated fat mass in kg: weight × body-fat% / 100. Null when either input is missing. */
val BodyCompositionLog.fatMassKg: Double?
    get() {
        val w = weightKg ?: return null
        val fat = bodyFatPercent ?: return null
        return w * fat / 100.0
    }

/** Estimated fat-free (lean) mass in kg: weight − fat mass. Null when either input is missing. */
val BodyCompositionLog.fatFreeMassKg: Double?
    get() {
        val w = weightKg ?: return null
        val fat = fatMassKg ?: return null
        return w - fat
    }
