package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

enum class SpecialPeriodType {
    INJURY,
    HOLIDAY,
    RECOVERY_WEEK
}

/**
 * Represents a special period that overrides the standard training phase.
 * Examples: Injury, Holiday, Manual Recovery Week.
 */
@Entity(tableName = "special_periods")
data class SpecialPeriod(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val type: SpecialPeriodType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null
)

