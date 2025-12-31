package com.tripath.data.local.repository

import com.tripath.data.local.database.dao.WellnessDao
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.model.TaskTriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Implementation of RecoveryRepository using WellnessDao.
 */
class RecoveryRepositoryImpl @Inject constructor(
    private val wellnessDao: WellnessDao
) : RecoveryRepository {

    // ==================== Daily Wellness Log Operations ====================

    override suspend fun getWellnessLog(date: LocalDate): DailyWellnessLog? =
        withContext(Dispatchers.IO) {
            wellnessDao.getLogByDate(date)
        }

    override fun getWellnessLogFlow(date: LocalDate): Flow<DailyWellnessLog?> =
        wellnessDao.getLogByDateFlow(date)

    override fun getAllWellnessLogs(): Flow<List<DailyWellnessLog>> =
        wellnessDao.getAllLogs()

    override suspend fun getAllWellnessLogsOnce(): List<DailyWellnessLog> =
        withContext(Dispatchers.IO) {
            wellnessDao.getAllLogsOnce()
        }

    override fun getWellnessLogsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DailyWellnessLog>> =
        wellnessDao.getLogsByDateRange(startDate, endDate)

    override suspend fun getLastRecordedWeight(currentDate: LocalDate): Double? =
        withContext(Dispatchers.IO) {
            wellnessDao.getLastRecordedWeight(currentDate)
        }

    override suspend fun insertWellnessLog(log: DailyWellnessLog) =
        withContext(Dispatchers.IO) {
            wellnessDao.insertLog(log)
        }

    override suspend fun updateWellnessLog(log: DailyWellnessLog) =
        withContext(Dispatchers.IO) {
            wellnessDao.updateLog(log)
        }

    override suspend fun deleteWellnessLog(log: DailyWellnessLog) =
        withContext(Dispatchers.IO) {
            wellnessDao.deleteLog(log)
        }

    // ==================== Wellness Task Definition Operations ====================

    override fun getAllTasks(): Flow<List<WellnessTaskDefinition>> =
        wellnessDao.getAllTasks()

    override suspend fun getAllTasksOnce(): List<WellnessTaskDefinition> =
        withContext(Dispatchers.IO) {
            wellnessDao.getAllTasksOnce()
        }

    override suspend fun getTaskById(id: Long): WellnessTaskDefinition? =
        withContext(Dispatchers.IO) {
            wellnessDao.getTaskById(id)
        }

    override suspend fun insertTask(task: WellnessTaskDefinition): Long =
        withContext(Dispatchers.IO) {
            wellnessDao.insertTask(task)
        }

    override suspend fun insertTasks(tasks: List<WellnessTaskDefinition>) =
        withContext(Dispatchers.IO) {
            wellnessDao.insertTasks(tasks)
        }

    override suspend fun updateTask(task: WellnessTaskDefinition) =
        withContext(Dispatchers.IO) {
            wellnessDao.updateTask(task)
        }

    override suspend fun deleteTask(task: WellnessTaskDefinition) =
        withContext(Dispatchers.IO) {
            wellnessDao.deleteTask(task)
        }

    override suspend fun deleteTaskById(id: Long) =
        withContext(Dispatchers.IO) {
            wellnessDao.deleteTaskById(id)
        }

    override suspend fun deleteAllLogs() =
        withContext(Dispatchers.IO) {
            wellnessDao.deleteAllLogs()
        }

    override suspend fun deleteAllTasks() =
        withContext(Dispatchers.IO) {
            wellnessDao.deleteAllTasks()
        }

    // ==================== Initialization ====================

    override suspend fun initializeDefaults() = withContext(Dispatchers.IO) {
        // Get existing tasks
        val existingTasks = wellnessDao.getAllTasksOnce()
        val existingTitles = existingTasks.map { it.title.lowercase() }.toSet()
        
        // Define default tasks
        val defaultTasks = listOf(
            WellnessTaskDefinition(
                id = 0,
                title = "Creatine",
                description = "Take creatine supplement",
                type = TaskTriggerType.DAILY,
                triggerThreshold = null
            ),
            WellnessTaskDefinition(
                id = 0,
                title = "Vitamins",
                description = "Take daily vitamins",
                type = TaskTriggerType.DAILY,
                triggerThreshold = null
            ),
            WellnessTaskDefinition(
                id = 0,
                title = "Stretching",
                description = "Perform daily stretching routine",
                type = TaskTriggerType.DAILY,
                triggerThreshold = null
            )
        )
        
        // Only insert default tasks that don't already exist (by title)
        val tasksToInsert = defaultTasks.filter { 
            it.title.lowercase() !in existingTitles 
        }
        
        if (tasksToInsert.isNotEmpty()) {
            wellnessDao.insertTasks(tasksToInsert)
        }
    }
}
