package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `nutrition_presets` — saved label-plus-macros entries that can be re-applied to a day
 * without retyping them.
 *
 * Purely additive, like [MIGRATION_21_22]: a device that never saves a preset simply has an empty
 * table.
 *
 * The column list must match what Room generates for
 * [com.tripath.data.local.database.entities.NutritionPreset] exactly, or schema validation fails
 * on the next launch.
 *
 * **No `23.json` was exported.** Version 23 and 24 were written in the same sitting, so Room only
 * ever generated a schema for the version the build ended on. Nothing is broken by this — every
 * migration here is hand-written, so none is derived from the exported schemas — but the chain in
 * `app/schemas` skips 23, and a `MigrationTestHelper` test starting from 23 has no schema to open.
 * To restore it, build once with `AppDatabase.version = 23`, keep the generated file, then set the
 * version back.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nutrition_presets (
                id TEXT NOT NULL,
                label TEXT NOT NULL,
                kcal REAL,
                proteinG REAL,
                carbsG REAL,
                fatG REAL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }
}
