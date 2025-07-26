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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import com.mhk.deviceinspector.data.NetworkConnectionInfo
import com.mhk.deviceinspector.data.NetworkSession
import com.mhk.deviceinspector.services.NetworkMonitorVpnService
import com.mhk.deviceinspector.ui.components.GenericScreen
import com.mhk.deviceinspector.util.formatTimestamp
import com.mhk.deviceinspector.util.StoragePermissionHelper
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isVpnRunning by remember { mutableStateOf(NetworkMonitorVpnService.isRunning) }
    var currentSession by remember { mutableStateOf<NetworkSession?>(null) }
    var sessions by remember { mutableStateOf<List<NetworkSession>>(emptyList()) }
    var isExporting by remember { mutableStateOf(false) }
    var sessionToExport by remember { mutableStateOf<NetworkSession?>(null) }
    var exportMessage by remember { mutableStateOf("") }

    // Real-time connections for current session
    val connections = remember { mutableStateListOf<NetworkConnectionInfo>() }

    // Load existing sessions on startup
    LaunchedEffect(Unit) {
        sessions = loadSessions(context)
    }

    // Connection receiver for real-time updates
    val connectionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getParcelableExtra<NetworkConnectionInfo>(NetworkMonitorVpnService.EXTRA_CONNECTION_INFO)?.let { connectionInfo ->
                    connections.add(0, connectionInfo)

                    // Create a new session object to trigger recomposition for the active session card
                    currentSession?.let { session ->
                        val updatedConnections = session.connections.toMutableList().apply { add(connectionInfo) }
                        currentSession = session.copy(connections = updatedConnections)
                    }

                    // Enforce the 100 item limit for the real-time view
                    if (connections.size > 100) {
                        connections.removeLast()
                    }

                    // Auto-save session periodically (every 10 connections)
                    if (currentSession?.connections?.size?.rem(10) == 0) {
                        context?.let { ctx ->
                            coroutineScope.launch {
                                currentSession?.let { saveSession(ctx, it) }
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
        // The actual export will be triggered again by the user
    }

    GenericScreen("Network Monitor", navController = navController) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Control buttons
            Button(
                onClick = {
                    if (isVpnRunning) {
                        stopMonitoring(context) {
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isVpnRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isVpnRunning) "Stop Session" else "Start Session")
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
                        Text("Session ID: ${session.sessionId.take(8)}...")
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Monitoring network traffic...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { connections.clear() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear real-time log")
                            }
                        }
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
                                    },
                                    onExport = { sessionToExport = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export dialog for a single session
    if (sessionToExport != null) {
        AlertDialog(
            onDismissRequest = { sessionToExport = null },
            title = { Text("Export Session") },
            text = {
                Column {
                    Text("Export session ${sessionToExport?.sessionId?.take(8)}... to a JSON file?")
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
                        if (StoragePermissionHelper.hasStoragePermission(context)) {
                            isExporting = true
                            coroutineScope.launch {
                                try {
                                    val result = exportSingleSessionToJson(context, sessionToExport!!)
                                    exportMessage = result
                                    if (result.contains("Success")) {
                                        kotlinx.coroutines.delay(2000)
                                        sessionToExport = null
                                        exportMessage = ""
                                    }
                                } catch (e: Exception) {
                                    exportMessage = "Export failed: ${e.message}"
                                } finally {
                                    isExporting = false
                                }
                            }
                        } else {
                            StoragePermissionHelper.requestStoragePermission(context, storagePermissionLauncher)
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
                        sessionToExport = null
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
fun ConnectionInfoCard(info: NetworkConnectionInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = info.icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(info.appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${info.sourceAddress}:${info.sourcePort} -> ${info.destinationAddress}:${info.destinationPort}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(info.protocol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${info.packetSize} B", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: NetworkSession,
    onDelete: (NetworkSession) -> Unit,
    onExport: (NetworkSession) -> Unit
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

                IconButton(onClick = { onExport(session) }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Export session"
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
                        "${formatTimestamp(connection.timestamp)}: ${connection.appName} -> ${connection.destinationAddress}",
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

private suspend fun exportSingleSessionToJson(context: Context, session: NetworkSession): String = withContext(Dispatchers.IO) {
    try {
        if (!StoragePermissionHelper.hasStoragePermission(context)) {
            return@withContext "Storage permission not granted."
        }

        val exportDir = StoragePermissionHelper.createExportDirectory()
            ?: return@withContext "Failed to create export directory."

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportFile = File(exportDir, "session_${session.sessionId.take(8)}_$timestamp.json")

        val exportJson = sessionToJson(session)

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
                    put("appName", connection.appName)
                    put("packageName", connection.packageName)
                    put("protocol", connection.protocol)
                    put("sourceAddress", connection.sourceAddress)
                    put("sourcePort", connection.sourcePort)
                    put("destinationAddress", connection.destinationAddress)
                    put("destinationPort", connection.destinationPort)
                    put("packetSize", connection.packetSize)
                })
            }
        })
    }
}

private fun jsonToSession(json: JSONObject): NetworkSession {
    val connections = mutableListOf<NetworkConnectionInfo>()
    val connectionsArray = json.getJSONArray("connections")

    for (i in 0 until connectionsArray.length()) {
        val connJson = connectionsArray.getJSONObject(i)
        connections.add(
            NetworkConnectionInfo(
                timestamp = connJson.getLong("timestamp"),
                appName = connJson.getString("appName"),
                packageName = connJson.getString("packageName"),
                protocol = connJson.getString("protocol"),
                sourceAddress = connJson.getString("sourceAddress"),
                sourcePort = connJson.getInt("sourcePort"),
                destinationAddress = connJson.getString("destinationAddress"),
                destinationPort = connJson.getInt("destinationPort"),
                packetSize = connJson.getInt("packetSize"),
                icon = null // Icon is not saved in JSON
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
