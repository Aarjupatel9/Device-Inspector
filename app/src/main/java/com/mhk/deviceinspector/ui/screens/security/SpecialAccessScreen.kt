/*
 * This file contains the UI and logic for the Special Access screen.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/SpecialAccessScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.health.connect.datatypes.AppInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.data.AllSpecialAccessApps
import com.mhk.deviceinspector.data.SpecialAccessApp
import com.mhk.deviceinspector.ui.components.GenericScreen
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun SpecialAccessScreen(
    specialAccessApps: AllSpecialAccessApps?,
    onRefresh: () -> Unit,
    navController: NavController
) {
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefresh()
    }

    GenericScreen("Special App Access", navController = navController) {
        when (specialAccessApps) {
            null -> {
                CircularProgressIndicator()
                Text("Scanning for special access apps...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        SpecialAccessCategory(
                            title = "Device Administrators",
                            description = "Apps that can control screen lock, passwords, or wipe data.",
                            apps = specialAccessApps.deviceAdmins,
                            intentAction = android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                        )
                    }
                    item {
                        SpecialAccessCategory(
                            title = "Accessibility Services",
                            description = "Apps that can read screen content and perform actions for you.",
                            apps = specialAccessApps.accessibilityServices,
                            intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                    }
                    item {
                        SpecialAccessCategory(
                            title = "Display Over Other Apps",
                            description = "Apps that can draw on top of other running applications.",
                            apps = specialAccessApps.drawOverApps,
                            intentAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecialAccessCategory(
    title: String,
    description: String,
    apps: List<SpecialAccessApp>,
    intentAction: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {} // Refresh is handled by the parent screen
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            if (apps.isEmpty()) {
                Text("No apps found with this access.", style = MaterialTheme.typography.bodyMedium)
            } else {
                apps.forEach { app ->
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = rememberDrawablePainter(drawable = app.icon),
                            contentDescription = app.appName,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.bodyLarge)
                            val isUndeclared = app.description.contains("Not declared in manifest")
                            Text(
                                text = app.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isUndeclared) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isUndeclared) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    val intent = Intent(intentAction)
                    // For Device Admin, the intent needs the specific component to add
                    if (intentAction == DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN && apps.isNotEmpty()) {
                        val componentName = ComponentName(apps.first().packageName, "") // This is a simplification
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    }
                    settingsLauncher.launch(intent)
                }) {
                    Text("Manage")
                }
            }
        }
    }
}

internal fun getSpecialAccessApps(context: Context): AllSpecialAccessApps {
    val pm = context.packageManager

    // 1. Get Device Admins
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminApps = dpm.activeAdmins?.mapNotNull { componentName ->
        try {
            val packageInfo = pm.getPackageInfo(componentName.packageName, PackageManager.GET_PERMISSIONS)
            val appInfo = packageInfo.applicationInfo
            val isDeclared = packageInfo.requestedPermissions?.contains(Manifest.permission.BIND_DEVICE_ADMIN) == true
            val description = if (isDeclared) "Can enforce security policies." else "Active admin (Not declared in manifest)"

            SpecialAccessApp(
                appName = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(pm),
                description = description
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    } ?: emptyList()

    // 2. Get Accessibility Services
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    // Use the literal value (-1) for FEEDBACK_ALL_MASK to avoid resolution issues.
    val accessibilityServices = am.getEnabledAccessibilityServiceList(-1)
        .mapNotNull { serviceInfo ->
            try {
                val appInfo = serviceInfo.resolveInfo.serviceInfo.applicationInfo
                val packageInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                val isDeclared = packageInfo.requestedPermissions?.contains(Manifest.permission.BIND_ACCESSIBILITY_SERVICE) == true
                val defaultDesc = serviceInfo.loadDescription(pm) ?: "Can read screen content."
                val description = if (isDeclared) defaultDesc else "$defaultDesc (Not declared in manifest)"

                SpecialAccessApp(
                    appName = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    description = description
                )
            } catch (e: Exception) {
                null
            }
        }

    // 3. Get Apps that can draw over other apps
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    // Get a comprehensive list of all applications, including system and disabled ones.

    val allApplications = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)

    val drawOverApps = allApplications.mapNotNull { appInfo ->
        // For each app, check if the permission is granted at runtime
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, appInfo.uid, appInfo.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, appInfo.uid, appInfo.packageName)
        }

        if (mode == AppOpsManager.MODE_ALLOWED) {
            // If granted, then get its package info to check for declaration in the manifest
            try {
                val packageInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                val isDeclared = packageInfo.requestedPermissions?.contains(Manifest.permission.SYSTEM_ALERT_WINDOW) == true
                val description = if (isDeclared) "Can display on top of other apps." else "Can display on top of other apps (Not declared in manifest)"
                SpecialAccessApp(
                    appName = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    description = description
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null // Should not happen, but good practice
            }
        } else {
            null
        }
    }


    return AllSpecialAccessApps(
        deviceAdmins = adminApps,
        accessibilityServices = accessibilityServices,
        drawOverApps = drawOverApps
    )
}
