package com.tripath.data.local.database.util

import com.tripath.data.local.backup.TrainingPlanDto
import com.tripath.data.local.backup.toDto
import com.tripath.data.local.backup.toEntity
import com.tripath.data.local.database.entities.TrainingPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Utility for serializing and deserializing day templates.
 */
object TemplateSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serializes a list of training plans to a JSON string.
     */
    fun serializeActivities(activities: List<TrainingPlan>): String {
        val dtos = activities.map { it.toDto() }
        return json.encodeToString(dtos)
    }

    /**
     * Deserializes a JSON string to a list of training plans for a specific date.
     */
    fun deserializeActivities(jsonString: String, targetDate: LocalDate): List<TrainingPlan> {
        return try {
            val dtos = json.decodeFromString<List<TrainingPlanDto>>(jsonString)
            dtos.map { it.toEntity().copy(id = java.util.UUID.randomUUID().toString(), date = targetDate) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

