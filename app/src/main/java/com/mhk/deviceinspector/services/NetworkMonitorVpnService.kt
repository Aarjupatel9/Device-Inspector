/*
 * This file contains the implementation for the local VpnService.
 * Location: app/src/main/java/com/mhk/deviceinspector/services/NetworkMonitorVpnService.kt
 */
package com.mhk.deviceinspector.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class NetworkMonitorVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var vpnThread: Thread

    companion object {
        const val ACTION_START = "com.mhk.deviceinspector.ACTION_START"
        const val ACTION_STOP = "com.mhk.deviceinspector.ACTION_STOP"

        const val BROADCAST_ACTION_CONNECTION = "com.mhk.deviceinspector.CONNECTION_EVENT"
        const val EXTRA_CONNECTION_INFO = "EXTRA_CONNECTION_INFO"

        var isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startVpn()
                }
            }
            ACTION_STOP -> {
                stopVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        isRunning = true
        val builder = Builder()
        builder.setSession("DeviceInspectorVpn")
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        vpnInterface = builder.establish()

        vpnThread = thread {
            val fileInputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            val fileOutputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
            val packet = ByteBuffer.allocate(32767)

            while (isRunning) {
                try {
                    val length = fileInputStream.read(packet.array())
                    if (length > 0) {
                        packet.limit(length)
                        parsePacket(packet)
                        // In a real firewall, you would write the packet to the output stream.
                        // For monitoring, we just analyze and drop it.
                        packet.clear()
                    }
                } catch (e: Exception) {
                    // Handle exceptions
                }
            }
        }
    }

    private fun parsePacket(packet: ByteBuffer) {
        val ipVersion = packet.get().toInt() shr 4
        if (ipVersion != 4) return // Only handle IPv4 for simplicity

        packet.position(0)
        val headerLength = (packet.get(0).toInt() and 0x0F) * 4
        val protocol = packet.get(9).toInt() and 0xFF
        val sourceAddress = InetAddress.getByAddress(ByteArray(4).apply { packet.position(12); packet.get(this) })
        val destAddress = InetAddress.getByAddress(ByteArray(4).apply { packet.position(16); packet.get(this) })

        // This is a simplification. A real implementation would need to look up the app
        // associated with the source port, which is complex.
        val connectionInfo = "Proto: $protocol, Src: ${sourceAddress.hostAddress}, Dst: ${destAddress.hostAddress}"

        // Broadcast the connection info to the UI
        val intent = Intent(BROADCAST_ACTION_CONNECTION).apply {
            putExtra(EXTRA_CONNECTION_INFO, connectionInfo)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun stopVpn() {
        isRunning = false
        if (::vpnThread.isInitialized) {
            vpnThread.interrupt()
        }
        vpnInterface?.close()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
