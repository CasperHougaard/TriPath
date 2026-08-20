package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `daily_activity_logs` — whole-day steps, calories and HRV from Health Connect.
 *
 * Purely additive. Days before this migration simply have no row, and every consumer treats a
 * missing row as "no step data" and falls back to the profile's activity level, so historical
 * figures are unchanged rather than silently recomputed.
 *
 * The column list must match what Room generates for
 * [com.tripath.data.local.database.entities.DailyActivityLog] exactly, or schema validation fails
 * on the next launch.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_activity_logs (
                date INTEGER NOT NULL,
                steps INTEGER,
                workoutSteps INTEGER,
                activeCaloriesKcal REAL,
                totalCaloriesKcal REAL,
                hrvRmssd REAL,
                importedAt INTEGER NOT NULL,
                PRIMARY KEY(date)
            )
            """.trimIndent()
        )
    }
}
