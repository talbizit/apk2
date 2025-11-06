package com.smsreceiver

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for archive time categorization
 * Ensures messages are correctly categorized by age
 */
class ArchiveCategorizationTest {

    private fun daysSince(timestamp: Long, now: Long): Long {
        return TimeUnit.MILLISECONDS.toDays(now - timestamp)
    }

    @Test
    fun `test last 7 days category`() {
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - TimeUnit.DAYS.toMillis(3)

        val days = daysSince(threeDaysAgo, now)

        assertTrue(days <= 7)
        assertEquals(3L, days)
    }

    @Test
    fun `test last month category`() {
        val now = System.currentTimeMillis()
        val fifteenDaysAgo = now - TimeUnit.DAYS.toMillis(15)

        val days = daysSince(fifteenDaysAgo, now)

        assertTrue(days in 8..30)
        assertEquals(15L, days)
    }

    @Test
    fun `test last quarter category`() {
        val now = System.currentTimeMillis()
        val sixtyDaysAgo = now - TimeUnit.DAYS.toMillis(60)

        val days = daysSince(sixtyDaysAgo, now)

        assertTrue(days in 31..90)
        assertEquals(60L, days)
    }

    @Test
    fun `test last year category`() {
        val now = System.currentTimeMillis()
        val oneHundredDaysAgo = now - TimeUnit.DAYS.toMillis(100)

        val days = daysSince(oneHundredDaysAgo, now)

        assertTrue(days in 91..365)
        assertEquals(100L, days)
    }

    @Test
    fun `test older category`() {
        val now = System.currentTimeMillis()
        val fourHundredDaysAgo = now - TimeUnit.DAYS.toMillis(400)

        val days = daysSince(fourHundredDaysAgo, now)

        assertTrue(days > 365)
        assertEquals(400L, days)
    }

    @Test
    fun `test categorize multiple messages`() {
        val now = System.currentTimeMillis()

        val messages = listOf(
            SmsData("+1", "Recent", "2024", isArchived = true, archiveTimestamp = now - TimeUnit.DAYS.toMillis(3)),
            SmsData("+2", "Month", "2024", isArchived = true, archiveTimestamp = now - TimeUnit.DAYS.toMillis(15)),
            SmsData("+3", "Quarter", "2024", isArchived = true, archiveTimestamp = now - TimeUnit.DAYS.toMillis(60)),
            SmsData("+4", "Year", "2024", isArchived = true, archiveTimestamp = now - TimeUnit.DAYS.toMillis(200)),
            SmsData("+5", "Old", "2024", isArchived = true, archiveTimestamp = now - TimeUnit.DAYS.toMillis(400))
        )

        val last7Days = messages.filter { daysSince(it.archiveTimestamp, now) <= 7 }
        val lastMonth = messages.filter { daysSince(it.archiveTimestamp, now) in 8..30 }
        val lastQuarter = messages.filter { daysSince(it.archiveTimestamp, now) in 31..90 }
        val lastYear = messages.filter { daysSince(it.archiveTimestamp, now) in 91..365 }
        val older = messages.filter { daysSince(it.archiveTimestamp, now) > 365 }

        assertEquals(1, last7Days.size)
        assertEquals(1, lastMonth.size)
        assertEquals(1, lastQuarter.size)
        assertEquals(1, lastYear.size)
        assertEquals(1, older.size)

        assertEquals("Recent", last7Days[0].message)
        assertEquals("Month", lastMonth[0].message)
        assertEquals("Quarter", lastQuarter[0].message)
        assertEquals("Year", lastYear[0].message)
        assertEquals("Old", older[0].message)
    }

    @Test
    fun `test boundary conditions - exactly 7 days`() {
        val now = System.currentTimeMillis()
        val exactlySevenDays = now - TimeUnit.DAYS.toMillis(7)

        val days = daysSince(exactlySevenDays, now)

        assertEquals(7L, days)
        assertTrue(days <= 7) // Should be in "Last 7 Days"
    }

    @Test
    fun `test boundary conditions - exactly 8 days`() {
        val now = System.currentTimeMillis()
        val exactlyEightDays = now - TimeUnit.DAYS.toMillis(8)

        val days = daysSince(exactlyEightDays, now)

        assertEquals(8L, days)
        assertTrue(days in 8..30) // Should be in "Last Month"
    }

    @Test
    fun `test boundary conditions - exactly 30 days`() {
        val now = System.currentTimeMillis()
        val exactlyThirtyDays = now - TimeUnit.DAYS.toMillis(30)

        val days = daysSince(exactlyThirtyDays, now)

        assertEquals(30L, days)
        assertTrue(days in 8..30) // Should be in "Last Month"
    }

    @Test
    fun `test boundary conditions - exactly 365 days`() {
        val now = System.currentTimeMillis()
        val exactlyOneYear = now - TimeUnit.DAYS.toMillis(365)

        val days = daysSince(exactlyOneYear, now)

        assertEquals(365L, days)
        assertTrue(days in 91..365) // Should be in "Last Year"
    }
}
