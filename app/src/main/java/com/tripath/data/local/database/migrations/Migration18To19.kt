package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds an `isIgnored` flag to workout and sleep logs so individual synced data points
 * can be excluded from analytics/training-load/recovery while remaining in the database.
 * Mirrors the existing `isIgnored` column on body_composition_logs (added in 15→16).
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE workout_logs ADD COLUMN isIgnored INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE sleep_logs ADD COLUMN isIgnored INTEGER NOT NULL DEFAULT 0"
        )
    }
}
