/*
 * This file contains the UI for the App Components list screen.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/AppComponentsScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.Screen
import com.mhk.deviceinspector.data.AppComponentInfo
import com.mhk.deviceinspector.ui.components.GenericScreen
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun AppComponentsScreen(
    apps: List<AppComponentInfo>?,
    navController: NavController
) {
    var searchQuery by remember { mutableStateOf("") }

    GenericScreen("App Components", navController = navController) {
        when (apps) {
            null -> {
                CircularProgressIndicator()
                Text("Loading application list...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                // Filter the list based on the search query
                val filteredApps = remember(searchQuery, apps) {
                    if (searchQuery.isBlank()) {
                        apps
                    } else {
                        apps.filter {
                            it.appName.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search by name or package") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredApps.isEmpty()) {
                    Text("No applications found.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredApps) { app ->
                            AppInfoCard(app = app) {
                                navController.navigate("${Screen.AppComponentsDetail.route}/${app.packageName}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoCard(app: AppComponentInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = app.icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

internal fun getInstalledApps(pm: PackageManager): List<AppComponentInfo> {
    return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        .map { appInfo ->
            AppComponentInfo(
                appName = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(pm)
            )
        }
        .sortedBy { it.appName.lowercase() }
}
