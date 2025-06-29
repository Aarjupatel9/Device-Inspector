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
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.mhk.deviceinspector.data.AppEventInfo
import com.mhk.deviceinspector.data.AppUsageInfo
import com.mhk.deviceinspector.data.DeviceInfo
import com.mhk.deviceinspector.data.HiddenAppInfo
import com.mhk.deviceinspector.ui.components.PermissionRequestScreen
import com.mhk.deviceinspector.ui.screens.DeviceInfoScreen
import com.mhk.deviceinspector.ui.screens.HistoryScreen
import com.mhk.deviceinspector.ui.screens.SecurityScreen
import com.mhk.deviceinspector.ui.screens.UsageScreen
import com.mhk.deviceinspector.ui.screens.findHiddenApps
import com.mhk.deviceinspector.ui.screens.getAppLaunchHistory
import com.mhk.deviceinspector.ui.screens.getAppUsageStats
import com.mhk.deviceinspector.ui.screens.getDetailedDeviceInfo
import com.mhk.deviceinspector.ui.theme.DeviceInspectorTheme
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
    object Security : Screen("security", "Security", Icons.Default.Security)
}

val navItems = listOf(Screen.Usage, Screen.History, Screen.DeviceInfo, Screen.Security)

@Composable
fun MainApp() {
    val context = LocalContext.current
    var hasUsageStatsPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // This launcher opens the settings screen. When the user returns,
    // its callback is fired, where we re-check the permission state.
    val usageSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Update the permission state when the user returns from settings
        hasUsageStatsPermission = hasUsageStatsPermission(context)
    }

    if (hasUsageStatsPermission) {
        AppWithNavigation()
    } else {
        PermissionRequestScreen {
            // Launch the settings screen using the launcher
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
    // Add state for the history time filter, default to 4 hours
    var historyFilterMillis by remember { mutableStateOf(4 * 60 * 60 * 1000L) }


    // This effect runs only once, fetching initial data
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) { usageInfo = getAppUsageStats(context) }
        launch(Dispatchers.IO) { securityInfo = findHiddenApps(context) }
        launch(Dispatchers.IO) { deviceInfo = getDetailedDeviceInfo(context) }
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
            // Pass the data down to the screens
            usageInfo = usageInfo,
            historyInfo = historyInfo,
            deviceInfo = deviceInfo,
            securityInfo = securityInfo,
            selectedHistoryDuration = historyFilterMillis,
            // Provide a lambda to allow the history screen to change the filter
            onHistoryDurationChange = { newDuration ->
                historyFilterMillis = newDuration
            },
            // Provide a lambda to allow the security screen to trigger a refresh
            onRefreshSecurityInfo = {
                coroutineScope.launch(Dispatchers.IO) {
                    securityInfo = null // Show loader
                    securityInfo = findHiddenApps(context)
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
                selected = currentRoute == screen.route,
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
    selectedHistoryDuration: Long,
    onHistoryDurationChange: (Long) -> Unit,
    onRefreshSecurityInfo: () -> Unit
) {
    NavHost(navController, startDestination = Screen.Usage.route, modifier = modifier) {
        composable(Screen.Usage.route) { UsageScreen(usageInfo) }
        composable(Screen.History.route) { HistoryScreen(historyInfo, selectedHistoryDuration, onHistoryDurationChange) }
        composable(Screen.DeviceInfo.route) { DeviceInfoScreen(deviceInfo) }
        composable(Screen.Security.route) { SecurityScreen(securityInfo, onRefreshSecurityInfo) }
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
