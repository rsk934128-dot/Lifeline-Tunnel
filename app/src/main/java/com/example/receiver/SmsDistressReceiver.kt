package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.service.SmsTunnelManager

class SmsDistressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""
                
                if (body.contains(SmsTunnelManager.SMS_PREFIX) || body.contains("LIFELINE://SOS") || body.contains(SmsTunnelManager.ACK_PREFIX)) {
                    SmsTunnelManager.handleIncomingSms(context, sender, body)
                }
            }
        }
    }
}
