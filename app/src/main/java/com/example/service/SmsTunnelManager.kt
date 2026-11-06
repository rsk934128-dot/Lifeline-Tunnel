package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class IncomingSmsDistressSignal(
    val id: String = UUID.randomUUID().toString(),
    val senderNumber: String,
    val sosType: String,
    val message: String,
    val locationCoords: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false
)

object SmsTunnelManager {
    const val SMS_PREFIX = "LIFELINE_SOS"
    const val ACK_PREFIX = "LIFELINE_ACK"
    private const val CHANNEL_ID = "lifeline_emergency_sms_channel"
    private const val NOTIFICATION_ID = 9999

    private val _activeIncomingSignal = MutableStateFlow<IncomingSmsDistressSignal?>(null)
    val activeIncomingSignal: StateFlow<IncomingSmsDistressSignal?> = _activeIncomingSignal.asStateFlow()

    private val _incomingSignalsHistory = MutableStateFlow<List<IncomingSmsDistressSignal>>(emptyList())
    val incomingSignalsHistory: StateFlow<List<IncomingSmsDistressSignal>> = _incomingSignalsHistory.asStateFlow()

    private val _lastSentStatus = MutableStateFlow<String?>(null)
    val lastSentStatus: StateFlow<String?> = _lastSentStatus.asStateFlow()

    private val _isVibrating = MutableStateFlow(false)
    val isVibrating: StateFlow<Boolean> = _isVibrating.asStateFlow()

    fun handleIncomingSms(context: Context, sender: String, messageBody: String) {
        val trimmed = messageBody.trim()
        if (trimmed.startsWith(SMS_PREFIX) || trimmed.startsWith("LIFELINE://SOS")) {
            // Format: LIFELINE_SOS|<TYPE>|<MESSAGE>|<COORDS>
            val parts = trimmed.split("|")
            val type = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].trim() else "SOS EMERGENCY"
            val note = if (parts.size > 2 && parts[2].isNotBlank()) parts[2].trim() else "Emergency distress signal received"
            val coords = if (parts.size > 3 && parts[3].isNotBlank() && parts[3] != "OFFLINE") parts[3].trim() else null

            val signal = IncomingSmsDistressSignal(
                senderNumber = sender,
                sosType = type,
                message = note,
                locationCoords = coords
            )

            _activeIncomingSignal.value = signal
            _incomingSignalsHistory.value = listOf(signal) + _incomingSignalsHistory.value.take(49)

            triggerEmergencyAlert(context, signal)
        } else if (trimmed.startsWith(ACK_PREFIX)) {
            _lastSentStatus.value = "Acknowledgment from $sender: ${trimmed.removePrefix(ACK_PREFIX).trim('|', ' ')}"
        }
    }

    private fun triggerEmergencyAlert(context: Context, signal: IncomingSmsDistressSignal) {
        try {
            // Trigger vibration alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
            }
            _isVibrating.value = true

            // Trigger high priority notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Lifeline Emergency Signals",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Full-Screen and Heads-up Emergency Incoming Signals"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("OPEN_EMERGENCY_ALARM", true)
                    putExtra("SENDER_NUMBER", signal.senderNumber)
                    putExtra("SOS_TYPE", signal.sosType)
                    putExtra("MESSAGE", signal.message)
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    911,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("🚨 GSM TUNNEL: ${signal.sosType}")
                    .setContentText("${signal.senderNumber}: ${signal.message}")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("Sender: ${signal.senderNumber}\nType: ${signal.sosType}\nMessage: ${signal.message}\nLocation: ${signal.locationCoords ?: "Local Area"}"))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}
    }

    fun dismissActiveSignal(context: Context? = null) {
        _activeIncomingSignal.value = null
        _isVibrating.value = false
        context?.let {
            val notificationManager = it.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
            try {
                @Suppress("DEPRECATION")
                val vibrator = it.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            } catch (_: Exception) {}
        }
    }

    fun sendDistressSms(
        context: Context,
        recipientPhone: String,
        sosType: String,
        message: String,
        coords: String? = null
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            _lastSentStatus.value = "SEND_SMS permission not granted. Requesting permission..."
            return false
        }

        return try {
            val cleanPhone = recipientPhone.replace(" ", "").replace("-", "")
            if (cleanPhone.isBlank()) {
                _lastSentStatus.value = "Phone number is empty"
                return false
            }

            val formattedPayload = "$SMS_PREFIX|$sosType|$message|${coords ?: "OFFLINE"}"

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (formattedPayload.length > 160) {
                val parts = smsManager.divideMessage(formattedPayload)
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, formattedPayload, null, null)
            }

            _lastSentStatus.value = "✓ GSM SOS sent to $cleanPhone ($sosType)"
            true
        } catch (e: Exception) {
            _lastSentStatus.value = "Failed to send SMS: ${e.localizedMessage}"
            false
        }
    }

    fun sendAckSms(context: Context, recipientPhone: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            _lastSentStatus.value = "SEND_SMS permission required"
            return false
        }
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val payload = "$ACK_PREFIX|ACK_RECEIVED|Lifeline Tunnel node acknowledged your SOS."
            smsManager.sendTextMessage(recipientPhone, null, payload, null, null)
            _lastSentStatus.value = "✓ Sent acknowledgment to $recipientPhone"
            true
        } catch (e: Exception) {
            _lastSentStatus.value = "Ack failed: ${e.localizedMessage}"
            false
        }
    }

    fun simulateIncomingDistressSignal(
        context: Context,
        sender: String = "+8801712-990011",
        sosType: String = "CRITICAL MEDICAL",
        message: String = "Patient unconscious, zero mobile internet, need emergency ambulance & saline!",
        coords: String = "23.8103° N, 90.4125° E"
    ) {
        val signal = IncomingSmsDistressSignal(
            senderNumber = sender,
            sosType = sosType,
            message = message,
            locationCoords = coords
        )
        _activeIncomingSignal.value = signal
        _incomingSignalsHistory.value = listOf(signal) + _incomingSignalsHistory.value.take(49)
        triggerEmergencyAlert(context, signal)
    }

    fun clearHistory() {
        _incomingSignalsHistory.value = emptyList()
    }
}
