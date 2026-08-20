package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A snapshot of one entry in LiftPath's exercise library, so muscle-group mapping (Phase 3's
 * strain model) works even when LiftPath is not currently installed or synced.
 *
 * [primaryTargets]/[secondaryTargets] are comma-separated [com.liftpath.models.TargetMuscle] names
 * (kept as CSV rather than a `List<String>` so no type converter is needed for this small table).
 */
@Entity(tableName = "lift_exercise_catalog")
data class LiftExerciseCatalogEntry(
    @PrimaryKey
    val id: Int,
    val name: String,
    /** [com.liftpath.models.BodyRegion] name. */
    val region: String? = null,
    /** [com.liftpath.models.Tier] name. */
    val tier: String? = null,
    /** [com.liftpath.models.MovementPattern] name. */
    val pattern: String? = null,
    /** [com.liftpath.models.Mechanics] name. */
    val mechanics: String? = null,
    val primaryTargets: String = "",
    val secondaryTargets: String = "",
    val importedAt: Long = System.currentTimeMillis()
)
