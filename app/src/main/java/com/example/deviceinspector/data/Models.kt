/*
 * This file contains the data models for the application.
 * Location: app/src/main/java/com/example/deviceinspector/data/Models.kt
 */
package com.example.deviceinspector.data

import android.graphics.drawable.Drawable

// Data class to hold detailed information about a hidden app
data class HiddenAppInfo(
    val appName: String,
    val packageName: String,
    val installerPackage: String?,
    val lastTimeUsed: Long,
    val icon: Drawable?
)

// Data class to hold information for the app usage screen
data class AppUsageInfo(
    val appName: String,
    val packageName: String,
    val totalTimeInForeground: Long,
    val icon: Drawable?,
    // New: A map of daily usage (Timestamp of day start -> Milliseconds of usage)
    val dailyUsage: Map<Long, Long>
)

// Data class to hold information for a single app launch event
data class AppEventInfo(
    val appName: String,
    val packageName: String,
    val eventTime: Long,
    val icon: Drawable?,
    // New: The specific activity that was launched
    val activityName: String?
)
