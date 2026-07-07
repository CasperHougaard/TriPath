// This file has been removed as part of the project cleanup.
package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.SleepLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RecoveryRepository {

    fun getBodyCompositionLogs(): Flow<List<BodyCompositionLog>>

    fun getAllBodyCompositionLogsIncludingIgnored(): Flow<List<BodyCompositionLog>>

    suspend fun getBodyCompositionInRange(from: LocalDate, to: LocalDate): List<BodyCompositionLog>

    suspend fun getLatestBodyComposition(): BodyCompositionLog?

    suspend fun setIgnored(id: String, isIgnored: Boolean)

    fun getSleepLogs(): Flow<List<SleepLog>>

    /** All sleep logs including ignored ones (for the synced-data management list). */
    fun getAllSleepLogsIncludingIgnored(): Flow<List<SleepLog>>

    /** Set whether a sleep session is ignored (excluded from recovery analytics). */
    suspend fun setSleepIgnored(connectId: String, isIgnored: Boolean)

    // ---- Nutrition (manual daily tracker) ----

    fun getNutritionLogs(): Flow<List<NutritionLog>>

    /** Observe a single day's nutrition, or null when nothing is logged for that date. */
    fun getNutritionLog(date: LocalDate): Flow<NutritionLog?>

    /** Atomically add [grams] to a single macro (or kcal) for [date]; other fields untouched. */
    suspend fun quickAddMacro(date: LocalDate, macro: NutritionMacro, grams: Double)

    /** Atomically add the given amounts together for [date]; null args leave that field alone. */
    suspend fun addNutrition(
        date: LocalDate,
        kcal: Double? = null,
        protein: Double? = null,
        carbs: Double? = null,
        fat: Double? = null
    )

    /** Set absolute values for [date] (edit dialog); blanks/null clear a field. */
    suspend fun setNutritionDay(
        date: LocalDate,
        kcal: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        creatineTaken: Boolean
    )

    /** Set the creatine-taken flag for [date] without disturbing macros. */
    suspend fun setCreatine(date: LocalDate, taken: Boolean)

    /** Remove all logged data for [date]. */
    suspend fun clearNutritionDay(date: LocalDate)
}

/** Which nutrition field a quick-add targets. */
enum class NutritionMacro { ENERGY, PROTEIN, CARBS, FAT }
