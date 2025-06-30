/*
 * This file is the main entry point of the application and handles navigation.
 * It now manages the state for all screens to prevent re-loading on tab switch.
 * Location: app/src/main/java/com/mhk/deviceinspector/MainActivity.kt
 */
package com.mhk.deviceinspector

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.mhk.deviceinspector.data.*
import com.mhk.deviceinspector.ui.components.PermissionRequestScreen
import com.mhk.deviceinspector.ui.screens.*
import com.mhk.deviceinspector.ui.screens.security.*
import com.mhk.deviceinspector.ui.theme.DeviceInspectorTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceInspectorTheme {
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

// --- App Navigation ---
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Usage : Screen("usage", "Usage", Icons.Default.PieChart)
    object History : Screen("history", "History", Icons.Default.History)
    object DeviceInfo : Screen("device_info", "Device Info", Icons.Default.Info)

    // Updated Security Section Routes
    object SecurityHub : Screen("security_hub", "Security", Icons.Default.Security)
    object HiddenApps : Screen("hidden_apps", "Hidden Apps", Icons.Default.VisibilityOff)
    object DangerousPermissions : Screen("dangerous_permissions", "Dangerous Permissions", Icons.Default.VpnKey)
    object SpecialAccess : Screen("special_access", "Special Access", Icons.Default.VpnLock)
    object NetworkMonitor : Screen("network_monitor", "Network Monitor", Icons.Default.Public)
    object AppComponents : Screen("app_components", "App Components", Icons.Default.Extension)
}

val navItems = listOf(Screen.Usage, Screen.History, Screen.DeviceInfo, Screen.SecurityHub)

@Composable
fun MainApp() {
    val context = LocalContext.current
    var hasUsageStatsPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    val usageSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageStatsPermission = hasUsageStatsPermission(context)
    }

    if (hasUsageStatsPermission) {
        AppWithNavigation()
    } else {
        PermissionRequestScreen {
            usageSettingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}

@Composable
fun AppWithNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    // --- State Hoisting: Hold the data for all screens here ---
    var usageInfo by remember { mutableStateOf<List<AppUsageInfo>?>(null) }
    var historyInfo by remember { mutableStateOf<List<AppEventInfo>?>(null) }
    var securityInfo by remember { mutableStateOf<List<HiddenAppInfo>?>(null) }
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var permissionsInfo by remember { mutableStateOf<List<PermissionAppInfo>?>(null) }
    var specialAccessInfo by remember { mutableStateOf<AllSpecialAccessApps?>(null) }


    var historyFilterMillis by remember { mutableStateOf(4 * 60 * 60 * 1000L) }

    // This effect runs only once, fetching all data in parallel background threads.
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) { usageInfo = getAppUsageStats(context) }
        launch(Dispatchers.IO) { securityInfo = findHiddenApps(context) }
        launch(Dispatchers.IO) { deviceInfo = getDetailedDeviceInfo(context) }
        launch(Dispatchers.IO) { permissionsInfo = getDangerousPermissionsApps(context) }
        launch(Dispatchers.IO) { specialAccessInfo = getSpecialAccessApps(context) }
    }

    // This effect re-runs ONLY when the history filter changes
    LaunchedEffect(historyFilterMillis) {
        historyInfo = null // Show loader
        launch(Dispatchers.IO) { historyInfo = getAppLaunchHistory(context, historyFilterMillis) }
    }


    Scaffold(
        bottomBar = { AppBottomNavigation(navController) }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            usageInfo = usageInfo,
            historyInfo = historyInfo,
            deviceInfo = deviceInfo,
            securityInfo = securityInfo,
            permissionsInfo = permissionsInfo,
            specialAccessInfo = specialAccessInfo,
            selectedHistoryDuration = historyFilterMillis,
            onHistoryDurationChange = { newDuration ->
                historyFilterMillis = newDuration
            },
            onRefreshSecurityInfo = {
                coroutineScope.launch(Dispatchers.IO) {
                    securityInfo = null // Show loader
                    securityInfo = findHiddenApps(context)
                }
            },
            onRefreshSpecialAccessInfo = {
                coroutineScope.launch(Dispatchers.IO) {
                    specialAccessInfo = null // Show loader
                    specialAccessInfo = getSpecialAccessApps(context)
                }
            }
        )
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
                selected = currentRoute?.startsWith(screen.route.substringBefore("_")) ?: false,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    usageInfo: List<AppUsageInfo>?,
    historyInfo: List<AppEventInfo>?,
    deviceInfo: DeviceInfo?,
    securityInfo: List<HiddenAppInfo>?,
    permissionsInfo: List<PermissionAppInfo>?,
    specialAccessInfo: AllSpecialAccessApps?,
    selectedHistoryDuration: Long,
    onHistoryDurationChange: (Long) -> Unit,
    onRefreshSecurityInfo: () -> Unit,
    onRefreshSpecialAccessInfo: () -> Unit
) {
    NavHost(navController, startDestination = Screen.Usage.route, modifier = modifier) {
        composable(Screen.Usage.route) { UsageScreen(usageInfo) }
        composable(Screen.History.route) { HistoryScreen(historyInfo, selectedHistoryDuration, onHistoryDurationChange) }
        composable(Screen.DeviceInfo.route) { DeviceInfoScreen(deviceInfo) }

        // New Security Navigation
        composable(Screen.SecurityHub.route) { SecurityHubScreen(navController) }
        composable(Screen.HiddenApps.route) { SecurityScreen(securityInfo, onRefreshSecurityInfo, navController) }
        composable(Screen.DangerousPermissions.route) { DangerousPermissionsScreen(permissionsInfo, navController) }
        composable(Screen.SpecialAccess.route) { SpecialAccessScreen(specialAccessInfo, onRefreshSpecialAccessInfo, navController) }
        composable(Screen.NetworkMonitor.route) { NetworkMonitorScreen(navController) }

        // Add routes for the "Coming Soon" screens
        composable(Screen.AppComponents.route) {
            ComingSoonScreen(
                navController = navController,
                featureName = Screen.AppComponents.label,
                featureIcon = Screen.AppComponents.icon
            )
        }
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
