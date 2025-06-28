/*
 * This file contains utility functions used across the app.
 * Location: app/src/main/java/com/example/deviceinspector/util/Helpers.kt
 */
package com.example.deviceinspector.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Formats a Unix timestamp into a readable date string.
 */
fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Formats milliseconds into a readable "Xh Ym Zs" string.
 */
fun formatUsageTime(millis: Long): String {
    if (millis <= 0) return "0s"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    val builder = StringBuilder()
    if (hours > 0) {
        builder.append(hours).append("h ")
    }
    if (minutes > 0) {
        builder.append(minutes).append("m ")
    }
    if (seconds > 0 || (hours == 0L && minutes == 0L)) {
        builder.append(seconds).append("s")
    }
    return builder.toString().trim()
}


/**
 * Provides a user-friendly name for the installer package.
 */
fun getInstallerName(installerPackage: String?): String {
    return when (installerPackage) {
        null -> "Unknown / Sideloaded"
        "com.android.vending" -> "Google Play Store"
        "com.amazon.venezia" -> "Amazon Appstore"
        else -> installerPackage
    }
}
