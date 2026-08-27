package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A barcode → per-100g nutrition lookup, cached locally so a repeat scan works offline and a
 * fresh scan doesn't re-hit the network. [isManualOverride] marks a row the user corrected (or
 * filled in by hand, when Open Food Facts had no data) — those values are never overwritten by a
 * later API response.
 */
@Entity(tableName = "scanned_foods")
data class ScannedFoodCache(
    @PrimaryKey
    val barcode: String,
    val name: String?,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val isManualOverride: Boolean,
    val updatedAt: Long
)
