package com.tripath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.ui.theme.TriPathTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity that displays the privacy policy for Health Connect integration.
 * This is required by Health Connect to explain how health data is used.
 */
@AndroidEntryPoint
class HealthConnectPrivacyPolicyActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrivacyPolicyContent()
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.health_connect_rationale_title),
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.health_connect_rationale_message),
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Data Usage",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = """
                TriPath reads the following data from Health Connect:
                
                • Exercise Sessions - To track your completed workouts (runs, bike rides, swims, and strength training)
                • Heart Rate - To calculate training intensity and stress
                • Calories Burned - To track energy expenditure
                • Distance - For run and bike workout tracking
                • Steps - For activity monitoring
                
                All data is stored locally on your device. TriPath does not have a backend server and does not transmit your health data anywhere.
                
                Your data is used solely to:
                1. Display your completed workouts
                2. Calculate Training Stress Score (TSS)
                3. Help you track progress toward your Ironman goal
                
                You can export or delete your data at any time using the Backup feature in the app settings.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

