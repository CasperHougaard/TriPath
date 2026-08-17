package com.tripath.ui.health.nutrition

import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionEntryKind

/**
 * How a ledger entry reads in the day log. Kept free of Compose so the wording is unit-testable.
 */

/** "+100 kcal", "Chicken & rice", "Edited totals" — the headline for a row. */
fun entryTitle(entry: NutritionEntry): String {
    entry.label?.let { return it }
    return when (entry.kind) {
        NutritionEntryKind.QUICK_ADD -> entryDeltaSummary(entry).ifEmpty { "Quick add" }
        NutritionEntryKind.CUSTOM_ADD -> "Custom add"
        NutritionEntryKind.ADJUSTMENT -> "Edited totals"
    }
}

/**
 * The amounts a row applied, signed: "+620 kcal · +45 g protein", "−100 kcal".
 * Empty when the entry only flipped the creatine flag.
 */
fun entryDeltaSummary(entry: NutritionEntry): String {
    val parts = listOfNotNull(
        entry.deltaKcal?.let { "${signed(it)} kcal" },
        entry.deltaProteinG?.let { "${signed(it)} g protein" },
        entry.deltaCarbsG?.let { "${signed(it)} g carbs" },
        entry.deltaFatG?.let { "${signed(it)} g fat" }
    )
    return parts.joinToString(" · ")
}

/**
 * Secondary line for a row: what an edit changed the totals from, plus any creatine flip.
 * Null when there is nothing extra to say (a plain add).
 */
fun entryDetailLine(entry: NutritionEntry): String? {
    if (entry.kind != NutritionEntryKind.ADJUSTMENT) return null
    val parts = listOfNotNull(
        entry.deltaKcal?.let { d ->
            val from = entry.prevKcal ?: 0.0
            "%,.0f → %,.0f kcal".format(from, from + d)
        },
        entry.deltaProteinG?.let { d ->
            val from = entry.prevProteinG ?: 0.0
            "%.0f → %.0f g protein".format(from, from + d)
        },
        entry.creatineTo?.let { if (it) "creatine on" else "creatine off" }
    )
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** Signed amount with no decimals: 100.0 -> "+100", -100.0 -> "−100" (a true minus sign). */
private fun signed(value: Double): String =
    if (value < 0) "−%,.0f".format(-value) else "+%,.0f".format(value)
