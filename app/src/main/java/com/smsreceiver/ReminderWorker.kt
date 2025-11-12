package com.smsreceiver

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val sender = inputData.getString(ReminderManager.EXTRA_SENDER) ?: return Result.failure()
        val message = inputData.getString(ReminderManager.EXTRA_MESSAGE) ?: return Result.failure()
        val timestamp = inputData.getString(ReminderManager.EXTRA_TIMESTAMP) ?: return Result.failure()

        val sms = SmsData(
            sender = sender,
            message = message,
            timestamp = timestamp
        )

        val reminderManager = ReminderManager(context)
        reminderManager.showNotification(sms)

        return Result.success()
    }
}
