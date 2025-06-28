/*
 * This file is the main entry point of the application.
 * Location: app/src/main/java/com/example/deviceinspector/MainActivity.kt
 */
package com.example.deviceinspector

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.deviceinspector.ui.theme.DeviceInspectorTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceInspectorTheme {
                // Set status bar color to match the app theme
                val systemUiController = rememberSystemUiController()
                val useDarkIcons = !isSystemInDarkTheme()
                val statusBarColor = MaterialTheme.colorScheme.surface

                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = statusBarColor,
                        darkIcons = useDarkIcons
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

// --- App Navigation Sealed Class for defining screens ---
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Usage : Screen("usage", "Usage", Icons.Default.PieChart)
    object History : Screen("history", "History", Icons.Default.History)
    object DeviceInfo : Screen("device_info", "Device Info", Icons.Default.Info)
    object Security : Screen("security", "Security", Icons.Default.Security)
}

// List of screens for the bottom navigation bar
val navItems = listOf(Screen.Usage, Screen.History, Screen.DeviceInfo, Screen.Security)

@Composable
fun MainApp() {
    val context = LocalContext.current
    // Check for permission state. This state will not auto-update if the user grants it
    // while the app is in the background. A lifecycle-aware check would be an improvement.
    var hasUsageStatsPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    if (hasUsageStatsPermission) {
        AppWithNavigation()
    } else {
        // Show a screen that guides the user to the settings page
        PermissionRequestScreen {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}

@Composable
fun AppWithNavigation() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { AppBottomNavigation(navController) }
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun AppBottomNavigation(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        navItems.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        // Pop up to the start destination to avoid building a large back stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state on re-selection
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController, startDestination = Screen.Usage.route, modifier = modifier) {
        composable(Screen.Usage.route) { UsageScreen() }
        composable(Screen.History.route) { HistoryScreen() }
        composable(Screen.DeviceInfo.route) { DeviceInfoScreen() }
        composable(Screen.Security.route) { SecurityScreen() }
    }
}


// --- Placeholder Screens ---

@Composable
fun UsageScreen() {
    GenericScreen("App Usage") {
        Text("This screen will show app usage statistics.\n\nImplementation requires using `UsageStatsManager` to query for app usage data within a specific time range.")
    }
}

@Composable
fun HistoryScreen() {
    GenericScreen("Usage History") {
        Text("This screen will show a timeline of recently used apps.\n\nLike the Usage screen, this will use `UsageStatsManager` but focus on the `getLastTimeUsed()` property of `UsageStats`.")
    }
}

@Composable
fun DeviceInfoScreen() {
    GenericScreen("Device Information") {
        val deviceInfo = getDeviceDetails()
        // A LazyColumn is better for long lists than a standard Column
        androidx.compose.foundation.lazy.LazyColumn {
            items(deviceInfo.entries.toList()) { (key, value) ->
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("$key: ", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun SecurityScreen() {
    val context = LocalContext.current
    GenericScreen("Hidden Apps Detector") {
        val hiddenApps = remember { findHiddenApps(context) }
        if (hiddenApps.isEmpty()) {
            Text("No hidden applications found. All installed apps have a launcher icon.")
        } else {
            Column {
                Text(
                    "Found ${hiddenApps.size} app(s) without a launcher icon:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                androidx.compose.foundation.lazy.LazyColumn {
                    items(hiddenApps) { appName ->
                        Text(
                            "• $appName",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}


// --- Helper Functions & UI Components ---

/**
 * A generic screen layout template.
 */
@Composable
fun GenericScreen(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        content()
    }
}

/**
 * A screen to request the Usage Stats permission from the user.
 */
@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "To track app usage, this app needs access to your device's usage data. Please grant permission in the next screen.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

/**
 * Checks if the app has the `PACKAGE_USAGE_STATS` permission.
 */
private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * Gathers a map of basic device details from the `android.os.Build` class.
 */
fun getDeviceDetails(): Map<String, String> {
    return mapOf(
        "Model" to Build.MODEL,
        "Manufacturer" to Build.MANUFACTURER,
        "Brand" to Build.BRAND,
        "Device" to Build.DEVICE,
        "Product" to Build.PRODUCT,
        "Hardware" to Build.HARDWARE,
        "Android Version" to Build.VERSION.RELEASE,
        "API Level" to Build.VERSION.SDK_INT.toString(),
        "Build ID" to Build.ID,
    )
}

/**
 * Finds installed applications that do not have a main launcher activity.
 * These are often background services, but could also be malicious.
 * @return A list of app names that are "hidden".
 */
fun findHiddenApps(context: Context): List<String> {
    val pm: PackageManager = context.packageManager
    // Get all installed applications on the device
    val allApps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val hiddenApps = mutableListOf<String>()

    for (appInfo in allApps) {
        // Filter for user-installed apps (not part of the system image)
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            // Check if the package manager can create a launch intent for the app
            val launchIntent: Intent? = pm.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent == null) {
                // If no launch intent is found, it's considered "hidden" from the launcher
                val appName = appInfo.loadLabel(pm).toString()
                hiddenApps.add(appName)
            }
        }
    }
    return hiddenApps.sorted()
}
