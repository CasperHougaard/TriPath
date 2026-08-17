package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionEntryKind
import com.tripath.data.local.database.entities.NutritionLog
import java.time.LocalDate

/**
 * The four amounts an add or edit applies to a day. A null field means "left alone" — distinct
 * from 0.0, which means "changed by nothing" and would still clear a value on undo.
 */
data class NutritionDeltas(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null
) {
    val isEmpty: Boolean
        get() = kcal == null && proteinG == null && carbsG == null && fatG == null
}

/** The delta a quick-add applies: exactly one field, chosen by [macro]. */
fun quickAddDeltas(macro: NutritionMacro, amount: Double): NutritionDeltas = when (macro) {
    NutritionMacro.ENERGY -> NutritionDeltas(kcal = amount)
    NutritionMacro.PROTEIN -> NutritionDeltas(proteinG = amount)
    NutritionMacro.CARBS -> NutritionDeltas(carbsG = amount)
    NutritionMacro.FAT -> NutritionDeltas(fatG = amount)
}

/**
 * Turns an absolute edit ("set today's calories to 2,000") into the delta it actually applies,
 * so the ledger holds one kind of thing and undo is always "subtract".
 *
 * A field is null in the result when it is genuinely untouched (unlogged before and after);
 * clearing a logged field yields a negative delta that undo restores.
 */
fun adjustmentDeltas(
    old: NutritionLog?,
    kcal: Double?,
    protein: Double?,
    carbs: Double?,
    fat: Double?
): NutritionDeltas {
    fun delta(newValue: Double?, oldValue: Double?): Double? {
        if (newValue == null && oldValue == null) return null
        val diff = (newValue ?: 0.0) - (oldValue ?: 0.0)
        return if (diff == 0.0) null else diff
    }
    return NutritionDeltas(
        kcal = delta(kcal, old?.energyKcal),
        proteinG = delta(protein, old?.proteinG),
        carbsG = delta(carbs, old?.carbsG),
        fatG = delta(fat, old?.fatG)
    )
}

/** Builds the ledger row for a quick-add or custom add. */
fun addEntry(
    date: LocalDate,
    kind: NutritionEntryKind,
    deltas: NutritionDeltas,
    label: String? = null,
    now: Long
): NutritionEntry = NutritionEntry(
    date = date,
    loggedAt = now,
    kind = kind,
    label = label?.trim()?.takeIf { it.isNotEmpty() },
    deltaKcal = deltas.kcal,
    deltaProteinG = deltas.proteinG,
    deltaCarbsG = deltas.carbsG,
    deltaFatG = deltas.fatG
)

/**
 * Builds the ledger row for a manual total edit, keeping the previous calorie/protein values so
 * the log can read "1,840 → 2,000" without recomputing anything.
 */
fun adjustmentEntry(
    date: LocalDate,
    old: NutritionLog?,
    deltas: NutritionDeltas,
    creatineTaken: Boolean,
    now: Long
): NutritionEntry = NutritionEntry(
    date = date,
    loggedAt = now,
    kind = NutritionEntryKind.ADJUSTMENT,
    deltaKcal = deltas.kcal,
    deltaProteinG = deltas.proteinG,
    deltaCarbsG = deltas.carbsG,
    deltaFatG = deltas.fatG,
    prevKcal = old?.energyKcal,
    prevProteinG = old?.proteinG,
    creatineFrom = (old?.creatineTaken ?: false).takeIf { it != creatineTaken },
    creatineTo = creatineTaken.takeIf { it != (old?.creatineTaken ?: false) }
)
