package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS body_composition_logs (
                id TEXT NOT NULL PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                weightKg REAL,
                bodyFatPercent REAL,
                boneMassKg REAL,
                leanMassKg REAL,
                importedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
