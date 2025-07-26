/*
 * Helper class for handling storage permissions across different Android versions
 * Location: app/src/main/java/com/mhk/deviceinspector/util/StoragePermissionHelper.kt
 */
package com.mhk.deviceinspector.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import java.io.File

object StoragePermissionHelper {

    /**
     * Check if we have the necessary storage permissions
     */
    fun hasStoragePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - Check if we have MANAGE_EXTERNAL_STORAGE
                Environment.isExternalStorageManager()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6-10 - Check WRITE_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // Below Android 6 - Permission granted at install time
                true
            }
        }
    }

    /**
     * Request storage permission based on Android version
     */
    fun requestStoragePermission(
        context: Context,
        launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
    ) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - Request MANAGE_EXTERNAL_STORAGE
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                launcher.launch(intent)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6-10 - This should be handled by requesting WRITE_EXTERNAL_STORAGE
                // But since we're using Compose, we'll redirect to app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                launcher.launch(intent)
            }
        }
    }

    /**
     * Create the export directory in Downloads/DeviceInspector/NetworkSessions
     */
    fun createExportDirectory(): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "DeviceInspector/NetworkSessions")

            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            if (exportDir.exists() && exportDir.canWrite()) {
                exportDir
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get a user-friendly path string for display
     */
    fun getExportPathForDisplay(): String {
        return "Downloads/DeviceInspector/NetworkSessions/"
    }
}