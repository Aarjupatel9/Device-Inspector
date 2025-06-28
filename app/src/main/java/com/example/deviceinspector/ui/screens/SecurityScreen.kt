/*
 * This file contains the UI and logic for the Security screen.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/SecurityScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.deviceinspector.data.HiddenAppInfo
import com.example.deviceinspector.ui.components.GenericScreen
import com.example.deviceinspector.ui.components.InfoRow
import com.example.deviceinspector.util.formatTimestamp
import com.example.deviceinspector.util.getInstallerName
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import android.app.usage.UsageStatsManager

@Composable
fun SecurityScreen() {
    val context = LocalContext.current
    val hiddenAppsState = produceState<List<HiddenAppInfo>?>(initialValue = null) {
        value = findHiddenApps(context)
    }

    GenericScreen("Hidden Apps Detector") {
        when (val apps = hiddenAppsState.value) {
            null -> { // Loading state
                CircularProgressIndicator()
                Text("Scanning for hidden apps...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                if (apps.isEmpty()) {
                    Text("No hidden applications found. All installed apps have a launcher icon.")
                } else {
                    Text(
                        "Found ${apps.size} app(s) without a launcher icon:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(apps) { app ->
                            HiddenAppCard(app = app) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.fromParts("package", app.packageName, null)
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenAppCard(app: HiddenAppInfo, onDetailsClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.icon),
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("Last Used:", formatTimestamp(app.lastTimeUsed))
            InfoRow("Installer:", getInstallerName(app.installerPackage))
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDetailsClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Details / Uninstall")
            }
        }
    }
}


/**
 * Finds installed applications that do not have a main launcher activity.
 * @return A list of `HiddenAppInfo` objects with detailed information.
 */
private fun findHiddenApps(context: Context): List<HiddenAppInfo> {
    val pm: PackageManager = context.packageManager
    val allApps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val hiddenApps = mutableListOf<HiddenAppInfo>()

    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val twoYearsInMillis = 2L * 365 * 24 * 60 * 60 * 1000
    val startTime = System.currentTimeMillis() - twoYearsInMillis
    val usageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, startTime, System.currentTimeMillis())
    val usageStatsMap = usageStats.associateBy({ it.packageName }, { it.lastTimeUsed })

    for (appInfo in allApps) {
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            val launchIntent: Intent? = pm.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent == null) {
                val appName = appInfo.loadLabel(pm).toString()
                val packageName = appInfo.packageName
                val icon = appInfo.loadIcon(pm)
                val lastTimeUsed = usageStatsMap[packageName] ?: 0L
                val installerPackage = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageName).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageName)
                    }
                } catch (e: Exception) { null }

                hiddenApps.add(HiddenAppInfo(appName, packageName, installerPackage, lastTimeUsed, icon))
            }
        }
    }
    return hiddenApps.sortedByDescending { it.lastTimeUsed }
}
