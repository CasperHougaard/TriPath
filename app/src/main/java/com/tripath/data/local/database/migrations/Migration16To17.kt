package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nutrition_logs (
                date INTEGER NOT NULL PRIMARY KEY,
                energyKcal REAL,
                proteinG REAL,
                carbsG REAL,
                fatG REAL,
                fiberG REAL,
                sugarG REAL,
                entryCount INTEGER NOT NULL DEFAULT 0,
                importedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
