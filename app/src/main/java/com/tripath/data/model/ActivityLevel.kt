package com.tripath.data.model

/**
 * Non-exercise activity level, used only as a fallback when step data is unavailable.
 *
 * Normally [com.tripath.domain.health.MetabolicModel.neatFactor] derives this from the day's actual
 * step count, which is both more accurate and one fewer thing to ask the user. This enum covers the
 * days where the phone stayed on the kitchen table.
 *
 * [DEFAULT] is 1.20 — the same fixed constant the app applied to every day before step data
 * existed, so nobody's numbers move when the derived factor is unavailable.
 */
enum class ActivityLevel(val factor: Double, val label: String, val description: String) {
    SEDENTARY(1.15, "Sedentary", "Desk job, little walking"),
    LIGHTLY_ACTIVE(1.20, "Lightly active", "Desk job with a daily walk"),
    ACTIVE(1.30, "Active", "On your feet much of the day"),
    VERY_ACTIVE(1.40, "Very active", "Physical job, constantly moving");

    companion object {
        val DEFAULT = LIGHTLY_ACTIVE

        fun fromName(name: String?): ActivityLevel? =
            entries.firstOrNull { it.name == name }
    }
}
