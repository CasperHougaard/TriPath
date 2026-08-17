package com.tripath.data.local.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects that Android restored a cloud backup onto this device, and offers it to the user.
 *
 * ## How detection works
 * There is no public API that tells an app "your data was just restored". But the platform
 * restores `filesDir` while `noBackupFilesDir` is, by definition, never part of a backup. That
 * asymmetry is the signal:
 *
 * - A normal launch has the install marker in `noBackupFilesDir`.
 * - A fresh install after a restore has **no** marker but **does** have a snapshot in `filesDir`.
 *
 * ## Why the user is asked instead of restoring automatically
 * A silent restore would overwrite data if the user had already started logging on the new phone,
 * and a restore that happens invisibly is impossible to tell apart from data loss. So the app
 * prompts once, and keeps the snapshot afterwards so it stays restorable from the My Data screen.
 */
@Singleton
class RestoreCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotStore: CloudSnapshotStore
) {
    private val _pendingRestore = MutableStateFlow<SnapshotMeta?>(null)

    /**
     * Metadata of a restorable backup that the user has not yet been asked about,
     * or null when there is nothing to offer.
     */
    val pendingRestore: StateFlow<SnapshotMeta?> = _pendingRestore.asStateFlow()

    private val installMarker: File
        get() = File(context.noBackupFilesDir, INSTALL_MARKER_FILE)

    /**
     * Work out whether this launch follows a cloud restore. Safe to call on every app start.
     */
    suspend fun checkForRestoredBackup() = withContext(Dispatchers.IO) {
        val isFreshInstall = !installMarker.exists()
        if (!isFreshInstall) return@withContext

        // Metadata may have been restored underneath us since the store was constructed.
        val meta = snapshotStore.reloadMeta()
        val restorable = meta != null && snapshotStore.hasSnapshot()

        if (restorable) {
            Log.i(
                TAG,
                "Fresh install with a restored snapshot from ${meta?.timestampMillis}; " +
                    "offering restore"
            )
            _pendingRestore.value = meta
        }

        // Written whether or not a snapshot was found, so the check runs exactly once per install.
        markInstallSeen()
    }

    /**
     * Restore the snapshot the user was offered.
     *
     * Uses [ImportMode.MERGE]: on a fresh install there is nothing to merge with, and if the user
     * did log something before accepting, merging keeps it rather than deleting it.
     */
    suspend fun acceptPendingRestore(): Result<ImportSummary> {
        val result = snapshotStore.restoreFromSnapshot(ImportMode.MERGE)
        if (result.isSuccess) {
            _pendingRestore.value = null
        }
        return result
    }

    /**
     * Dismiss the prompt. The snapshot stays on disk and can still be restored later from
     * the My Data screen.
     */
    fun declinePendingRestore() {
        _pendingRestore.value = null
    }

    private fun markInstallSeen() {
        try {
            context.noBackupFilesDir.mkdirs()
            installMarker.writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            // If this fails the prompt may reappear next launch, which is recoverable;
            // failing the launch would not be.
            Log.w(TAG, "Could not write install marker", e)
        }
    }

    companion object {
        private const val TAG = "RestoreCoordinator"

        /**
         * Lives in `noBackupFilesDir`, which Android never includes in a backup or restore.
         * Its absence is what identifies a fresh install.
         */
        private const val INSTALL_MARKER_FILE = "install.marker"
    }
}
