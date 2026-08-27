package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `scanned_foods` — the barcode → per-100g nutrition cache behind barcode-scan logging.
 * Purely additive, like [MIGRATION_21_22]: a device that never scans a barcode simply has an
 * empty table.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scanned_foods (
                barcode TEXT NOT NULL,
                name TEXT,
                kcalPer100g REAL,
                proteinPer100g REAL,
                isManualOverride INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(barcode)
            )
            """.trimIndent()
        )
    }
}
