package com.tripath.data.local.repository

import com.tripath.data.local.database.dao.BodyCompositionDao
import com.tripath.data.local.database.entities.BodyCompositionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RecoveryRepositoryImpl @Inject constructor(
    private val bodyCompositionDao: BodyCompositionDao
) : RecoveryRepository {

    override fun getBodyCompositionLogs(): Flow<List<BodyCompositionLog>> =
        bodyCompositionDao.getAllLogs()

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
}
