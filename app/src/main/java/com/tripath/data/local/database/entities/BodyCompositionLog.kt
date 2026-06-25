package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_composition_logs")
data class BodyCompositionLog(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val boneMassKg: Double?,
    val leanMassKg: Double?,
    val importedAt: Long
)
