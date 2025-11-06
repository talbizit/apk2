package com.smsreceiver

data class SmsData(
    val sender: String,
    val message: String,
    val timestamp: String,
    val timestampMillis: Long = 0L,
    var isArchived: Boolean = false,
    var archiveTimestamp: Long = 0L
)
