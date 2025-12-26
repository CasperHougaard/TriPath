package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a reusable template for a day's planned activities.
 * Activities are stored as a JSON string for flexibility.
 */
@Entity(tableName = "day_templates")
data class DayTemplate(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** JSON serialized list of TrainingPlanDto */
    val activitiesJson: String
)

