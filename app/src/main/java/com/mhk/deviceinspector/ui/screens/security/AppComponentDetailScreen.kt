/*
 * This file contains the UI for the App Component details screen.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/AppComponentDetailScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.data.AppDetailInfo
import com.mhk.deviceinspector.ui.components.GenericScreen
import com.mhk.deviceinspector.util.formatTimestamp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppComponentDetailScreen(
    packageName: String,
    navController: NavController
) {
    val context = LocalContext.current
    val appDetailsState = produceState<AppDetailInfo?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            getAppDetails(context, packageName)
        }
    }

    val appDetails = appDetailsState.value

    GenericScreen(
        title = appDetails?.appName ?: "Loading...",
        navController = navController
    ) {
        when (appDetails) {
            null -> {
                CircularProgressIndicator()
                Text("Loading component details...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                var selectedTabIndex by remember { mutableStateOf(0) }
                val tabs = listOf(
                    "Manifest",
                    "Activities (${appDetails.activities.size})",
                    "Services (${appDetails.services.size})",
                    "Receivers (${appDetails.receivers.size})",
                    "Providers (${appDetails.providers.size})"
                )

                Column {
                    ScrollableTabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    when (selectedTabIndex) {
                        0 -> ManifestInfoTab(appDetails)
                        1 -> ComponentListTab(components = appDetails.activities, icon = Icons.Default.Pages, title = "Activities")
                        2 -> ComponentListTab(components = appDetails.services, icon = Icons.Default.Settings, title = "Services")
                        3 -> ComponentListTab(components = appDetails.receivers, icon = Icons.Default.BroadcastOnPersonal, title = "Receivers")
                        4 -> ComponentListTab(components = appDetails.providers, icon = Icons.Default.Share, title = "Providers")
                    }
                }
            }
        }
    }
}

@Composable
fun ManifestInfoTab(details: AppDetailInfo) {
    // Add verticalArrangement to space out all items in the list consistently.
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Manifest Summary Section ---
        item {
            Text("Manifest Summary", style = MaterialTheme.typography.titleLarge)
        }
        items(details.manifestSummary.entries.toList()) { (key, value) ->
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = "$key:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(120.dp)
                )
                Text(text = value)
            }
        }

        // --- Application Flags Section ---
        if (details.applicationFlags.isNotEmpty()) {
            item {
                Text("Application Flags", style = MaterialTheme.typography.titleLarge)
            }
            items(details.applicationFlags) { flag ->
                ComponentItem(name = flag, icon = Icons.Default.Flag)
            }
        }

        // --- Permissions Section ---
        item {
            Text("Declared Permissions (${details.permissions.size})", style = MaterialTheme.typography.titleLarge)
        }
        items(details.permissions) { permission ->
            ComponentItem(name = permission.substringAfterLast('.'), icon = Icons.Default.VpnKey)
        }
    }
}

@Composable
fun ComponentListTab(components: List<String>, icon: ImageVector, title: String) {
    if (components.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No $title found in the manifest.")
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(components) { componentName ->
                ComponentItem(name = componentName, icon = icon)
            }
        }
    }
}

@Composable
fun ComponentItem(name: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

internal fun getAppDetails(context: Context, packageName: String): AppDetailInfo? {
    val pm = context.packageManager
    return try {
        val packageInfo = pm.getPackageInfo(
            packageName,
            PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_PERMISSIONS
        )
        val appInfo = packageInfo.applicationInfo

        val manifestSummary = mapOf(
            "Version Name" to (packageInfo.versionName ?: "N/A"),
            "Version Code" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            },
            "First Install" to formatTimestamp(packageInfo.firstInstallTime),
            "Last Update" to formatTimestamp(packageInfo.lastUpdateTime),
            "Target SDK" to packageInfo.applicationInfo.targetSdkVersion.toString(),
            "Min SDK" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageInfo.applicationInfo.minSdkVersion.toString()
            } else {
                "N/A"
            }
        )

        val applicationFlags = getApplicationFlags(appInfo.flags)
        val permissions = packageInfo.requestedPermissions?.sorted() ?: emptyList()
        val activities = packageInfo.activities?.map { it.name }?.sorted() ?: emptyList()
        val services = packageInfo.services?.map { it.name }?.sorted() ?: emptyList()
        val receivers = packageInfo.receivers?.map { it.name }?.sorted() ?: emptyList()
        val providers = packageInfo.providers?.map { it.name }?.sorted() ?: emptyList()

        AppDetailInfo(
            appName = appInfo.loadLabel(pm).toString(),
            packageName = appInfo.packageName,
            icon = appInfo.loadIcon(pm),
            manifestSummary = manifestSummary,
            applicationFlags = applicationFlags,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

private fun getApplicationFlags(flags: Int): List<String> {
    val flagList = mutableListOf<String>()
    if (flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0) flagList.add("ALLOW_BACKUP")
    if (flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) flagList.add("DEBUGGABLE")
    if (flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS != 0) flagList.add("EXTRACT_NATIVE_LIBS")
    if (flags and ApplicationInfo.FLAG_FULL_BACKUP_ONLY != 0) flagList.add("FULL_BACKUP_ONLY")
    if (flags and ApplicationInfo.FLAG_HAS_CODE != 0) flagList.add("HAS_CODE")
    if (flags and ApplicationInfo.FLAG_LARGE_HEAP != 0) flagList.add("LARGE_HEAP")
    if (flags and ApplicationInfo.FLAG_PERSISTENT != 0) flagList.add("PERSISTENT")
    if (flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0) flagList.add("SUPPORTS_RTL")
    if (flags and ApplicationInfo.FLAG_TEST_ONLY != 0) flagList.add("TEST_ONLY")
    if (flags and ApplicationInfo.FLAG_VM_SAFE_MODE != 0) flagList.add("VM_SAFE_MODE")
    return flagList.sorted()
}
