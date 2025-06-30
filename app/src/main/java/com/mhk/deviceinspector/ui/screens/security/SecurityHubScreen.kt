/*
 * This file contains the UI for the main Security Hub.
 * It provides navigation to the various security tools.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/SecurityHubScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.Screen
import com.mhk.deviceinspector.ui.components.GenericScreen

data class SecurityFeature(
    val screen: Screen,
    val description: String
)

@Composable
fun SecurityHubScreen(navController: NavController) {
    val features = listOf(
        SecurityFeature(Screen.HiddenApps, "Scan for installed apps without a launcher icon."),
        SecurityFeature(Screen.DangerousPermissions, "Audit apps with high-risk permissions."),
        SecurityFeature(Screen.SpecialAccess, "Monitor apps with admin or accessibility rights."),
        SecurityFeature(Screen.NetworkMonitor, "Analyze real-time network traffic. (Coming Soon)"),
        SecurityFeature(Screen.AppComponents, "Inspect manifest for services & receivers. (Coming Soon)")
    )

    GenericScreen("Security Center") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(features) { feature ->
                SecurityFeatureCard(
                    title = feature.screen.label,
                    description = feature.description,
                    icon = feature.screen.icon,
                    onClick = { navController.navigate(feature.screen.route) }
                )
            }
        }
    }
}

@Composable
fun SecurityFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate"
            )
        }
    }
}
