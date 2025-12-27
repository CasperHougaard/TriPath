package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TaskTriggerType
import java.time.LocalDate

/**
 * Represents a daily wellness log entry containing recovery metrics.
 */
@Entity(tableName = "daily_wellness_logs")
data class DailyWellnessLog(
    @PrimaryKey
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val hrvRmssd: Double? = null,
    val morningWeight: Double? = null,
    val sorenessIndex: Int? = null, // 1-10
    val moodIndex: Int? = null, // 1-10
    val allergySeverity: AllergySeverity? = null,
    val completedTaskIds: List<Long>? = null
)

/**
 * Represents a wellness task definition that can be triggered based on various conditions.
 */
@Entity(tableName = "wellness_task_definitions")
data class WellnessTaskDefinition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val type: TaskTriggerType,
    val triggerThreshold: Int? = null
)
