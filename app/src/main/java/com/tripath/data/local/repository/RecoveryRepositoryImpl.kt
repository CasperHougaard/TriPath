// This file has been removed as part of the project cleanup.
package com.tripath.data.local.repository

import android.content.Context
import androidx.room.withTransaction
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.dao.BodyCompositionDao
import com.tripath.data.local.database.dao.DailyActivityDao
import com.tripath.data.local.database.dao.NutritionEntryDao
import com.tripath.data.local.database.dao.NutritionLogDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyActivityLog
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionEntryKind
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.widget.refreshNutritionWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RecoveryRepositoryImpl @Inject constructor(
    private val bodyCompositionDao: BodyCompositionDao,
    private val dailyActivityDao: DailyActivityDao,
    private val sleepLogDao: SleepLogDao,
    private val nutritionLogDao: NutritionLogDao,
    private val nutritionEntryDao: NutritionEntryDao,
    private val database: AppDatabase,
    @ApplicationContext private val context: Context
) : RecoveryRepository {

    /**
     * Nutrition can be edited from the app UI or from the widget's own buttons. Refreshing here,
     * on every write path, is what keeps the home-screen widget live-synced with the app instead
     * of only updating after its own quick-add/creatine taps.
     */
    private suspend fun refreshNutritionWidget() = refreshNutritionWidget(context)

    override fun getBodyCompositionLogs(): Flow<List<BodyCompositionLog>> =
        bodyCompositionDao.getAllLogs()

    override fun getDailyActivityLogs(): Flow<List<DailyActivityLog>> =
        dailyActivityDao.getAll()

    override fun getAllBodyCompositionLogsIncludingIgnored(): Flow<List<BodyCompositionLog>> =
        bodyCompositionDao.getAllLogsIncludingIgnored()

    override suspend fun getBodyCompositionInRange(from: LocalDate, to: LocalDate): List<BodyCompositionLog> =
        withContext(Dispatchers.IO) {
            val fromMillis = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val toMillis = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            bodyCompositionDao.getLogsInRange(fromMillis, toMillis)
        }

    override suspend fun getLatestBodyComposition(): BodyCompositionLog? =
        withContext(Dispatchers.IO) {
            bodyCompositionDao.getLatest()
        }

    override suspend fun setIgnored(id: String, isIgnored: Boolean) =
        withContext(Dispatchers.IO) {
            bodyCompositionDao.updateIgnored(id, isIgnored)
        }

    override fun getSleepLogs(): Flow<List<SleepLog>> =
        sleepLogDao.getAll()

    override fun getAllSleepLogsIncludingIgnored(): Flow<List<SleepLog>> =
        sleepLogDao.getAllIncludingIgnored()

    override suspend fun setSleepIgnored(connectId: String, isIgnored: Boolean) =
        withContext(Dispatchers.IO) {
            sleepLogDao.updateIgnored(connectId, isIgnored)
        }

    override fun getNutritionLogs(): Flow<List<NutritionLog>> =
        nutritionLogDao.getAll()

    override fun getNutritionLog(date: LocalDate): Flow<NutritionLog?> =
        nutritionLogDao.getByDateFlow(date)

    override fun getNutritionEntries(date: LocalDate): Flow<List<NutritionEntry>> =
        nutritionEntryDao.getByDateFlow(date)

    override suspend fun quickAddMacro(date: LocalDate, macro: NutritionMacro, grams: Double): Long {
        val entryId = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // The totals change and its ledger row go in together: a log that claims an add the
            // totals don't include (or the reverse) can never be undone back to a correct state.
            database.withTransaction {
                when (macro) {
                    NutritionMacro.ENERGY -> nutritionLogDao.addEnergy(date, grams, now)
                    NutritionMacro.PROTEIN -> nutritionLogDao.addProtein(date, grams, now)
                    NutritionMacro.CARBS -> nutritionLogDao.addCarbs(date, grams, now)
                    NutritionMacro.FAT -> nutritionLogDao.addFat(date, grams, now)
                }
                nutritionEntryDao.insert(
                    addEntry(date, NutritionEntryKind.QUICK_ADD, quickAddDeltas(macro, grams), now = now)
                )
            }
        }
        refreshNutritionWidget()
        return entryId
    }

    override suspend fun addNutrition(
        date: LocalDate,
        kcal: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        label: String?
    ): Long? {
        val deltas = NutritionDeltas(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat)
        if (deltas.isEmpty) return null
        val entryId = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.withTransaction {
                nutritionLogDao.addNutritionRaw(date, kcal, protein, carbs, fat, now)
                nutritionEntryDao.insert(
                    addEntry(date, NutritionEntryKind.CUSTOM_ADD, deltas, label, now)
                )
            }
        }
        refreshNutritionWidget()
        return entryId
    }

    override suspend fun setNutritionDay(
        date: LocalDate,
        kcal: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        creatineTaken: Boolean
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.withTransaction {
                val old = nutritionLogDao.getByDate(date)
                nutritionLogDao.upsert(
                    NutritionLog(
                        date = date,
                        energyKcal = kcal,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat,
                        creatineTaken = creatineTaken,
                        updatedAt = now
                    )
                )
                // Recorded as the delta it applied, so undoing an old edit later subtracts only
                // its own effect instead of stamping stale totals over anything logged since.
                val entry = adjustmentEntry(date, old, adjustmentDeltas(old, kcal, protein, carbs, fat), creatineTaken, now)
                if (!entry.isNoOp) nutritionEntryDao.insert(entry)
            }
        }
        refreshNutritionWidget()
    }

    override suspend fun undoNutritionEntry(entryId: Long) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.withTransaction {
                val entry = nutritionEntryDao.getById(entryId) ?: return@withTransaction
                nutritionLogDao.subtractNutritionRaw(
                    date = entry.date,
                    dKcal = entry.deltaKcal,
                    dProt = entry.deltaProteinG,
                    dCarb = entry.deltaCarbsG,
                    dFat = entry.deltaFatG,
                    now = now
                )
                entry.creatineFrom?.let { nutritionLogDao.setCreatine(entry.date, it, now) }
                nutritionEntryDao.deleteById(entryId)

                // Undoing the last thing logged for a day should leave "no data", not an empty
                // row that still shows up in the history list as a day with nothing in it.
                val remaining = nutritionLogDao.getByDate(entry.date)
                if (remaining != null && remaining.isEmpty() && nutritionEntryDao.countForDate(entry.date) == 0) {
                    nutritionLogDao.delete(remaining)
                }
            }
        }
        refreshNutritionWidget()
    }

    override suspend fun setCreatine(date: LocalDate, taken: Boolean) {
        withContext(Dispatchers.IO) {
            nutritionLogDao.setCreatine(date, taken, System.currentTimeMillis())
        }
        refreshNutritionWidget()
    }

    override suspend fun clearNutritionDay(date: LocalDate) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = nutritionLogDao.getByDate(date)
                if (existing != null) nutritionLogDao.delete(existing)
                nutritionEntryDao.deleteForDate(date)
            }
        }
        refreshNutritionWidget()
    }
}
