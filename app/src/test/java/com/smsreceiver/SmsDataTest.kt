package com.smsreceiver

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SmsData model
 * Tests data integrity and archive functionality
 */
class SmsDataTest {

    @Test
    fun `test SmsData creation with default values`() {
        val sms = SmsData(
            sender = "+1234567890",
            message = "Test message",
            timestamp = "2024-01-01 12:00:00"
        )

        assertEquals("+1234567890", sms.sender)
        assertEquals("Test message", sms.message)
        assertEquals("2024-01-01 12:00:00", sms.timestamp)
        assertEquals(0L, sms.timestampMillis)
        assertFalse(sms.isArchived)
        assertEquals(0L, sms.archiveTimestamp)
    }

    @Test
    fun `test SmsData creation with all values`() {
        val sms = SmsData(
            sender = "+1234567890",
            message = "Test message",
            timestamp = "2024-01-01 12:00:00",
            timestampMillis = 1704110400000L,
            isArchived = true,
            archiveTimestamp = 1704196800000L
        )

        assertTrue(sms.isArchived)
        assertEquals(1704110400000L, sms.timestampMillis)
        assertEquals(1704196800000L, sms.archiveTimestamp)
    }

    @Test
    fun `test SmsData with Hebrew message`() {
        val sms = SmsData(
            sender = "+972501234567",
            message = "שלום עולם",
            timestamp = "2024-01-01 12:00:00"
        )

        assertEquals("שלום עולם", sms.message)
        assertEquals("+972501234567", sms.sender)
    }

    @Test
    fun `test SmsData with multiline message`() {
        val message = "Line 1\nLine 2\nLine 3"
        val sms = SmsData(
            sender = "+1234567890",
            message = message,
            timestamp = "2024-01-01 12:00:00"
        )

        assertEquals("Line 1\nLine 2\nLine 3", sms.message)
        assertTrue(sms.message.contains("\n"))
    }

    @Test
    fun `test SmsData archive state mutation`() {
        val sms = SmsData(
            sender = "+1234567890",
            message = "Test",
            timestamp = "2024-01-01 12:00:00"
        )

        assertFalse(sms.isArchived)

        sms.isArchived = true
        sms.archiveTimestamp = System.currentTimeMillis()

        assertTrue(sms.isArchived)
        assertTrue(sms.archiveTimestamp > 0)
    }
}
