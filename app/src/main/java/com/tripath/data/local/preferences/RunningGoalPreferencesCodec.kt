package com.tripath.data.local.preferences

import com.tripath.data.local.backup.LocalDateSerializer
import com.tripath.domain.running.RunningGoal
import com.tripath.domain.running.RunningGoalType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate

object RunningGoalPreferencesCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(goal: RunningGoal): String {
        return json.encodeToString(goal.toPersisted())
    }

    fun decode(value: String?): RunningGoal? {
        if (value.isNullOrBlank()) return null
        return try {
            json.decodeFromString<PersistedRunningGoal>(value).toDomain()
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class PersistedRunningGoal(
        val type: String,
        val targetDistanceMeters: Int? = null,
        @Serializable(with = LocalDateSerializer::class)
        val targetDate: LocalDate? = null,
        val runsPerWeek: Int? = null,
        val preferredDays: List<String>? = null,
        val baselineLongestRunMeters: Int? = null,
        val baselineWeeklyRunMeters: Int? = null,
        val maxWeeklyProgressPercent: Float? = null
    )

    private fun RunningGoal.toPersisted(): PersistedRunningGoal {
        return PersistedRunningGoal(
            type = type.name,
            targetDistanceMeters = targetDistanceMeters,
            targetDate = targetDate,
            runsPerWeek = runsPerWeek,
            preferredDays = preferredDays?.map { it.name },
            baselineLongestRunMeters = baselineLongestRunMeters,
            baselineWeeklyRunMeters = baselineWeeklyRunMeters,
            maxWeeklyProgressPercent = maxWeeklyProgressPercent
        )
    }

    private fun PersistedRunningGoal.toDomain(): RunningGoal {
        return RunningGoal(
            type = RunningGoalType.valueOf(type),
            targetDistanceMeters = targetDistanceMeters,
            targetDate = targetDate,
            runsPerWeek = runsPerWeek,
            preferredDays = preferredDays?.map(DayOfWeek::valueOf),
            baselineLongestRunMeters = baselineLongestRunMeters,
            baselineWeeklyRunMeters = baselineWeeklyRunMeters,
            maxWeeklyProgressPercent = maxWeeklyProgressPercent
        )
    }
}