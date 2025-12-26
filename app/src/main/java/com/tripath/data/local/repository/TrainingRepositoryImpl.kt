package com.tripath.data.local.repository

import com.tripath.data.local.database.dao.DayNoteDao
import com.tripath.data.local.database.dao.DayTemplateDao
import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.dao.SpecialPeriodDao
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Implementation of TrainingRepository using Room DAOs and PreferencesManager.
 */
class TrainingRepositoryImpl @Inject constructor(
    private val trainingPlanDao: TrainingPlanDao,
    private val workoutLogDao: WorkoutLogDao,
    private val preferencesManager: PreferencesManager,
    private val specialPeriodDao: SpecialPeriodDao,
    private val dayNoteDao: DayNoteDao,
    private val dayTemplateDao: DayTemplateDao
) : TrainingRepository {

    // ==================== Training Plan Operations ====================

    override fun getAllTrainingPlans(): Flow<List<TrainingPlan>> = 
        trainingPlanDao.getAll()

    override suspend fun getAllTrainingPlansOnce(): List<TrainingPlan> =
        trainingPlanDao.getAllOnce()

    override suspend fun getTrainingPlanById(id: String): TrainingPlan? =
        trainingPlanDao.getById(id)

    override fun getTrainingPlansByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TrainingPlan>> =
        trainingPlanDao.getByDateRange(startDate.toEpochDay(), endDate.toEpochDay())

    override fun getTrainingPlansByType(type: WorkoutType): Flow<List<TrainingPlan>> =
        trainingPlanDao.getByType(type)

    override suspend fun insertTrainingPlan(plan: TrainingPlan) =
        trainingPlanDao.insert(plan)

    override suspend fun insertTrainingPlans(plans: List<TrainingPlan>) =
        trainingPlanDao.insertAll(plans)

    override suspend fun updateTrainingPlan(plan: TrainingPlan) =
        trainingPlanDao.update(plan)

    override suspend fun deleteTrainingPlan(plan: TrainingPlan) =
        trainingPlanDao.delete(plan)

    override suspend fun deleteAllTrainingPlans() =
        trainingPlanDao.deleteAll()

    override suspend fun copyWeek(sourceStartDate: LocalDate, targetStartDate: LocalDate) {
        withContext(Dispatchers.IO) {
            val sourceEndDate = sourceStartDate.plusDays(6)
            val sourcePlans = trainingPlanDao.getByDateRangeOnce(
                sourceStartDate.toEpochDay(),
                sourceEndDate.toEpochDay()
            )
            
            val daysOffset = java.time.temporal.ChronoUnit.DAYS.between(sourceStartDate, targetStartDate)
            
            val newPlans = sourcePlans.map { plan ->
                plan.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    date = plan.date.plusDays(daysOffset)
                )
            }
            
            trainingPlanDao.insertAll(newPlans)
        }
    }

    // ==================== Workout Log Operations ====================

    override fun getAllWorkoutLogs(): Flow<List<WorkoutLog>> =
        workoutLogDao.getAll()

    override suspend fun getAllWorkoutLogsOnce(): List<WorkoutLog> =
        workoutLogDao.getAllOnce()

    override suspend fun getWorkoutLogByConnectId(connectId: String): WorkoutLog? =
        workoutLogDao.getByConnectId(connectId)

    override fun getWorkoutLogsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkoutLog>> =
        workoutLogDao.getByDateRange(startDate.toEpochDay(), endDate.toEpochDay())

    override fun getWorkoutLogsByType(type: WorkoutType): Flow<List<WorkoutLog>> =
        workoutLogDao.getByType(type)

    override suspend fun insertWorkoutLog(log: WorkoutLog) =
        workoutLogDao.insert(log)

    override suspend fun insertWorkoutLogs(logs: List<WorkoutLog>) =
        workoutLogDao.insertAll(logs)

    override suspend fun deleteWorkoutLog(log: WorkoutLog) =
        workoutLogDao.delete(log)

    override suspend fun deleteAllWorkoutLogs() =
        workoutLogDao.deleteAll()

    override suspend fun workoutLogExists(connectId: String): Boolean =
        workoutLogDao.exists(connectId)

    // ==================== User Profile Operations ====================

    override fun getUserProfile(): Flow<UserProfile?> =
        preferencesManager.userProfileFlow

    override suspend fun getUserProfileOnce(): UserProfile? =
        preferencesManager.getUserProfile()

    override suspend fun upsertUserProfile(profile: UserProfile) =
        preferencesManager.saveUserProfile(profile)

    override suspend fun deleteUserProfile() =
        preferencesManager.deleteUserProfile()

    // ==================== Bulk Operations ====================

    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            trainingPlanDao.deleteAll()
            workoutLogDao.deleteAll()
            // Clear user profile from DataStore
            preferencesManager.deleteUserProfile()
            specialPeriodDao.deleteAll()
        }
    }

    // ==================== Special Period Operations ====================

    override fun getAllSpecialPeriods(): Flow<List<SpecialPeriod>> =
        specialPeriodDao.getAll()

    override suspend fun getAllSpecialPeriodsOnce(): List<SpecialPeriod> =
        specialPeriodDao.getAllOnce()

    override fun getActiveSpecialPeriods(date: LocalDate): Flow<List<SpecialPeriod>> =
        specialPeriodDao.getActivePeriods(date)

    override fun getSpecialPeriodsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SpecialPeriod>> =
        specialPeriodDao.getByDateRange(startDate, endDate)

    override suspend fun insertSpecialPeriod(specialPeriod: SpecialPeriod) =
        specialPeriodDao.insert(specialPeriod)

    override suspend fun insertSpecialPeriods(periods: List<SpecialPeriod>) {
        periods.forEach { specialPeriodDao.insert(it) }
    }

    override suspend fun deleteSpecialPeriod(id: String) =
        specialPeriodDao.deleteById(id)

    // ==================== Day Note Operations ====================

    override fun getDayNote(date: LocalDate): Flow<DayNote?> =
        dayNoteDao.getByDate(date)

    override suspend fun getDayNoteOnce(date: LocalDate): DayNote? =
        dayNoteDao.getByDateOnce(date)

    override suspend fun insertDayNote(dayNote: DayNote) =
        dayNoteDao.insert(dayNote)

    override suspend fun updateDayNote(dayNote: DayNote) =
        dayNoteDao.update(dayNote)

    override suspend fun deleteDayNote(dayNote: DayNote) =
        dayNoteDao.delete(dayNote)

    // ==================== Day Template Operations ====================

    override fun getAllDayTemplates(): Flow<List<DayTemplate>> =
        dayTemplateDao.getAll()

    override suspend fun getDayTemplateById(id: String): DayTemplate? =
        dayTemplateDao.getById(id)

    override suspend fun insertDayTemplate(template: DayTemplate) =
        dayTemplateDao.insert(template)

    override suspend fun deleteDayTemplate(template: DayTemplate) =
        dayTemplateDao.delete(template)
}
