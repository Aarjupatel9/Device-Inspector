/*
 * This file contains the UI and logic for the App Usage screen.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/UsageScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinspector.data.AppUsageInfo
import com.example.deviceinspector.ui.components.GenericScreen
import com.example.deviceinspector.util.formatUsageTime
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@Composable
fun UsageScreen() {
    val context = LocalContext.current
    val appUsageState = produceState<List<AppUsageInfo>?>(initialValue = null) {
        value = getAppUsageStats(context)
    }

    GenericScreen("App Usage (Last 7 Days)") {
        when (val apps = appUsageState.value) {
            null -> { // Loading state
                CircularProgressIndicator()
                Text("Calculating usage...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                if (apps.isEmpty()) {
                    Text("No application usage data found.")
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
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
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
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle details",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Daily Usage Chart:", style = MaterialTheme.typography.labelMedium)
                    DailyUsageBarChart(dailyUsage = app.dailyUsage)
                }
            }
        }
    }
}

@Composable
fun DailyUsageBarChart(dailyUsage: Map<Long, Long>) {
    val dayFormatter = SimpleDateFormat("E", Locale.getDefault())
    val maxUsage = max(1L, dailyUsage.values.maxOrNull() ?: 1L) // Avoid division by zero

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        dailyUsage.entries.sortedBy { it.key }.forEach { (timestamp, usage) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                val barHeight = (usage.toFloat() / maxUsage.toFloat() * 80).dp.coerceAtLeast(1.dp)
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dayFormatter.format(Date(timestamp)),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Queries the UsageStatsManager to get app usage for the last 7 days.
 */
private fun getAppUsageStats(context: Context): List<AppUsageInfo> {
    val pm: PackageManager = context.packageManager
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val usageStatsList: List<UsageStats> =
        usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

    if (usageStatsList.isNullOrEmpty()) {
        return emptyList()
    }

    val aggregatedStats = mutableMapOf<String, MutableList<UsageStats>>()
    // Group stats by package name
    for (stat in usageStatsList) {
        if (stat.totalTimeInForeground > 0) {
            aggregatedStats.getOrPut(stat.packageName) { mutableListOf() }.add(stat)
        }
    }

    val appUsageList = mutableListOf<AppUsageInfo>()
    for ((packageName, stats) in aggregatedStats) {
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }

            // Only show non-system apps
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val totalUsage = stats.sumOf { it.totalTimeInForeground }
                val dailyUsageMap = stats.associate {
                    // Use firstTimestamp to get the beginning of the stat's day interval
                    it.firstTimeStamp to it.totalTimeInForeground
                }

                appUsageList.add(
                    AppUsageInfo(
                        appName = appInfo.loadLabel(pm).toString(),
                        packageName = packageName,
                        totalTimeInForeground = totalUsage,
                        icon = appInfo.loadIcon(pm),
                        dailyUsage = dailyUsageMap
                    )
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // App might have been uninstalled, ignore it
        }
    }

    // Sort by most used app in the entire period
    return appUsageList.sortedByDescending { it.totalTimeInForeground }
}
