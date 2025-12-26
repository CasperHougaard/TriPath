package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Represents a note for a specific day in the training plan.
 */
@Entity(tableName = "day_notes")
data class DayNote(
    @PrimaryKey
    val date: LocalDate,
    val note: String
)

