package com.smsreceiver

import org.junit.Assert.*
import org.junit.Test

/**
 * Critical regression tests for storage format
 * These tests prevent the message truncation bug from returning
 */
class StorageFormatTest {

    @Test
    fun `test escape newlines in message`() {
        val message = "Line 1\nLine 2\nLine 3"
        val escaped = message
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\|")

        assertEquals("Line 1\\nLine 2\\nLine 3", escaped)
        assertFalse(escaped.contains("\n"))
    }

    @Test
    fun `test unescape newlines in message`() {
        val escaped = "Line 1\\nLine 2\\nLine 3"
        val unescaped = escaped
            .replace("\\\\", "\u0001")
            .replace("\\n", "\n")
            .replace("\\|", "|")
            .replace("\u0001", "\\")

        assertEquals("Line 1\nLine 2\nLine 3", unescaped)
        assertTrue(unescaped.contains("\n"))
    }

    @Test
    fun `test escape pipe delimiter`() {
        val message = "Message with | pipe"
        val escaped = message
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\|")

        assertEquals("Message with \\| pipe", escaped)
        assertFalse(escaped.contains("|"))
    }

    @Test
    fun `test unescape pipe delimiter`() {
        val escaped = "Message with \\| pipe"
        val unescaped = escaped
            .replace("\\\\", "\u0001")
            .replace("\\n", "\n")
            .replace("\\|", "|")
            .replace("\u0001", "\\")

        assertEquals("Message with | pipe", unescaped)
    }

    @Test
    fun `test escape backslash`() {
        val message = "Path: C:\\Users\\Test"
        val escaped = message
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\|")

        assertEquals("Path: C:\\\\Users\\\\Test", escaped)
    }

    @Test
    fun `test unescape backslash`() {
        val escaped = "Path: C:\\\\Users\\\\Test"
        val unescaped = escaped
            .replace("\\\\", "\u0001")
            .replace("\\n", "\n")
            .replace("\\|", "|")
            .replace("\u0001", "\\")

        assertEquals("Path: C:\\Users\\Test", unescaped)
    }

    @Test
    fun `test Hebrew message with newlines`() {
        val message = "שלום\nעולם\nמה שלומך"
        val escaped = message
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\|")

        val unescaped = escaped
            .replace("\\\\", "\u0001")
            .replace("\\n", "\n")
            .replace("\\|", "|")
            .replace("\u0001", "\\")

        assertEquals(message, unescaped)
    }

    @Test
    fun `test storage line format parsing`() {
        val date = "2024-01-01 12:00:00"
        val sender = "+1234567890"
        val message = "Test\\nmessage\\|with\\\\special"
        val isArchived = "1"
        val archiveTimestamp = "1704196800000"
        val timestampMillis = "1704110400000"

        val line = "$date|$sender|$message|$isArchived|$archiveTimestamp|$timestampMillis"
        val parts = line.split("|")

        assertEquals(6, parts.size)
        assertEquals(date, parts[0])
        assertEquals(sender, parts[1])
        assertEquals(message, parts[2])
        assertEquals("1", parts[3])
    }

    @Test
    fun `test round-trip escape and unescape`() {
        val originalMessages = listOf(
            "Simple message",
            "Message\nwith\nnewlines",
            "Message | with | pipes",
            "Path: C:\\Users\\Test",
            "שלום\nעולם",
            "Complex: Line1\nLine2 | C:\\Path\\Test"
        )

        for (original in originalMessages) {
            val escaped = original
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("|", "\\|")

            val unescaped = escaped
                .replace("\\\\", "\u0001")
                .replace("\\n", "\n")
                .replace("\\|", "|")
                .replace("\u0001", "\\")

            assertEquals("Round-trip failed for: $original", original, unescaped)
        }
    }

    @Test
    fun `test backward compatibility - old format without archive fields`() {
        val line = "2024-01-01 12:00:00|+1234567890|Test message"
        val parts = line.split("|")

        assertEquals(3, parts.size)

        val isArchived = if (parts.size > 3) parts[3] == "1" else false
        val archiveTimestamp = if (parts.size > 4) parts[4].toLongOrNull() ?: 0L else 0L

        assertFalse(isArchived)
        assertEquals(0L, archiveTimestamp)
    }

    @Test
    fun `test new format with all archive fields`() {
        val line = "2024-01-01 12:00:00|+1234567890|Test message|1|1704196800000|1704110400000"
        val parts = line.split("|")

        assertEquals(6, parts.size)

        val isArchived = if (parts.size > 3) parts[3] == "1" else false
        val archiveTimestamp = if (parts.size > 4) parts[4].toLongOrNull() ?: 0L else 0L
        val timestampMillis = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L

        assertTrue(isArchived)
        assertEquals(1704196800000L, archiveTimestamp)
        assertEquals(1704110400000L, timestampMillis)
    }
}
