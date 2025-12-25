package com.tripath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tripath.ui.MainScreen
import com.tripath.ui.theme.TriPathTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point Activity for the TriPath app.
 * Uses Jetpack Compose for UI rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriPathTheme {
                MainScreen()
            }
        }
    }
}


