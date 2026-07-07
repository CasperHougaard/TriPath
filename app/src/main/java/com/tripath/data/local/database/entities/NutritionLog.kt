package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Manually-tracked daily nutrition totals.
 * One row per calendar date (stored as epoch day). Values are entered by the user
 * in-app (quick-add buttons or the custom add/edit dialog), not synced from any
 * external source.
 *
 * Each macro/energy field is nullable so an *unlogged* value stays `null` and is never
 * conflated with a genuine `0` intake — a day with no logging shows "no data", not zero.
 */
@Entity(tableName = "nutrition_logs")
data class NutritionLog(
    /** The calendar date this record covers (stored as epoch day). */
    @PrimaryKey
    val date: LocalDate,

    /** Total energy consumed in kilocalories, or null if not logged. */
    val energyKcal: Double? = null,

    /** Total protein in grams, or null if not logged. */
    val proteinG: Double? = null,

    /** Total carbohydrate in grams, or null if not logged. */
    val carbsG: Double? = null,

    /** Total fat in grams, or null if not logged. */
    val fatG: Double? = null,

    /** Whether creatine was taken on this day. */
    val creatineTaken: Boolean = false,

    /** Timestamp (epoch millis) when this row was last modified. */
    val updatedAt: Long = System.currentTimeMillis()
)
