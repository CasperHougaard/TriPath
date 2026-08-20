package com.tripath.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One LiftPath lifting session, synced read-only from `content://com.liftpath.share`.
 *
 * [id] is LiftPath's own session id, so re-syncing the same session (edited notes, a corrected
 * set) replaces the row rather than duplicating it. See
 * [com.tripath.data.local.liftpath.LiftPathSyncManager] for how this is populated.
 */
@Entity(tableName = "lift_session_logs")
data class LiftSessionLog(
    @PrimaryKey
    val id: String,
    val date: LocalDate,
    /** Wall-clock start, when LiftPath ever tracks it. Usually null — LiftPath only stores [date]. */
    val startMillis: Long? = null,
    val durationSeconds: Long? = null,
    val planName: String? = null,
    /** [com.liftpath.models.SetIntent] name of the session's dominant intent, if resolvable. */
    val dominantIntent: String? = null,
    val totalSets: Int = 0,
    val importedAt: Long = System.currentTimeMillis()
)
