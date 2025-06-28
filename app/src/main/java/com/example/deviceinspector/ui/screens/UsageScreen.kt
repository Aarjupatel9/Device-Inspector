/*
 * This file contains the UI and logic for the App Usage screen.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/UsageScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.deviceinspector.data.AppUsageInfo
import com.example.deviceinspector.ui.components.GenericScreen
import com.example.deviceinspector.util.formatUsageTime
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun UsageScreen() {
    val context = LocalContext.current
    val appUsageState = produceState<List<AppUsageInfo>?>(initialValue = null) {
        value = getAppUsageStats(context)
    }

    GenericScreen("App Usage (Last 24h)") {
        when (val apps = appUsageState.value) {
            null -> { // Loading state
                CircularProgressIndicator()
                Text("Calculating usage...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                if (apps.isEmpty()) {
                    Text("No application usage data found for the last 24 hours.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(apps) { app ->
                            AppUsageCard(app = app)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageCard(app: AppUsageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = app.icon),
                contentDescription = "${app.appName} icon",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = app.appName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatUsageTime(app.totalTimeInForeground),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


/**
 * Queries the UsageStatsManager to get app usage for the last 24 hours.
 */
private fun getAppUsageStats(context: Context): List<AppUsageInfo> {
    val pm: PackageManager = context.packageManager
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val startTime = endTime - (24 * 60 * 60 * 1000) // 24 hours ago

    val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
    val appUsageList = mutableListOf<AppUsageInfo>()

    for (usageStats in usageStatsList) {
        if (usageStats.totalTimeInForeground > 0) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(usageStats.packageName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(usageStats.packageName, 0)
                }

                // Only show non-system apps
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    appUsageList.add(
                        AppUsageInfo(
                            appName = appInfo.loadLabel(pm).toString(),
                            packageName = usageStats.packageName,
                            totalTimeInForeground = usageStats.totalTimeInForeground,
                            icon = appInfo.loadIcon(pm)
                        )
                    )
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App might have been uninstalled, ignore it
            }
        }
    }

    // Sort by most used app
    return appUsageList.sortedByDescending { it.totalTimeInForeground }
}
