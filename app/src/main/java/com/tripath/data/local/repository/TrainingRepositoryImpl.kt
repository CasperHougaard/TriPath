package com.tripath.data.local.repository

import com.tripath.data.local.database.dao.TrainingPlanDao
import com.tripath.data.local.database.dao.UserProfileDao
import com.tripath.data.local.database.dao.WorkoutLogDao
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.UserProfile
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TrainingRepository.
 * Handles all data operations through Room DAOs.
 */
@Singleton
class TrainingRepositoryImpl @Inject constructor(
    private val trainingPlanDao: TrainingPlanDao,
    private val workoutLogDao: WorkoutLogDao,
    private val userProfileDao: UserProfileDao
) : TrainingRepository {

    // ==================== Training Plan Operations ====================

    override fun getAllTrainingPlans(): Flow<List<TrainingPlan>> {
        return trainingPlanDao.getAll()
    }

    override suspend fun getAllTrainingPlansOnce(): List<TrainingPlan> {
        return trainingPlanDao.getAllOnce()
    }

    override suspend fun getTrainingPlanById(id: String): TrainingPlan? {
        return trainingPlanDao.getById(id)
    }

    override fun getTrainingPlansByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TrainingPlan>> {
        return trainingPlanDao.getByDateRange(
            startDate.toEpochDay(),
            endDate.toEpochDay()
        )
    }

    override fun getTrainingPlansByType(type: WorkoutType): Flow<List<TrainingPlan>> {
        return trainingPlanDao.getByType(type)
    }

    override suspend fun insertTrainingPlan(plan: TrainingPlan) {
        trainingPlanDao.insert(plan)
    }

    override suspend fun insertTrainingPlans(plans: List<TrainingPlan>) {
        trainingPlanDao.insertAll(plans)
    }

    override suspend fun updateTrainingPlan(plan: TrainingPlan) {
        trainingPlanDao.update(plan)
    }

    override suspend fun deleteTrainingPlan(plan: TrainingPlan) {
        trainingPlanDao.delete(plan)
    }

    override suspend fun deleteAllTrainingPlans() {
        trainingPlanDao.deleteAll()
    }

    // ==================== Workout Log Operations ====================

    override fun getAllWorkoutLogs(): Flow<List<WorkoutLog>> {
        return workoutLogDao.getAll()
    }

    override suspend fun getAllWorkoutLogsOnce(): List<WorkoutLog> {
        return workoutLogDao.getAllOnce()
    }

    override suspend fun getWorkoutLogByConnectId(connectId: String): WorkoutLog? {
        return workoutLogDao.getByConnectId(connectId)
    }

    override fun getWorkoutLogsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<WorkoutLog>> {
        return workoutLogDao.getByDateRange(
            startDate.toEpochDay(),
            endDate.toEpochDay()
        )
    }

    override fun getWorkoutLogsByType(type: WorkoutType): Flow<List<WorkoutLog>> {
        return workoutLogDao.getByType(type)
    }

    override suspend fun insertWorkoutLog(log: WorkoutLog) {
        workoutLogDao.insert(log)
    }

    override suspend fun insertWorkoutLogs(logs: List<WorkoutLog>) {
        workoutLogDao.insertAll(logs)
    }

    override suspend fun deleteWorkoutLog(log: WorkoutLog) {
        workoutLogDao.delete(log)
    }

    override suspend fun deleteAllWorkoutLogs() {
        workoutLogDao.deleteAll()
    }

    override suspend fun workoutLogExists(connectId: String): Boolean {
        return workoutLogDao.exists(connectId)
    }

    // ==================== User Profile Operations ====================

    override fun getUserProfile(): Flow<UserProfile?> {
        return userProfileDao.getProfile()
    }

    override suspend fun getUserProfileOnce(): UserProfile? {
        return userProfileDao.getProfileOnce()
    }

    override suspend fun upsertUserProfile(profile: UserProfile) {
        userProfileDao.upsert(profile)
    }

    override suspend fun deleteUserProfile() {
        userProfileDao.delete()
    }

    // ==================== Bulk Operations ====================

    override suspend fun clearAllData() {
        trainingPlanDao.deleteAll()
        workoutLogDao.deleteAll()
        userProfileDao.delete()
    }
}

