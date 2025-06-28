/*
 * This file contains the UI and logic for the Device Info screen.
 * It now receives its data as a parameter to prevent re-fetching on tab switch.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/DeviceInfoScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.deviceinspector.data.*
import com.example.deviceinspector.ui.components.GenericScreen
import java.text.DecimalFormat

@Composable
fun DeviceInfoScreen(deviceInfo: DeviceInfo?) {
    GenericScreen("Device Information") {
        when (deviceInfo) {
            null -> {
                // Show a loading indicator while the data is being fetched.
                CircularProgressIndicator()
                Text("Loading device info...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                // Once data is loaded, display it.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        InfoCategoryCard(
                            title = "Hardware",
                            icon = Icons.Default.Memory,
                            info = mapOf(
                                "Model" to deviceInfo.hardware.model,
                                "Manufacturer" to deviceInfo.hardware.manufacturer,
                                "Brand" to deviceInfo.hardware.brand,
                                "Device" to deviceInfo.hardware.device,
                                "Product" to deviceInfo.hardware.product,
                                "Hardware" to deviceInfo.hardware.hardware,
                                "CPU ABI" to deviceInfo.hardware.cpuAbi
                            )
                        )
                    }
                    item {
                        InfoCategoryCard(
                            title = "Software",
                            icon = Icons.Default.Android,
                            info = mapOf(
                                "Android Version" to deviceInfo.software.androidVersion,
                                "API Level" to deviceInfo.software.apiLevel,
                                "Security Patch" to deviceInfo.software.securityPatch,
                                "Build ID" to deviceInfo.software.buildId,
                                "Kernel Version" to deviceInfo.software.kernelVersion
                            )
                        )
                    }
                    item {
                        InfoCategoryCard(
                            title = "Memory & Storage",
                            icon = Icons.Default.Storage,
                            info = mapOf(
                                "Total RAM" to deviceInfo.memory.totalRam,
                                "Available RAM" to deviceInfo.memory.availableRam,
                                "Total Internal" to deviceInfo.memory.totalInternalStorage,
                                "Available Internal" to deviceInfo.memory.availableInternalStorage
                            )
                        )
                    }
                    item {
                        InfoCategoryCard(
                            title = "Display",
                            icon = Icons.Default.StayCurrentPortrait,
                            info = mapOf(
                                "Resolution" to deviceInfo.display.resolution,
                                "Density" to deviceInfo.display.density,
                                "Refresh Rate" to deviceInfo.display.refreshRate
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCategoryCard(title: String, icon: ImageVector, info: Map<String, String>) {
    var isExpanded by remember { mutableStateOf(true) } // Default to expanded

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle details"
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    info.forEach { (key, value) ->
                        InfoRow(label = key, value = value)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


internal fun getDetailedDeviceInfo(context: Context): DeviceInfo {
    // Hardware Info
    val hardwareInfo = HardwareInfo(
        model = Build.MODEL,
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        hardware = Build.HARDWARE,
        cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "N/A"
    )

    // Software Info
    val softwareInfo = SoftwareInfo(
        androidVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT.toString(),
        securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
        buildId = Build.DISPLAY,
        kernelVersion = System.getProperty("os.version") ?: "N/A"
    )

    // Memory Info
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalRam = formatBytes(memoryInfo.totalMem)
    val availableRam = formatBytes(memoryInfo.availMem)

    val internalStatFs = StatFs(Environment.getDataDirectory().path)
    val totalInternal = formatBytes(internalStatFs.totalBytes)
    val availableInternal = formatBytes(internalStatFs.availableBytes)

    val memory = MemoryInfo(
        totalRam = totalRam,
        availableRam = "$availableRam available",
        totalInternalStorage = totalInternal,
        availableInternalStorage = "$availableInternal free"
    )

    // Display Info
    val displayMetrics = context.resources.displayMetrics
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    }

    val resolution = "${displayMetrics.heightPixels} x ${displayMetrics.widthPixels} pixels"
    val density = "${displayMetrics.densityDpi} dpi"
    val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        "${"%.2f".format(display?.refreshRate)} Hz"
    } else {
        "${display?.refreshRate} Hz"
    }

    val displayInfo = DisplayInfo(
        resolution = resolution,
        density = density,
        refreshRate = refreshRate
    )

    return DeviceInfo(
        hardware = hardwareInfo,
        software = softwareInfo,
        memory = memory,
        display = displayInfo
    )
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val df = DecimalFormat("#.##")
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${df.format(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${df.format(mb)} MB"
    val gb = mb / 1024.0
    return "${df.format(gb)} GB"
}
