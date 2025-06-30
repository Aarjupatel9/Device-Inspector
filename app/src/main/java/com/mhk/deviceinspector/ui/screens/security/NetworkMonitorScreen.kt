/*
 * This file contains the UI for the Network Monitor feature.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/NetworkMonitorScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import com.mhk.deviceinspector.services.NetworkMonitorVpnService
import com.mhk.deviceinspector.ui.components.GenericScreen

@Composable
fun NetworkMonitorScreen(navController: NavController) {
    val context = LocalContext.current
    var isVpnRunning by remember { mutableStateOf(NetworkMonitorVpnService.isRunning) }
    val connections = remember { mutableStateListOf<String>() }

    // This receiver will listen for broadcasts from the VpnService
    val connectionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(NetworkMonitorVpnService.EXTRA_CONNECTION_INFO)?.let {
                    // Add new connections to the top of the list
                    connections.add(0, it)
                }
            }
        }
    }

    // Register and unregister the receiver with the composable's lifecycle
    DisposableEffect(context) {
        val filter = IntentFilter(NetworkMonitorVpnService.BROADCAST_ACTION_CONNECTION)
        LocalBroadcastManager.getInstance(context).registerReceiver(connectionReceiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(connectionReceiver)
        }
    }

    // This launcher will handle the result of the VPN permission request from the system.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted, start the service
            context.startService(Intent(context, NetworkMonitorVpnService::class.java).apply {
                action = NetworkMonitorVpnService.ACTION_START
            })
            isVpnRunning = true
            connections.clear()
        }
    }

    GenericScreen("Network Monitor", navController = navController) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (isVpnRunning) {
                        // If it's running, send an intent to stop it.
                        context.startService(Intent(context, NetworkMonitorVpnService::class.java).apply {
                            action = NetworkMonitorVpnService.ACTION_STOP
                        })
                        isVpnRunning = false
                    } else {
                        // If it's not running, prepare to ask for permission.
                        val vpnPrepareIntent = VpnService.prepare(context)
                        if (vpnPrepareIntent != null) {
                            // The system needs to ask the user for permission. Launch the intent.
                            vpnPermissionLauncher.launch(vpnPrepareIntent)
                        } else {
                            // Permission was already granted. Start the service directly.
                            context.startService(Intent(context, NetworkMonitorVpnService::class.java).apply {
                                action = NetworkMonitorVpnService.ACTION_START
                            })
                            isVpnRunning = true
                            connections.clear()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(if (isVpnRunning) "Stop Monitoring" else "Start Monitoring")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isVpnRunning) {
                Text("Monitoring network traffic...", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                // Display the list of captured connections
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(connections) { connectionInfo ->
                        ConnectionInfoCard(info = connectionInfo)
                    }
                }
            } else {
                Text(
                    "Press 'Start Monitoring' to begin capturing network traffic. This will create a local VPN service on your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ConnectionInfoCard(info: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = info,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
