package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Converts nutrition_logs from a Health-Connect-synced aggregate into a manual daily tracker.
 * Adds `creatineTaken`, renames the timestamp semantics to `updatedAt`, and drops the
 * Health-Connect-only columns (fiberG, sugarG, entryCount, importedAt).
 *
 * SQLite can't drop columns in place, so the table is rebuilt: create new → copy kept
 * columns → drop old → rename. Existing calories/macros are preserved.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE nutrition_logs_new (
                date INTEGER NOT NULL PRIMARY KEY,
                energyKcal REAL,
                proteinG REAL,
                carbsG REAL,
                fatG REAL,
                creatineTaken INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO nutrition_logs_new (date, energyKcal, proteinG, carbsG, fatG, updatedAt)
            SELECT date, energyKcal, proteinG, carbsG, fatG, importedAt FROM nutrition_logs
            """.trimIndent()
        )
        database.execSQL("DROP TABLE nutrition_logs")
        database.execSQL("ALTER TABLE nutrition_logs_new RENAME TO nutrition_logs")
    }
}
