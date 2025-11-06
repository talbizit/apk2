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
        Log.d(TAG, "onReceive called! Action: ${intent?.action}")

        if (context == null || intent == null) {
            Log.e(TAG, "Context or intent is null")
            return
        }

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "SMS_RECEIVED_ACTION detected")
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            // Group messages by sender to handle multi-part SMS
            val messagesBySender = mutableMapOf<String, MutableList<SmsMessage>>()
            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress
                if (!messagesBySender.containsKey(sender)) {
                    messagesBySender[sender] = mutableListOf()
                }
                messagesBySender[sender]?.add(smsMessage)
            }

            // Process each sender's messages
            for ((sender, senderMessages) in messagesBySender) {
                // Concatenate all parts into a single message
                val fullMessageBody = StringBuilder()
                var timestamp = System.currentTimeMillis()

                for (smsMessage in senderMessages) {
                    fullMessageBody.append(smsMessage.messageBody)
                    timestamp = smsMessage.timestampMillis // Use the last part's timestamp
                }

                val completeMessage = fullMessageBody.toString()

                Log.d(TAG, "SMS received from: $sender")
                Log.d(TAG, "Complete message (${senderMessages.size} parts): $completeMessage")

                // Store the complete SMS
                saveSms(context, sender, completeMessage, timestamp)

                // Broadcast the complete message to the app
                val broadcastIntent = Intent(SMS_RECEIVED_ACTION).apply {
                    putExtra(EXTRA_SENDER, sender)
                    putExtra(EXTRA_MESSAGE, completeMessage)
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

        // Escape special characters to prevent parsing issues
        val escapedMessage = message
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\n", "\\n")    // Escape newlines
            .replace("|", "\\|")     // Escape pipe delimiter

        val newEntry = "$date|$sender|$escapedMessage\n"
        val updatedData = newEntry + existingData

        // Keep only last 100 messages to avoid storage issues
        val lines = updatedData.split("\n")
        val limitedData = lines.take(100).joinToString("\n")

        prefs.edit().putString("sms_list", limitedData).apply()
    }
}
