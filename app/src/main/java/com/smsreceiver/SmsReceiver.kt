package com.smsreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val SMS_RECEIVED_ACTION = "com.smsreceiver.SMS_RECEIVED"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress
                val messageBody = smsMessage.messageBody
                val timestamp = smsMessage.timestampMillis

                Log.d(TAG, "SMS received from: $sender")
                Log.d(TAG, "Message: $messageBody")

                // Store the SMS
                saveSms(context, sender, messageBody, timestamp)

                // Broadcast to the app
                val broadcastIntent = Intent(SMS_RECEIVED_ACTION).apply {
                    putExtra(EXTRA_SENDER, sender)
                    putExtra(EXTRA_MESSAGE, messageBody)
                    putExtra(EXTRA_TIMESTAMP, timestamp)
                }
                context.sendBroadcast(broadcastIntent)
            }
        }
    }

    private fun saveSms(context: Context, sender: String, message: String, timestamp: Long) {
        val prefs = context.getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val existingData = prefs.getString("sms_list", "") ?: ""

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(timestamp))

        val newEntry = "$date|$sender|$message\n"
        val updatedData = newEntry + existingData

        // Keep only last 100 messages to avoid storage issues
        val lines = updatedData.split("\n")
        val limitedData = lines.take(100).joinToString("\n")

        prefs.edit().putString("sms_list", limitedData).apply()
    }
}
