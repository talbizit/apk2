package com.smsreceiver

data class SmsData(
    val sender: String,
    val message: String,
    val timestamp: String,
    val timestampMillis: Long = 0L,
    var isArchived: Boolean = false,
    var archiveTimestamp: Long = 0L,
    var tags: MutableSet<String> = mutableSetOf(), // Tags: "saved", "receipts", "archived" - messages can have multiple tags
    var isRead: Boolean = false // Track read/unread status
)
