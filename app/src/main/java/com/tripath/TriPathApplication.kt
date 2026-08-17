package com.tripath

import android.app.Application
import com.tripath.data.local.backup.CloudSnapshotStore
import com.tripath.data.local.backup.RestoreCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class for TriPath.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class TriPathApplication : Application() {

    @Inject
    lateinit var cloudSnapshotStore: CloudSnapshotStore

    @Inject
    lateinit var restoreCoordinator: RestoreCoordinator

    /** Outlives any screen: backup upkeep must not be cancelled by navigation. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            restoreCoordinator.checkForRestoredBackup()

            // Order matters. A snapshot restored from the cloud is by definition older than the
            // refresh threshold, so refreshing first would overwrite the user's entire history
            // with a snapshot of an empty database before they were ever asked about it. Refresh
            // only once no restore is awaiting an answer.
            if (restoreCoordinator.pendingRestore.value == null) {
                // Keeps the file that Auto Backup uploads reasonably current. Auto Backup itself
                // runs about once a day, so a daily refresh is as fine-grained as is useful.
                cloudSnapshotStore.refreshIfStale()
            }
        }
    }
}
