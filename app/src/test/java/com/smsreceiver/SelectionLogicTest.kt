package com.smsreceiver

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for selection mode and group selection logic
 */
class SelectionLogicTest {

    @Test
    fun `test ListItem Header creation`() {
        val header = ListItem.Header("Test Header", false)

        assertEquals("Test Header", header.title)
        assertFalse(header.isSelected)
    }

    @Test
    fun `test ListItem Message creation`() {
        val sms = SmsData("+123", "Test", "2024-01-01")
        val message = ListItem.Message(sms, false)

        assertEquals(sms, message.sms)
        assertFalse(message.isSelected)
    }

    @Test
    fun `test Header selection toggle`() {
        val header = ListItem.Header("Test", false)

        assertFalse(header.isSelected)

        header.isSelected = true
        assertTrue(header.isSelected)

        header.isSelected = false
        assertFalse(header.isSelected)
    }

    @Test
    fun `test Message selection toggle`() {
        val sms = SmsData("+123", "Test", "2024-01-01")
        val message = ListItem.Message(sms, false)

        assertFalse(message.isSelected)

        message.isSelected = true
        assertTrue(message.isSelected)
    }

    @Test
    fun `test filter selected messages`() {
        val items = listOf(
            ListItem.Header("Group 1"),
            ListItem.Message(SmsData("+1", "Msg1", "2024-01-01"), true),
            ListItem.Message(SmsData("+1", "Msg2", "2024-01-01"), false),
            ListItem.Header("Group 2"),
            ListItem.Message(SmsData("+2", "Msg3", "2024-01-01"), true),
            ListItem.Message(SmsData("+2", "Msg4", "2024-01-01"), false)
        )

        val selectedMessages = items.filterIsInstance<ListItem.Message>()
            .filter { it.isSelected }
            .map { it.sms }

        assertEquals(2, selectedMessages.size)
        assertEquals("Msg1", selectedMessages[0].message)
        assertEquals("Msg3", selectedMessages[1].message)
    }

    @Test
    fun `test count selected messages`() {
        val items = listOf(
            ListItem.Message(SmsData("+1", "1", "2024"), true),
            ListItem.Message(SmsData("+1", "2", "2024"), false),
            ListItem.Message(SmsData("+1", "3", "2024"), true),
            ListItem.Message(SmsData("+1", "4", "2024"), true)
        )

        val count = items.filterIsInstance<ListItem.Message>().count { it.isSelected }

        assertEquals(3, count)
    }

    @Test
    fun `test clear all selections`() {
        val items = mutableListOf(
            ListItem.Header("Group", true),
            ListItem.Message(SmsData("+1", "1", "2024"), true),
            ListItem.Message(SmsData("+1", "2", "2024"), true)
        )

        items.forEach {
            when (it) {
                is ListItem.Header -> it.isSelected = false
                is ListItem.Message -> it.isSelected = false
            }
        }

        assertFalse(items.any { item ->
            when (item) {
                is ListItem.Header -> item.isSelected
                is ListItem.Message -> item.isSelected
            }
        })
    }

    @Test
    fun `test group messages by sender`() {
        val messages = listOf(
            SmsData("+1", "Msg1", "2024-01-01"),
            SmsData("+1", "Msg2", "2024-01-01"),
            SmsData("+2", "Msg3", "2024-01-01"),
            SmsData("+2", "Msg4", "2024-01-01")
        )

        val grouped = messages.groupBy { it.sender }

        assertEquals(2, grouped.size)
        assertEquals(2, grouped["+1"]?.size)
        assertEquals(2, grouped["+2"]?.size)
    }

    @Test
    fun `test inbox filtering - exclude archived`() {
        val allMessages = listOf(
            SmsData("+1", "Inbox1", "2024", isArchived = false),
            SmsData("+1", "Archived1", "2024", isArchived = true),
            SmsData("+2", "Inbox2", "2024", isArchived = false),
            SmsData("+2", "Archived2", "2024", isArchived = true)
        )

        val inboxMessages = allMessages.filter { !it.isArchived }

        assertEquals(2, inboxMessages.size)
        assertEquals("Inbox1", inboxMessages[0].message)
        assertEquals("Inbox2", inboxMessages[1].message)
    }

    @Test
    fun `test archive filtering - only archived`() {
        val allMessages = listOf(
            SmsData("+1", "Inbox1", "2024", isArchived = false),
            SmsData("+1", "Archived1", "2024", isArchived = true),
            SmsData("+2", "Inbox2", "2024", isArchived = false),
            SmsData("+2", "Archived2", "2024", isArchived = true)
        )

        val archivedMessages = allMessages.filter { it.isArchived }

        assertEquals(2, archivedMessages.size)
        assertEquals("Archived1", archivedMessages[0].message)
        assertEquals("Archived2", archivedMessages[1].message)
    }
}
