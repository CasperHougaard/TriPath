// This file has been removed as part of the project cleanup.
package com.tripath.data.local.repository

import android.content.Context
import com.tripath.data.local.database.dao.BodyCompositionDao
import com.tripath.data.local.database.dao.NutritionLogDao
import com.tripath.data.local.database.dao.SleepLogDao
import com.tripath.data.local.database.entities.BodyCompositionLog
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
    private val sleepLogDao: SleepLogDao,
    private val nutritionLogDao: NutritionLogDao,
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

    override suspend fun quickAddMacro(date: LocalDate, macro: NutritionMacro, grams: Double) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            when (macro) {
                NutritionMacro.ENERGY -> nutritionLogDao.addEnergy(date, grams, now)
                NutritionMacro.PROTEIN -> nutritionLogDao.addProtein(date, grams, now)
                NutritionMacro.CARBS -> nutritionLogDao.addCarbs(date, grams, now)
                NutritionMacro.FAT -> nutritionLogDao.addFat(date, grams, now)
            }
        }
        refreshNutritionWidget()
    }

    override suspend fun addNutrition(
        date: LocalDate,
        kcal: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?
    ) {
        withContext(Dispatchers.IO) {
            nutritionLogDao.addNutritionRaw(date, kcal, protein, carbs, fat, System.currentTimeMillis())
        }
        refreshNutritionWidget()
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
            nutritionLogDao.upsert(
                NutritionLog(
                    date = date,
                    energyKcal = kcal,
                    proteinG = protein,
                    carbsG = carbs,
                    fatG = fat,
                    creatineTaken = creatineTaken,
                    updatedAt = System.currentTimeMillis()
                )
            )
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
            val existing = nutritionLogDao.getByDate(date)
            if (existing != null) nutritionLogDao.delete(existing)
        }
        refreshNutritionWidget()
    }
}
