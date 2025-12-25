package com.tripath.domain

/**
 * Singleton object for centralized training logic and calculations.
 * Encapsulates math and recovery logic to separate it from ViewModels/UI.
 */
object TrainingLogic {

    /**
     * Calculate Training Stress Score (TSS).
     * 
     * @param durationMinutes Duration of the workout in minutes.
     * @param heartRate Average heart rate during the workout.
     * @param ftp Functional Threshold Power (or Heart Rate equivalent).
     * @return Calculated TSS or null if inputs are insufficient.
     */
    fun calculateTSS(durationMinutes: Int, heartRate: Int?, ftp: Int?): Int? {
        if (heartRate == null || ftp == null) {
            return null
        }
        // Placeholder logic: 
        // Real TSS formula: (sec x NP x IF) / (FTP x 3600) x 100
        // Simplified HR-based estimate for now:
        // (duration / 60) * (HR / LTHR)^2 * 100
        // We'll just return a dummy calculation for now.
        return ((durationMinutes / 60.0) * 50).toInt() 
    }

    /**
     * Determine if recovery is needed based on recent training stress.
     * 
     * @param recentTSS The sum or average TSS over a recent period (e.g., last 7 days).
     * @return True if recovery is advised, false otherwise.
     */
    fun isRecoveryNeeded(recentTSS: Int): Boolean {
        // Placeholder logic:
        // If 7-day TSS > 400 (arbitrary threshold), suggest recovery.
        return recentTSS > 400
    }
}

