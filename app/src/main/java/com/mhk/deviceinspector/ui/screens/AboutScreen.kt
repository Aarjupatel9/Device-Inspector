/*
 * This file contains the UI for the About screen.
 * Location: app/src/main/java/com/mhk/deviceinspector/ui/screens/AboutScreen.kt
 */
package com.mhk.deviceinspector.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mhk.deviceinspector.R
import com.mhk.deviceinspector.ui.components.GenericScreen

@Composable
fun AboutScreen(navController: NavController) {
    GenericScreen(title = "About", navController = navController) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // You would replace R.mipmap.ic_launcher with your actual icon resource
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "App Icon",
                modifier = Modifier.size(128.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Device Inspector",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Version 2.0.0",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Your all-in-one utility for monitoring and understanding your Android device. This tool is designed for users who want deep insights into their device's behavior, from application usage patterns to low-level security analysis.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Developed by",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Aarju Patel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "khm.developer@gmail.com",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
