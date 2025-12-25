package com.tripath

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for TriPath.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class TriPathApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization can be done here
    }
}

