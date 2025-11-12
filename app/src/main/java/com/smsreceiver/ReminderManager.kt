package com.smsreceiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sms_reminder_channel"
        const val NOTIFICATION_ID_BASE = 1000
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Reminders"
            val descriptionText = "Reminder notifications for SMS messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)

                // Set custom sound
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(sms: SmsData, reminderTimeMillis: Long) {
        val currentTime = System.currentTimeMillis()
        val delay = reminderTimeMillis - currentTime

        if (delay <= 0) {
            // Show notification immediately
            showNotification(sms)
            return
        }

        // Schedule work
        val workData = Data.Builder()
            .putString(EXTRA_SENDER, sms.sender)
            .putString(EXTRA_MESSAGE, sms.message)
            .putString(EXTRA_TIMESTAMP, sms.timestamp)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .addTag("reminder_${sms.sender}_${sms.timestampMillis}")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelReminder(sms: SmsData) {
        val tag = "reminder_${sms.sender}_${sms.timestampMillis}"
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }

    fun showNotification(sms: SmsData) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SMS Reminder: ${sms.sender}")
            .setContentText(sms.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BASE + sms.timestampMillis.toInt(), notification)
    }
}
