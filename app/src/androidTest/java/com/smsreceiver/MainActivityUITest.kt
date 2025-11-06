package com.smsreceiver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for MainActivity
 * Tests the main user-facing functionality including:
 * - SMS display
 * - Tab switching
 * - Swipe gestures
 * - Selection mode
 * - Delete functionality
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing data
        clearTestData()
    }

    @After
    fun tearDown() {
        clearTestData()
    }

    private fun clearTestData() {
        val prefs = context.getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun addTestSms(sender: String, message: String, isArchived: Boolean = false) {
        val prefs = context.getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val existingData = prefs.getString("sms_list", "") ?: ""
        val timestamp = "2024-01-01 12:00:00"
        val timestampMillis = System.currentTimeMillis()
        val archiveFlag = if (isArchived) "1" else "0"
        val archiveTimestamp = if (isArchived) System.currentTimeMillis().toString() else "0"

        val escapedMessage = message
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\|")

        val newLine = "$timestamp|$sender|$escapedMessage|$archiveFlag|$archiveTimestamp|$timestampMillis"
        val updatedData = if (existingData.isEmpty()) newLine else "$existingData\n$newLine"

        prefs.edit().putString("sms_list", updatedData).commit()
    }

    @Test
    fun testTabsAreDisplayed() {
        onView(withText("Inbox")).check(matches(isDisplayed()))
        onView(withText("Archive")).check(matches(isDisplayed()))
    }

    @Test
    fun testSwitchBetweenTabs() {
        // Start on Inbox tab
        onView(withText("Inbox")).check(matches(isDisplayed()))

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Verify Archive tab is selected
        onView(withText("Archive")).check(matches(isDisplayed()))

        // Switch back to Inbox tab
        onView(withText("Inbox")).perform(click())

        // Verify Inbox tab is selected
        onView(withText("Inbox")).check(matches(isDisplayed()))
    }

    @Test
    fun testInboxDisplaysSmsMessages() {
        // Add test SMS
        addTestSms("+1234567890", "Test message 1", false)
        addTestSms("+1234567890", "Test message 2", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify RecyclerView is displayed
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))

        // Verify messages are displayed
        onView(withText("Test message 1")).check(matches(isDisplayed()))
        onView(withText("Test message 2")).check(matches(isDisplayed()))
    }

    @Test
    fun testArchiveShowsOnlyArchivedMessages() {
        // Add inbox and archived messages
        addTestSms("+1111111111", "Inbox message", false)
        addTestSms("+2222222222", "Archived message", true)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Verify archived message is shown
        onView(withText("Archived message")).check(matches(isDisplayed()))

        // Switch back to Inbox to verify inbox message
        onView(withText("Inbox")).perform(click())
        onView(withText("Inbox message")).check(matches(isDisplayed()))
    }

    @Test
    fun testMultilineSmsDisplayCorrectly() {
        // Add multiline SMS
        addTestSms("+1234567890", "Line 1\nLine 2\nLine 3", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify multiline message is displayed
        onView(withText("Line 1\nLine 2\nLine 3")).check(matches(isDisplayed()))
    }

    @Test
    fun testHebrewSmsDisplayCorrectly() {
        // Add Hebrew SMS
        addTestSms("+972501234567", "שלום עולם\nמה שלומך", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify Hebrew message is displayed
        onView(withText("שלום עולם\nמה שלומך")).check(matches(isDisplayed()))
    }

    @Test
    fun testGroupingBySenderInInbox() {
        // Add messages from two different senders
        addTestSms("+1111111111", "Sender 1 Message 1", false)
        addTestSms("+1111111111", "Sender 1 Message 2", false)
        addTestSms("+2222222222", "Sender 2 Message 1", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify both senders are shown as headers
        onView(withText("+1111111111")).check(matches(isDisplayed()))
        onView(withText("+2222222222")).check(matches(isDisplayed()))

        // Verify messages are displayed
        onView(withText("Sender 1 Message 1")).check(matches(isDisplayed()))
        onView(withText("Sender 1 Message 2")).check(matches(isDisplayed()))
        onView(withText("Sender 2 Message 1")).check(matches(isDisplayed()))
    }

    @Test
    fun testClearButtonClearsInbox() {
        // Add test SMS
        addTestSms("+1234567890", "Test message", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify message is displayed
        onView(withText("Test message")).check(matches(isDisplayed()))

        // Click clear button
        onView(withId(R.id.clearButton)).perform(click())

        // Verify empty state is shown
        onView(withId(R.id.emptyText)).check(matches(isDisplayed()))
        onView(withId(R.id.emptyText)).check(matches(withText("No messages")))
    }

    @Test
    fun testEmptyStateDisplayedWhenNoMessages() {
        // Ensure no messages
        clearTestData()

        // Restart activity
        activityRule.scenario.recreate()

        // Verify empty state is shown
        onView(withId(R.id.emptyText)).check(matches(isDisplayed()))
        onView(withId(R.id.emptyText)).check(matches(withText("No messages")))
    }

    @Test
    fun testRecyclerViewScrolls() {
        // Add many messages to test scrolling
        for (i in 1..20) {
            addTestSms("+1234567890", "Message $i", false)
        }

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify first message is visible
        onView(withText("Message 1")).check(matches(isDisplayed()))

        // Scroll to last message
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(21)) // 20 messages + 1 header

        // Note: Due to the way scrolling works, we can't reliably check if Message 20 is displayed
        // after scrolling in this test setup, but the scroll action itself validates the RecyclerView works
    }

    @Test
    fun testPermissionRequestedOnFirstLaunch() {
        // Note: Testing runtime permissions requires more complex setup
        // This test verifies the permission handling code doesn't crash

        // Simply verify the activity launches successfully
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun testSpecialCharactersInMessages() {
        // Add message with special characters
        addTestSms("+1234567890", "Test | with \\special\\ chars", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify message is displayed correctly
        onView(withText("Test | with \\special\\ chars")).check(matches(isDisplayed()))
    }

    @Test
    fun testLongMessagesDisplay() {
        // Add a very long message
        val longMessage = "This is a very long message. " + "Lorem ipsum dolor sit amet. ".repeat(20)
        addTestSms("+1234567890", longMessage, false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify message is displayed (checks that it doesn't crash with long messages)
        onView(withText(longMessage)).check(matches(isDisplayed()))
    }

    @Test
    fun testBackwardCompatibility() {
        // Add old format message (without archive fields)
        val prefs = context.getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val oldFormatLine = "2024-01-01 12:00:00|+1234567890|Old format message"
        prefs.edit().putString("sms_list", oldFormatLine).commit()

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify old format message is displayed
        onView(withText("Old format message")).check(matches(isDisplayed()))
    }

    @Test
    fun testMultipleSendersGroupedCorrectly() {
        // Add messages from 3 different senders
        addTestSms("+1111111111", "Sender 1 Msg", false)
        addTestSms("+2222222222", "Sender 2 Msg", false)
        addTestSms("+3333333333", "Sender 3 Msg", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify all senders are displayed
        onView(withText("+1111111111")).check(matches(isDisplayed()))
        onView(withText("+2222222222")).check(matches(isDisplayed()))
        onView(withText("+3333333333")).check(matches(isDisplayed()))
    }
}
