/*
 * This file contains the UI and logic for the Usage History screen.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/HistoryScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.deviceinspector.data.AppEventInfo
import com.example.deviceinspector.ui.components.GenericScreen
import com.example.deviceinspector.ui.components.InfoRow
import com.example.deviceinspector.util.formatTimestamp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val historyState = produceState<List<AppEventInfo>?>(initialValue = null) {
        value = getAppLaunchHistory(context)
    }

    GenericScreen("Launch History (Last 48h)") {
        when (val events = historyState.value) {
            null -> { // Loading state
                CircularProgressIndicator()
                Text("Loading launch history...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                if (events.isEmpty()) {
                    Text("No app launch events found in the last 48 hours.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(events) { event ->
                            AppEventCard(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppEventCard(event: AppEventInfo) {
    // State to control the expanded view
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize() // Smoothly animate size changes
            .clickable { isExpanded = !isExpanded }, // Make the whole card clickable
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberDrawablePainter(drawable = event.icon),
                    contentDescription = "${event.appName} icon",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatTimestamp(event.eventTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Show an up/down arrow icon to indicate expanded state
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle details"
                )
            }

            // The expanded content, shown only when isExpanded is true
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(modifier = Modifier.padding(bottom = 12.dp))
                    InfoRow("Package:", event.packageName)
                    InfoRow("Activity:", event.activityName?.substringAfterLast('.') ?: "N/A")
                }
            }
        }
    }
}

/**
 * Queries the UsageStatsManager for app launch events in the last 48 hours.
 */
private fun getAppLaunchHistory(context: Context): List<AppEventInfo> {
    val pm: PackageManager = context.packageManager
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val startTime = endTime - (48 * 60 * 60 * 1000) // 48 hours ago

    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    val appEventList = mutableListOf<AppEventInfo>()
    val event = UsageEvents.Event()

    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)

        // ACTIVITY_RESUMED is the event for an app's activity moving to the foreground.
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
            try {
                val appInfo = pm.getApplicationInfo(event.packageName, 0)
                // Filter out system and launcher apps for a cleaner history
                if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 && event.packageName != context.packageName && !event.packageName.contains("launcher")) {
                    appEventList.add(
                        AppEventInfo(
                            appName = appInfo.loadLabel(pm).toString(),
                            packageName = event.packageName,
                            eventTime = event.timeStamp,
                            icon = appInfo.loadIcon(pm),
                            // Capture the class/activity name from the event
                            activityName = event.className
                        )
                    )
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App might have been uninstalled, ignore it.
            }
        }
    }

    // Return the list sorted with the most recent event first.
    return appEventList.sortedByDescending { it.eventTime }
}
