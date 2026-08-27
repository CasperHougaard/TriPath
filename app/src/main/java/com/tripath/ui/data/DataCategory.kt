package com.tripath.ui.data

/**
 * The kinds of data TriPath stores, as presented to the user in the My Data browser.
 *
 * One entry per Room entity the user themselves put there, plus [SETTINGS] for the DataStore
 * preferences — the point being that a user asking "what does this app know about me, and is it
 * safe?" gets a full answer rather than a curated one.
 *
 * Not yet covered: whole-day activity, and LiftPath's imported sessions, sets and exercise catalog.
 * Those are backed up and restored like everything else, so the gap is in the browser rather than in
 * the export, but it is a gap — anything added here should be added there too.
 */
enum class DataCategory(
    val id: String,
    val title: String,
    val description: String
) {
    WORKOUTS(
        id = "workouts",
        title = "Workouts",
        description = "Completed sessions, including imported ones"
    ),
    RAW_SAMPLES(
        id = "raw_samples",
        title = "Raw workout samples",
        description = "Heart rate, power, route and CNS data per session"
    ),
    PLANNED_SESSIONS(
        id = "planned_sessions",
        title = "Planned sessions",
        description = "Your training plan"
    ),
    SLEEP(
        id = "sleep",
        title = "Sleep",
        description = "Nightly duration, stages and scores"
    ),
    BODY_COMPOSITION(
        id = "body_composition",
        title = "Body composition",
        description = "Weight, body fat, lean and bone mass"
    ),
    NUTRITION(
        id = "nutrition",
        title = "Nutrition",
        description = "Daily energy, macros and creatine"
    ),
    NUTRITION_ENTRIES(
        id = "nutrition_entries",
        title = "Nutrition log entries",
        description = "Every individual add and edit behind the daily totals"
    ),
    NUTRITION_PRESETS(
        id = "nutrition_presets",
        title = "Nutrition presets",
        description = "Saved label and macro combinations for quick logging"
    ),
    SCANNED_FOODS(
        id = "scanned_foods",
        title = "Scanned foods",
        description = "Barcodes you have scanned, with their per-100g nutrition"
    ),
    WELLNESS_LOGS(
        id = "wellness_logs",
        title = "Wellness logs",
        description = "Daily recovery metrics"
    ),
    WELLNESS_TASKS(
        id = "wellness_tasks",
        title = "Wellness tasks",
        description = "Your task definitions"
    ),
    SPECIAL_PERIODS(
        id = "special_periods",
        title = "Special periods",
        description = "Injuries, holidays and recovery weeks"
    ),
    DAY_NOTES(
        id = "day_notes",
        title = "Day notes",
        description = "Notes attached to specific dates"
    ),
    DAY_TEMPLATES(
        id = "day_templates",
        title = "Day templates",
        description = "Reusable day plans"
    ),
    SETTINGS(
        id = "settings",
        title = "Settings & profile",
        description = "Every stored preference, including your physiological metrics"
    );

    companion object {
        fun fromId(id: String?): DataCategory? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One stored record, flattened for display.
 *
 * A single UI model for every table keeps the browser to one list screen instead of twelve
 * near-identical ones. [fields] is expected to carry *every* remaining column so that what the
 * user sees genuinely is all of their data.
 */
data class DataRecordUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val fields: List<Pair<String, String>> = emptyList(),
    val isIgnored: Boolean = false
)
