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
    val dailyUsage: Map<Long, Long>,
    val hourlyUsageEvents: List<Pair<Long, Long>>
)

// Data class to hold information for a single app launch event
data class AppEventInfo(
    val appName: String,
    val packageName: String,

    val eventTime: Long,
    val icon: Drawable?,
    val activityName: String?
)

// --- New Data Classes for Detailed Device Info ---

data class DeviceInfo(
    val hardware: HardwareInfo,
    val software: SoftwareInfo,
    val memory: MemoryInfo,
    val display: DisplayInfo
)

data class HardwareInfo(
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val product: String,
    val hardware: String,
    val cpuAbi: String
)

data class SoftwareInfo(
    val androidVersion: String,
    val apiLevel: String,
    val securityPatch: String,
    val buildId: String,
    val kernelVersion: String
)

data class MemoryInfo(
    val totalRam: String,
    val availableRam: String,
    val totalInternalStorage: String,
    val availableInternalStorage: String
)

data class DisplayInfo(
    val resolution: String,
    val density: String,
    val refreshRate: String
)
