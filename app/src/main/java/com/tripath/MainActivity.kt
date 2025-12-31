package com.tripath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.tripath.data.local.healthconnect.HealthConnectManager
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.ui.MainScreen
import com.tripath.ui.theme.TriPathTheme
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

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions: Set<String> ->
        onPermissionsResult(grantedPermissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set navigation bar to white for visibility in dark theme
        val window = window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Check Health Connect permissions on startup
        checkHealthConnectPermissions()
        
        setContent {
            // Observe dark theme preference
            val isDarkTheme by preferencesManager.darkThemeFlow.collectAsState(initial = true)
            val view = LocalView.current
            
            // Configure Android system status bar and navigation bar icons based on theme
            DisposableEffect(isDarkTheme) {
                val windowInsetsController = WindowCompat.getInsetsController(window, view)
                
                if (isDarkTheme) {
                    // Dark theme: dark bars with bright icons
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    windowInsetsController.isAppearanceLightNavigationBars = false
                    windowInsetsController.isAppearanceLightStatusBars = false
                } else {
                    // Light theme: light bars with dark icons
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.WHITE
                    windowInsetsController.isAppearanceLightNavigationBars = true
                    windowInsetsController.isAppearanceLightStatusBars = true
                }
                
                onDispose { }
            }
            
            TriPathTheme(darkTheme = isDarkTheme) {
                MainScreen()
            }
        }
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
}


