package com.tripath.data.local.backup

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains the gzipped JSON snapshot that Android Auto Backup uploads to the user's Google
 * account ("sikkerhedskopi").
 *
 * ## Why a snapshot file and not the database
 * The Room database is deliberately excluded from cloud backup in `res/xml/data_extraction_rules.xml`.
 * Backing up a JSON snapshot instead means:
 * - **Quota headroom.** Auto Backup gives an app ~25 MB, and exceeding it makes the platform drop
 *   the app's backup entirely, without telling the user. Heart-rate, power and route sample blobs
 *   compress roughly 10:1 as gzipped JSON, whereas the raw SQLite file also carries indices and
 *   free pages.
 * - **Version tolerance.** A restored snapshot is replayed through [BackupManager]'s versioned
 *   importer, so a backup taken on an older build restores cleanly. Dropping in a foreign SQLite
 *   file would instead depend on Room migrations lining up exactly.
 * - **Consistency.** The file is written from a single transaction and swapped into place
 *   atomically, so the platform can never copy a half-written snapshot.
 */
@Singleton
class CloudSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serialises concurrent refreshes, e.g. app start racing a post-sync refresh. */
    private val writeMutex = Mutex()

    /**
     * Singleton-lifetime scope for fire-and-forget refreshes. A snapshot triggered as the user
     * leaves a screen must not be cancelled along with that screen's lifecycle.
     */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _meta = MutableStateFlow(readMeta())

    /** Metadata for the snapshot currently on disk, or null if there is none. */
    val meta: StateFlow<SnapshotMeta?> = _meta.asStateFlow()

    private val snapshotDir: File
        get() = File(context.filesDir, SNAPSHOT_DIR)

    private val snapshotFile: File
        get() = File(snapshotDir, SNAPSHOT_FILE)

    private val metaFile: File
        get() = File(snapshotDir, META_FILE)

    /** Whether a restorable snapshot exists on disk. */
    fun hasSnapshot(): Boolean = snapshotFile.exists() && snapshotFile.length() > 0

    /**
     * Write a fresh snapshot of all app data.
     *
     * The payload is written to a temporary file and then renamed over the live one, so a crash
     * or a backup pass mid-write leaves the previous good snapshot intact.
     */
    suspend fun writeSnapshot(): Result<SnapshotMeta> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                snapshotDir.mkdirs()

                val versionCode = appVersionCode()
                val backupData = backupManager.exportToBackupData(
                    appVersionCode = versionCode,
                    appVersionName = appVersionName()
                )
                val payload = json.encodeToString(AppBackupData.serializer(), backupData)

                val tempFile = File(snapshotDir, "$SNAPSHOT_FILE.tmp")
                GZIPOutputStream(tempFile.outputStream().buffered()).use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
                if (!tempFile.renameTo(snapshotFile)) {
                    // renameTo fails on some devices when the destination exists.
                    snapshotFile.delete()
                    if (!tempFile.renameTo(snapshotFile)) {
                        tempFile.delete()
                        return@withLock Result.failure(
                            java.io.IOException("Could not move snapshot into place")
                        )
                    }
                }

                val meta = SnapshotMeta(
                    timestampMillis = backupData.timestamp,
                    appVersionCode = versionCode,
                    appVersionName = appVersionName(),
                    backupFormatVersion = backupData.version,
                    compressedBytes = snapshotFile.length(),
                    uncompressedBytes = payload.length.toLong(),
                    counts = backupData.recordCounts()
                )
                metaFile.writeText(json.encodeToString(SnapshotMeta.serializer(), meta))
                _meta.value = meta

                requestBackupPass()

                if (meta.compressedBytes > QUOTA_WARNING_BYTES) {
                    Log.w(
                        TAG,
                        "Snapshot is ${meta.compressedBytes / 1_048_576} MB, approaching the " +
                            "Auto Backup quota. Beyond it the platform silently skips this app."
                    )
                }

                Result.success(meta)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write cloud snapshot", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Write a snapshot only if the existing one is older than [maxAgeMillis].
     *
     * Auto Backup itself runs roughly once a day, so refreshing more eagerly than that would
     * spend CPU and battery re-serialising data the platform hasn't collected yet.
     */
    suspend fun refreshIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): Result<SnapshotMeta>? {
        val current = _meta.value
        val age = current?.let { System.currentTimeMillis() - it.timestampMillis }
        return if (current == null || age == null || age >= maxAgeMillis) {
            writeSnapshot()
        } else {
            null
        }
    }

    /**
     * Fire-and-forget refresh, for lifecycle callbacks that can't await a result.
     *
     * Called when the app goes to the background, which is what keeps the backup close to the
     * user's actual data: without it, everything logged since the last refresh — up to a whole
     * day — would be missing from a restore, and the user would have no way to know.
     */
    fun refreshInBackground(maxAgeMillis: Long = BACKGROUND_MAX_AGE_MILLIS) {
        storeScope.launch { refreshIfStale(maxAgeMillis) }
    }

    /**
     * Read and inflate the snapshot JSON, or null if there is no snapshot or it can't be read.
     */
    suspend fun readSnapshotJson(): String? = withContext(Dispatchers.IO) {
        if (!hasSnapshot()) return@withContext null
        try {
            GZIPInputStream(snapshotFile.inputStream().buffered()).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cloud snapshot", e)
            null
        }
    }

    /**
     * Restore the snapshot currently on disk.
     */
    suspend fun restoreFromSnapshot(mode: ImportMode = ImportMode.MERGE): Result<ImportSummary> {
        val payload = readSnapshotJson()
            ?: return Result.failure(IllegalStateException("No backup found on this device"))
        return backupManager.importFromJson(payload, mode)
    }

    /**
     * Read the sidecar metadata written alongside the snapshot.
     *
     * Kept as uncompressed JSON so the UI and the restore prompt can describe a backup — its
     * date and record counts — without inflating the whole payload.
     */
    fun readMeta(): SnapshotMeta? {
        val file = metaFile
        if (!file.exists()) return null
        return try {
            json.decodeFromString(SnapshotMeta.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable snapshot metadata; ignoring", e)
            null
        }
    }

    /** Re-read metadata from disk, e.g. after the platform restored a snapshot into place. */
    fun reloadMeta(): SnapshotMeta? = readMeta().also { _meta.value = it }

    /**
     * Tell the backup framework there is new data, so it can schedule a pass at the next
     * opportunity (typically idle + charging + unmetered network).
     */
    private fun requestBackupPass() {
        try {
            android.app.backup.BackupManager(context).dataChanged()
        } catch (e: Exception) {
            // Backup may be disabled or unavailable; the snapshot on disk is still valid.
            Log.w(TAG, "Could not request a backup pass", e)
        }
    }

    private fun appVersionCode(): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (e: PackageManager.NameNotFoundException) {
        0L
    }

    private fun appVersionName(): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        private const val TAG = "CloudSnapshotStore"

        /**
         * Must match the `<include domain="file" path="cloud_backup/" />` entry in
         * `res/xml/data_extraction_rules.xml` — renaming this directory without updating that
         * file would silently stop the snapshot being backed up.
         */
        const val SNAPSHOT_DIR = "cloud_backup"
        const val SNAPSHOT_FILE = "snapshot.json.gz"
        const val META_FILE = "snapshot_meta.json"

        /** Auto Backup allows roughly 25 MB per app; warn well before that. */
        private const val QUOTA_WARNING_BYTES = 20L * 1024 * 1024

        private const val DEFAULT_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Threshold used when the app goes to the background. Short, because this is the window
         * of work a restore would silently lose; long enough that flicking in and out of the app
         * doesn't re-serialise the database each time.
         */
        private const val BACKGROUND_MAX_AGE_MILLIS = 10L * 60 * 1000
    }
}

/**
 * Describes the snapshot on disk without needing to inflate it.
 */
@Serializable
data class SnapshotMeta(
    val timestampMillis: Long,
    val appVersionCode: Long,
    val appVersionName: String? = null,
    val backupFormatVersion: Int,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val counts: Map<String, Int> = emptyMap()
) {
    /** Total records described by [counts]. */
    val totalRecords: Int get() = counts.values.sum()
}

/**
 * Per-table record counts, used for the snapshot metadata and the restore prompt.
 */
fun AppBackupData.recordCounts(): Map<String, Int> = mapOf(
    "trainingPlans" to trainingPlans.size,
    "workoutLogs" to workoutLogs.size,
    "rawWorkoutData" to rawWorkoutData.size,
    "sleepLogs" to sleepLogs.size,
    "specialPeriods" to specialPeriods.size,
    "dayNotes" to dayNotes.size,
    "dayTemplates" to dayTemplates.size,
    "wellnessLogs" to wellnessLogs.size,
    "wellnessTasks" to wellnessTasks.size,
    "bodyCompositionLogs" to bodyCompositionLogs.size,
    "nutritionLogs" to nutritionLogs.size,
    "nutritionEntries" to nutritionEntries.size,
    "preferences" to preferences.size
)
