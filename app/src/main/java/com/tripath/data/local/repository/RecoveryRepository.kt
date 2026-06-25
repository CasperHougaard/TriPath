package com.tripath.data.local.repository

import com.tripath.data.local.database.entities.BodyCompositionLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RecoveryRepository {

    fun getBodyCompositionLogs(): Flow<List<BodyCompositionLog>>

    suspend fun getBodyCompositionInRange(from: LocalDate, to: LocalDate): List<BodyCompositionLog>

    suspend fun getLatestBodyComposition(): BodyCompositionLog?
}
