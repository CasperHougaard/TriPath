package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A saved nutrition entry — label plus macros — that can be re-applied to a day without
 * retyping it, e.g. "Protein shake" or "Chicken & rice".
 */
@Entity(tableName = "nutrition_presets")
data class NutritionPreset(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val createdAt: Long
)
