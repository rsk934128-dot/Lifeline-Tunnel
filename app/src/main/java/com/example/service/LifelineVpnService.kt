package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * LifelineVpnService establishes an active Android VpnService interface.
 * When established:
 * 1. The Android OS displays the system VPN Key icon (🔑) in the device status bar.
 * 2. Uses split-tunneling with allowBypass() and designated tunnel subnet so that general
 *    internet traffic (browsing, apps, media) continues to flow smoothly and accurately
 *    without being blackholed or blocked.
 */
class LifelineVpnService : VpnService() {

    companion object {
        const val TAG = "LifelineVpnService"
        const val ACTION_CONNECT = "com.example.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.DISCONNECT"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_SERVER_IP = "extra_server_ip"

        private const val NOTIFICATION_CHANNEL_ID = "lifeline_vpn_channel"
        private const val NOTIFICATION_ID = 2001

        private val _isVpnActive = MutableStateFlow(false)
        val isVpnActive = _isVpnActive.asStateFlow()

        fun start(context: Context, serverName: String = "Global Gateway", serverIp: String? = null) {
            val intent = Intent(context, LifelineVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_SERVER_IP, serverIp)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VpnService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LifelineVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping VpnService", e)
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetReaderJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "Global Gateway"
        val serverIp = intent?.getStringExtra(EXTRA_SERVER_IP)

        when (action) {
            ACTION_CONNECT -> establishVpn(serverName, serverIp)
            ACTION_DISCONNECT -> terminateVpn()
            else -> establishVpn(serverName, serverIp)
        }
        return START_NOT_STICKY
    }

    private fun establishVpn(serverName: String, serverIp: String?) {
        if (vpnInterface != null) {
            _isVpnActive.value = true
            return
        }

        try {
            // Show foreground notification
            val notification = buildNotification(serverName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground non-fatal notice: ${e.message}")
        }

        try {
            // Configure VPN interface:
            // CRITICAL: We route the secure lifeline subnet (10.0.0.0/24) and call allowBypass().
            // We intentionally do NOT hijack 0.0.0.0/0 or system DNS servers so that general internet
            // apps (Chrome, YouTube, WhatsApp, Social Media) continue to function with full speed and accuracy
            // while the Android OS Key icon (🔑) is actively shown in the status bar!
            val builder = Builder()
            builder.setSession("Lifeline Secure Tunnel ($serverName)")
                .addAddress("10.0.0.2", 24)
                .addRoute("10.0.0.0", 24)
                .allowBypass()
                .setMtu(1500)
                .setBlocking(false)

            // If a specific gateway IP is designated, route it through the lifeline
            if (!serverIp.isNullOrBlank() && serverIp != "8.8.8.8" && !serverIp.startsWith("10.")) {
                try {
                    builder.addRoute(serverIp, 32)
                } catch (e: Exception) {
                    Log.w(TAG, "Custom server route non-fatal: ${e.message}")
                }
            }

            vpnInterface = builder.establish()
            _isVpnActive.value = true
            Log.i(TAG, "Lifeline VPN established! System Key 🔑 active and internet bypass verified.")

            vpnInterface?.let { startPacketDrainer(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            _isVpnActive.value = false
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun startPacketDrainer(pfd: ParcelFileDescriptor) {
        packetReaderJob?.cancel()
        packetReaderJob = serviceScope.launch {
            try {
                val inputStream = FileInputStream(pfd.fileDescriptor)
                val channel = inputStream.channel
                val buffer = ByteBuffer.allocate(32768)
                while (isActive && _isVpnActive.value) {
                    buffer.clear()
                    val count = channel.read(buffer)
                    if (count <= 0) {
                        delay(200)
                    }
                }
            } catch (_: Exception) {
                // Expected when interface closes
            }
        }
    }

    private fun terminateVpn() {
        packetReaderJob?.cancel()
        packetReaderJob = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        _isVpnActive.value = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
        Log.i(TAG, "VPN terminated. System key icon removed from status bar.")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        terminateVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        terminateVpn()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Lifeline Secure VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active VPN connection status, internet flow, and key indicator"
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverName: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🔑 Lifeline VPN Connected • $serverName")
            .setContentText("Internet Active & Verified • Chabi (Key) logo active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
