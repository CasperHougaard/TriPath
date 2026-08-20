package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the LiftPath set-sync tables — `lift_session_logs`, `lift_set_logs`,
 * `lift_exercise_catalog` — populated by [com.tripath.data.local.liftpath.LiftPathSyncManager].
 *
 * Purely additive, like [MIGRATION_20_21]: a device that never enables the LiftPath integration
 * simply has empty tables.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lift_session_logs (
                id TEXT NOT NULL,
                date INTEGER NOT NULL,
                startMillis INTEGER,
                durationSeconds INTEGER,
                planName TEXT,
                dominantIntent TEXT,
                totalSets INTEGER NOT NULL,
                importedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lift_set_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId TEXT NOT NULL,
                exerciseId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                kg REAL NOT NULL,
                reps INTEGER NOT NULL,
                rpe REAL,
                isWarmup INTEGER NOT NULL,
                intent TEXT,
                durationSeconds INTEGER,
                bodyweightKg REAL,
                FOREIGN KEY(sessionId) REFERENCES lift_session_logs(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_lift_set_logs_sessionId ON lift_set_logs(sessionId)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lift_exercise_catalog (
                id INTEGER NOT NULL,
                name TEXT NOT NULL,
                region TEXT,
                tier TEXT,
                pattern TEXT,
                mechanics TEXT,
                primaryTargets TEXT NOT NULL,
                secondaryTargets TEXT NOT NULL,
                importedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }
}
