/*
 * Enhanced Network Monitor Screen with Session Logging and JSON Export
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/NetworkMonitorScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import com.mhk.deviceinspector.services.NetworkMonitorVpnService
import com.mhk.deviceinspector.ui.components.GenericScreen
import com.mhk.deviceinspector.util.formatTimestamp
import com.mhk.deviceinspector.util.StoragePermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// Data models for session logging
data class NetworkSession(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long?,
    val connections: MutableList<NetworkLogEntry> = mutableListOf()
)

data class NetworkLogEntry(
    val timestamp: Long,
    val protocol: String,
    val sourceAddress: String,
    val destinationAddress: String,
    val sourcePort: Int?,
    val destinationPort: Int?,
    val connectionInfo: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isVpnRunning by remember { mutableStateOf(NetworkMonitorVpnService.isRunning) }
    var currentSession by remember { mutableStateOf<NetworkSession?>(null) }
    var sessions by remember { mutableStateOf<List<NetworkSession>>(emptyList()) }
    var isExporting by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }

    // Real-time connections for current session
    val connections = remember { mutableStateListOf<String>() }

    // Load existing sessions on startup
    LaunchedEffect(Unit) {
        sessions = loadSessions(context)
    }

    // Connection receiver for real-time updates
    val connectionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(NetworkMonitorVpnService.EXTRA_CONNECTION_INFO)?.let { connectionInfo ->
                    connections.add(0, connectionInfo)

                    // Add to current session if active
                    currentSession?.let { session ->
                        val logEntry = parseConnectionInfo(connectionInfo)
                        session.connections.add(logEntry)

                        // Auto-save session periodically (every 10 connections)
                        if (session.connections.size % 10 == 0) {
                            context?.let { ctx ->
                                coroutineScope.launch {
                                    saveSession(ctx, session)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Register receiver
    DisposableEffect(context) {
        val filter = IntentFilter(NetworkMonitorVpnService.BROADCAST_ACTION_CONNECTION)
        LocalBroadcastManager.getInstance(context).registerReceiver(connectionReceiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(connectionReceiver)
        }
    }

    // VPN permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startMonitoring(context) { session ->
                currentSession = session
                isVpnRunning = true
                connections.clear()
            }
        }
    }

    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if permission was granted after returning from settings
        // The actual export will be triggered again by the user
    }

    GenericScreen("Network Monitor", navController = navController) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isVpnRunning) {
                            stopMonitoring(context) { session ->
                                currentSession?.let {
                                    coroutineScope.launch {
                                        val completedSession = it.copy(endTime = System.currentTimeMillis())
                                        saveSession(context, completedSession)
                                        sessions = sessions + completedSession
                                    }
                                }
                                currentSession = null
                                isVpnRunning = false
                            }
                        } else {
                            val vpnPrepareIntent = VpnService.prepare(context)
                            if (vpnPrepareIntent != null) {
                                vpnPermissionLauncher.launch(vpnPrepareIntent)
                            } else {
                                startMonitoring(context) { session ->
                                    currentSession = session
                                    isVpnRunning = true
                                    connections.clear()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isVpnRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isVpnRunning) "Stop Session" else "Start Session")
                }

                Button(
                    onClick = {
                        // Check storage permission before showing export dialog
                        if (StoragePermissionHelper.hasStoragePermission(context)) {
                            showExportDialog = true
                        } else {
                            StoragePermissionHelper.requestStoragePermission(context, storagePermissionLauncher)
                        }
                    },
                    enabled = sessions.isNotEmpty() && !isExporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export JSON")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current session info
            currentSession?.let { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Active Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Session ID: ${session.sessionId}")
                        Text("Started: ${formatTimestamp(session.startTime)}")
                        Text("Connections Logged: ${session.connections.size}")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tabs for Real-time and Sessions
            var selectedTab by remember { mutableStateOf(0) }
            val tabs = listOf("Real-time", "Sessions (${sessions.size})")

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab content
            when (selectedTab) {
                0 -> {
                    // Real-time connections
                    if (isVpnRunning) {
                        Text(
                            "Monitoring network traffic...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(connections) { connectionInfo ->
                                ConnectionInfoCard(info = connectionInfo)
                            }
                        }
                    } else {
                        Text(
                            "Press 'Start Session' to begin monitoring and logging network traffic.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                1 -> {
                    // Session history
                    if (sessions.isEmpty()) {
                        Text(
                            "No network sessions recorded yet. Start a monitoring session to begin logging.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sessions.sortedByDescending { it.startTime }) { session ->
                                SessionCard(
                                    session = session,
                                    onDelete = { sessionToDelete ->
                                        coroutineScope.launch {
                                            deleteSession(context, sessionToDelete.sessionId)
                                            sessions = sessions.filter { it.sessionId != sessionToDelete.sessionId }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Session Logs") },
            text = {
                Column {
                    Text("Export ${sessions.size} network sessions to JSON file?")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "File will be saved to: ${StoragePermissionHelper.getExportPathForDisplay()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (exportMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            exportMessage,
                            color = if (exportMessage.contains("Success"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            try {
                                val result = exportSessionsToJson(context, sessions)
                                exportMessage = result
                                if (result.contains("Success")) {
                                    // Auto-close dialog after 2 seconds on success
                                    kotlinx.coroutines.delay(2000)
                                    showExportDialog = false
                                    exportMessage = ""
                                }
                            } catch (e: Exception) {
                                exportMessage = "Export failed: ${e.message}"
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        exportMessage = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConnectionInfoCard(info: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = info,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: NetworkSession,
    onDelete: (NetworkSession) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Session ${session.sessionId.take(8)}...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Started: ${formatTimestamp(session.startTime)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    session.endTime?.let {
                        Text(
                            "Ended: ${formatTimestamp(it)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "${session.connections.size} connections logged",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle details"
                    )
                }

                IconButton(onClick = { onDelete(session) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (expanded && session.connections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Connection Details:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                // Show first few connections as preview
                session.connections.take(5).forEach { connection ->
                    Text(
                        "${formatTimestamp(connection.timestamp)}: ${connection.connectionInfo}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                if (session.connections.size > 5) {
                    Text(
                        "... and ${session.connections.size - 5} more connections",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Helper functions
private fun startMonitoring(context: Context, onSessionStarted: (NetworkSession) -> Unit) {
    val session = NetworkSession(
        sessionId = UUID.randomUUID().toString(),
        startTime = System.currentTimeMillis(),
        endTime = null
    )

    context.startService(Intent(context, NetworkMonitorVpnService::class.java).apply {
        action = NetworkMonitorVpnService.ACTION_START
    })

    onSessionStarted(session)
}

private fun stopMonitoring(context: Context, onSessionStopped: (NetworkSession?) -> Unit) {
    context.startService(Intent(context, NetworkMonitorVpnService::class.java).apply {
        action = NetworkMonitorVpnService.ACTION_STOP
    })

    onSessionStopped(null)
}

private fun parseConnectionInfo(connectionInfo: String): NetworkLogEntry {
    // Parse the connection string to extract structured data
    // This is a simplified parser - you might want to enhance this based on your actual format
    val parts = connectionInfo.split(", ")
    val timestamp = System.currentTimeMillis()

    return NetworkLogEntry(
        timestamp = timestamp,
        protocol = parts.find { it.startsWith("Proto:") }?.substringAfter("Proto: ") ?: "Unknown",
        sourceAddress = parts.find { it.startsWith("Src:") }?.substringAfter("Src: ") ?: "Unknown",
        destinationAddress = parts.find { it.startsWith("Dst:") }?.substringAfter("Dst: ") ?: "Unknown",
        sourcePort = null, // Extract if available in your format
        destinationPort = null, // Extract if available in your format
        connectionInfo = connectionInfo
    )
}

private suspend fun saveSession(context: Context, session: NetworkSession) = withContext(Dispatchers.IO) {
    try {
        val sessionsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "NetworkSessions")
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs()
        }

        val sessionFile = File(sessionsDir, "${session.sessionId}.json")
        val json = sessionToJson(session)

        FileWriter(sessionFile).use { writer ->
            writer.write(json.toString(2))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun loadSessions(context: Context): List<NetworkSession> = withContext(Dispatchers.IO) {
    try {
        val sessionsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "NetworkSessions")
        if (!sessionsDir.exists()) return@withContext emptyList()

        val sessions = mutableListOf<NetworkSession>()
        sessionsDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                sessions.add(jsonToSession(json))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        sessions.sortedByDescending { it.startTime }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private suspend fun deleteSession(context: Context, sessionId: String) = withContext(Dispatchers.IO) {
    try {
        val sessionsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "NetworkSessions")
        val sessionFile = File(sessionsDir, "$sessionId.json")
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun exportSessionsToJson(context: Context, sessions: List<NetworkSession>): String = withContext(Dispatchers.IO) {
    try {
        // Check permission first
        if (!StoragePermissionHelper.hasStoragePermission(context)) {
            return@withContext "Storage permission not granted. Please grant permission and try again."
        }

        // Create export directory
        val exportDir = StoragePermissionHelper.createExportDirectory()
            ?: return@withContext "Failed to create export directory. Please check storage permissions."

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportFile = File(exportDir, "network_sessions_$timestamp.json")

        val exportJson = JSONObject().apply {
            put("exportDate", System.currentTimeMillis())
            put("exportDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("totalSessions", sessions.size)
            put("totalConnections", sessions.sumOf { it.connections.size })
            put("deviceInfo", JSONObject().apply {
                put("appVersion", "1.0")
                put("exportedBy", "Device Inspector")
            })
            put("sessions", JSONArray().apply {
                sessions.forEach { session ->
                    put(sessionToJson(session))
                }
            })
        }

        FileWriter(exportFile).use { writer ->
            writer.write(exportJson.toString(2))
        }

        "Success! Exported to: ${StoragePermissionHelper.getExportPathForDisplay()}${exportFile.name}"
    } catch (e: Exception) {
        e.printStackTrace()
        "Export failed: ${e.message}"
    }
}

private fun sessionToJson(session: NetworkSession): JSONObject {
    return JSONObject().apply {
        put("sessionId", session.sessionId)
        put("startTime", session.startTime)
        put("endTime", session.endTime)
        put("connections", JSONArray().apply {
            session.connections.forEach { connection ->
                put(JSONObject().apply {
                    put("timestamp", connection.timestamp)
                    put("protocol", connection.protocol)
                    put("sourceAddress", connection.sourceAddress)
                    put("destinationAddress", connection.destinationAddress)
                    put("sourcePort", connection.sourcePort)
                    put("destinationPort", connection.destinationPort)
                    put("connectionInfo", connection.connectionInfo)
                })
            }
        })
    }
}

private fun jsonToSession(json: JSONObject): NetworkSession {
    val connections = mutableListOf<NetworkLogEntry>()
    val connectionsArray = json.getJSONArray("connections")

    for (i in 0 until connectionsArray.length()) {
        val connJson = connectionsArray.getJSONObject(i)
        connections.add(
            NetworkLogEntry(
                timestamp = connJson.getLong("timestamp"),
                protocol = connJson.getString("protocol"),
                sourceAddress = connJson.getString("sourceAddress"),
                destinationAddress = connJson.getString("destinationAddress"),
                sourcePort = if (connJson.isNull("sourcePort")) null else connJson.getInt("sourcePort"),
                destinationPort = if (connJson.isNull("destinationPort")) null else connJson.getInt("destinationPort"),
                connectionInfo = connJson.getString("connectionInfo")
            )
        )
    }

    return NetworkSession(
        sessionId = json.getString("sessionId"),
        startTime = json.getLong("startTime"),
        endTime = if (json.isNull("endTime")) null else json.getLong("endTime"),
        connections = connections
    )
}