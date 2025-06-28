/*
 * This file contains the UI and logic for the Device Info screen.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/DeviceInfoScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.deviceinspector.ui.components.GenericScreen

@Composable
fun DeviceInfoScreen() {
    GenericScreen("Device Information") {
        val deviceInfo = getDeviceDetails()
        LazyColumn {
            items(deviceInfo.entries.toList()) { (key, value) ->
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("$key: ", fontWeight = FontWeight.Bold)
                    Text(value)
                }
            }
        }
    }
}

private fun getDeviceDetails(): Map<String, String> {
    return mapOf(
        "Model" to Build.MODEL, "Manufacturer" to Build.MANUFACTURER, "Brand" to Build.BRAND,
        "Device" to Build.DEVICE, "Product" to Build.PRODUCT, "Hardware" to Build.HARDWARE,
        "Android Version" to Build.VERSION.RELEASE, "API Level" to Build.VERSION.SDK_INT.toString(),
        "Build ID" to Build.ID,
    )
}
