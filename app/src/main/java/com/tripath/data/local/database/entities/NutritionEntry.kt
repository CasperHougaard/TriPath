package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** How a [NutritionEntry] came to be — decides how it reads in the day log. */
enum class NutritionEntryKind {
    /** A one-tap add of a single macro, from the app or the home-screen widget. */
    QUICK_ADD,

    /** The "Custom add" dialog: several fields at once, optionally labelled. */
    CUSTOM_ADD,

    /** A manual total edit (tile inline edit or the edit-day dialog), recorded as a delta. */
    ADJUSTMENT
}

/**
 * One itemised change to a day's nutrition — the ledger behind [NutritionLog]'s totals.
 *
 * ## Everything is a delta
 * Even an absolute edit ("set calories to 2,000") is stored as the delta it applied
 * (`+160` when the day held 1,840). That makes undo uniform — always "subtract the deltas" —
 * and order-independent, so undoing an old edit can never wipe out adds made after it.
 *
 * A null delta means the entry did not touch that field, which is distinct from a 0 delta.
 *
 * [NutritionLog] remains the source of truth for totals: days logged before this table existed
 * have no entries at all, and the totals must still be right for them.
 */
@Entity(tableName = "nutrition_entries", indices = [Index("date")])
data class NutritionEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The day whose totals this entry changed. */
    val date: LocalDate,

    /** When the entry was recorded (epoch millis) — orders the log and shows the time of day. */
    val loggedAt: Long,

    val kind: NutritionEntryKind,

    /** Optional user-supplied name for a custom add, e.g. "Chicken & rice". */
    val label: String? = null,

    /** Amount added to each field; null where the entry left the field alone. */
    val deltaKcal: Double? = null,
    val deltaProteinG: Double? = null,
    val deltaCarbsG: Double? = null,
    val deltaFatG: Double? = null,

    /** [NutritionEntryKind.ADJUSTMENT] only, for display ("1,840 → 2,000"). Not used by undo. */
    val prevKcal: Double? = null,
    val prevProteinG: Double? = null,

    /**
     * Creatine flag before/after, when an adjustment changed it. Null on entries that left the
     * flag alone — the standalone creatine toggle is idempotent and stays out of the ledger.
     */
    val creatineFrom: Boolean? = null,
    val creatineTo: Boolean? = null
) {
    /** True when this entry changed nothing — such entries are never persisted. */
    val isNoOp: Boolean
        get() = deltaKcal == null && deltaProteinG == null && deltaCarbsG == null &&
            deltaFatG == null && creatineFrom == creatineTo
}
