package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `nutrition_entries` — the itemised ledger behind each day's nutrition totals, so a
 * mistaken quick-add or custom add can be undone individually instead of by retyping the day.
 *
 * Purely additive: `nutrition_logs` is untouched and keeps holding the totals, so days logged
 * before this migration simply have no entries (the day sheet says so).
 *
 * The column list must match what Room generates for
 * [com.tripath.data.local.database.entities.NutritionEntry] exactly, or schema validation fails
 * on the next launch.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nutrition_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date INTEGER NOT NULL,
                loggedAt INTEGER NOT NULL,
                kind TEXT NOT NULL,
                label TEXT,
                deltaKcal REAL,
                deltaProteinG REAL,
                deltaCarbsG REAL,
                deltaFatG REAL,
                prevKcal REAL,
                prevProteinG REAL,
                creatineFrom INTEGER,
                creatineTo INTEGER
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_nutrition_entries_date ON nutrition_entries (date)"
        )
    }
}
