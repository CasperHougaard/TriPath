package com.tripath

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.view.WindowCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.tripath.data.local.backup.CloudSnapshotStore
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.ui.MainScreen
import com.tripath.ui.theme.TriPathAppTheme
import com.tripath.widget.refreshNutritionWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main entry point Activity for the TriPath app.
 * Uses Jetpack Compose for UI rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var healthConnectManager: HealthConnectManager
    
    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var cloudSnapshotStore: CloudSnapshotStore

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions: Set<String> ->
        onPermissionsResult(grantedPermissions)
    }

    /**
     * A pending in-app destination requested by an external entry point (e.g. the home-screen
     * widget). MainScreen observes this and navigates once, then clears it.
     */
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Honor a destination requested by the launching intent (e.g. tapping the widget).
        pendingDestination.value = intent?.getStringExtra(EXTRA_OPEN_DESTINATION)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Check Health Connect permissions on startup
        checkHealthConnectPermissions()

        setContent {
            val destination by pendingDestination.collectAsState()

            // Appearance and system-bar contrast both live in TriPathAppTheme.
            TriPathAppTheme(preferencesManager) {
                MainScreen(
                    pendingDestination = destination,
                    onDestinationHandled = { pendingDestination.value = null }
                )
            }
        }
    }

    /** Called when the widget (or another intent) targets an already-running instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_DESTINATION)?.let { pendingDestination.value = it }
    }

    /**
     * Refresh the nutrition widget whenever the app is opened or closed. Nutrition writes already
     * refresh it directly, but this catches state that affects the widget without going through
     * that path (e.g. changing the protein/calorie target in Settings), so the widget never shows
     * numbers that are out of sync with what's in the app.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { refreshNutritionWidget(applicationContext) }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { refreshNutritionWidget(applicationContext) }

        // Leaving the app is the natural point to capture what was just logged. Without this the
        // cloud snapshot could trail the database by up to a day, and a restore would quietly
        // come back missing that day's entries.
        cloudSnapshotStore.refreshInBackground()
    }

    /**
     * Check if Health Connect permissions are granted and request them if not.
     */
    private fun checkHealthConnectPermissions() {
        lifecycleScope.launch {
            try {
                // Check if Health Connect is available first
                if (!healthConnectManager.isAvailable()) {
                    // Health Connect not available, skip permission check
                    return@launch
                }
                
                if (!healthConnectManager.hasAllPermissions()) {
                    // Request permissions
                    val permissionsToRequest = healthConnectManager.getPermissionsToRequest()
                    permissionLauncher.launch(permissionsToRequest)
                }
            } catch (e: Exception) {
                // Handle any exceptions gracefully - don't crash the app
                // Health Connect might not be available or there might be other issues
            }
        }
    }

    /**
     * Handle the result of permission request.
     */
    private fun onPermissionsResult(grantedPermissions: Set<String>) {
        lifecycleScope.launch {
            // Permissions have been updated, UI can react to the new state
            // The DashboardViewModel will check permissions and sync accordingly
        }
    }

    companion object {
        /** Intent extra naming an in-app destination to open on launch. */
        const val EXTRA_OPEN_DESTINATION = "com.tripath.OPEN_DESTINATION"

        /** Value for [EXTRA_OPEN_DESTINATION]: open the nutrition detail page. */
        const val DEST_NUTRITION = "nutrition"
    }
}


