/*
 * This file contains the UI and logic for the Dangerous Permissions screen.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/DangerousPermissionsScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.data.PermissionAppInfo
import com.mhk.deviceinspector.ui.components.GenericScreen
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DangerousPermissionsScreen(
    permissionsInfoFromMain: List<PermissionAppInfo>?, // Renamed to clarify its origin
    navController: NavController
) {
    val context = LocalContext.current
    var permissionsInfo by remember { mutableStateOf(permissionsInfoFromMain) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // This launcher starts the settings activity. When the user returns,
    // it triggers a refresh of the app list.
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Increment the trigger to cause the LaunchedEffect to re-run
        refreshTrigger++
    }

    // This effect re-fetches the data whenever the refresh is triggered.
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) { // Don't re-fetch on initial composition
            permissionsInfo = null // Show loading indicator
            permissionsInfo = withContext(Dispatchers.IO) {
                getDangerousPermissionsApps(context)
            }
        }
    }

    GenericScreen("Dangerous Permissions Audit", navController = navController) {
        when (val apps = permissionsInfo) {
            null -> {
                CircularProgressIndicator()
                Text("Scanning app permissions...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                if (apps.isEmpty()) {
                    Text("No user-installed apps with dangerous permissions found.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(apps) { app ->
                            PermissionAppCard(app = app) {
                                // Create an intent to open the app's specific settings page
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.fromParts("package", app.packageName, null)
                                settingsLauncher.launch(intent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionAppCard(app: PermissionAppInfo, onManageClick: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.icon),
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge)
                    Text("${app.permissions.size} dangerous permissions granted", style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle permissions"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(modifier = Modifier.padding(bottom = 8.dp))
                    app.permissions.forEach { permission ->
                        Text(
                            text = "• ${permission.substringAfterLast('.')}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onManageClick) {
                            Text("Manage")
                        }
                    }
                }
            }
        }
    }
}

// A list of permissions considered "dangerous" by Android.
private val DANGEROUS_PERMISSIONS = listOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
    Manifest.permission.CAMERA,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.GET_ACCOUNTS,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.WRITE_CALL_LOG,
    Manifest.permission.ADD_VOICEMAIL,
    Manifest.permission.USE_SIP,
    Manifest.permission.PROCESS_OUTGOING_CALLS,
    Manifest.permission.BODY_SENSORS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_WAP_PUSH,
    Manifest.permission.RECEIVE_MMS,
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

internal fun getDangerousPermissionsApps(context: Context): List<PermissionAppInfo> {
    val pm = context.packageManager
    val appsWithPermissions = mutableListOf<PermissionAppInfo>()

    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    for (packageInfo in packages) {
        // Filter for user-installed apps only
        if (packageInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0) {
            val grantedPermissions = mutableListOf<String>()
            packageInfo.requestedPermissions?.forEachIndexed { index, permission ->
                if (packageInfo.requestedPermissionsFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) {
                    if (DANGEROUS_PERMISSIONS.contains(permission)) {
                        grantedPermissions.add(permission)
                    }
                }
            }

            if (grantedPermissions.isNotEmpty()) {
                appsWithPermissions.add(
                    PermissionAppInfo(
                        appName = packageInfo.applicationInfo.loadLabel(pm).toString(),
                        packageName = packageInfo.packageName,
                        icon = packageInfo.applicationInfo.loadIcon(pm),
                        permissions = grantedPermissions.sorted()
                    )
                )
            }
        }
    }
    return appsWithPermissions.sortedByDescending { it.permissions.size }
}
