/*
 * This file contains a generic placeholder screen for upcoming features.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/security/ComingSoonScreen.kt
 */
package com.mhk.deviceinspector.ui.screens.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.ui.components.GenericScreen

@Composable
fun ComingSoonScreen(
    navController: NavController,
    featureName: String,
    featureIcon: ImageVector
) {
    GenericScreen(title = featureName, navController = navController) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = featureIcon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Coming Soon!",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "This feature is currently under development.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
