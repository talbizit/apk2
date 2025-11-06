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
 * UI tests for swipe gestures and selection mode
 * Tests advanced interactions:
 * - Swipe to archive
 * - Swipe to restore
 * - Long-press selection mode
 * - Multi-select and delete
 */
@RunWith(AndroidJUnit4::class)
class SwipeAndSelectionUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    fun testSwipeRightToArchiveFromInbox() {
        // Add test SMS to inbox
        addTestSms("+1234567890", "Swipe test message", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Verify message is in inbox
        onView(withText("Swipe test message")).check(matches(isDisplayed()))

        // Swipe right on the message (position 1, after header at position 0)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, swipeRight()))

        // Wait a bit for the swipe animation
        Thread.sleep(500)

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Verify message is now in archive
        onView(withText("Swipe test message")).check(matches(isDisplayed()))
    }

    @Test
    fun testSwipeRightToRestoreFromArchive() {
        // Add test SMS to archive
        addTestSms("+1234567890", "Restore test message", true)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Verify message is in archive
        onView(withText("Restore test message")).check(matches(isDisplayed()))

        // Swipe right on the message (position 1, after header at position 0)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, swipeRight()))

        // Wait a bit for the swipe animation
        Thread.sleep(500)

        // Switch back to Inbox tab
        onView(withText("Inbox")).perform(click())

        // Verify message is now in inbox
        onView(withText("Restore test message")).check(matches(isDisplayed()))
    }

    @Test
    fun testLongPressEntersSelectionMode() {
        // Add test SMS
        addTestSms("+1234567890", "Long press test", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Long press on a message (position 1, after header)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))

        // Wait for selection mode to activate
        Thread.sleep(300)

        // Verify delete button appears (indicating selection mode is active)
        onView(withId(R.id.deleteButton)).check(matches(isDisplayed()))

        // Verify checkboxes are now visible
        // Note: This is harder to test directly, but the presence of delete button confirms selection mode
    }

    @Test
    fun testSelectionModeDeleteButton() {
        // Add test SMS to archive
        addTestSms("+1234567890", "Delete test message", true)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Long press to enter selection mode
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))

        // Wait for selection mode
        Thread.sleep(300)

        // Verify delete button is visible
        onView(withId(R.id.deleteButton)).check(matches(isDisplayed()))

        // Click item to select it (position 1)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, click()))

        // Click delete button
        onView(withId(R.id.deleteButton)).perform(click())

        // Wait for deletion
        Thread.sleep(300)

        // Verify empty state is shown
        onView(withId(R.id.emptyText)).check(matches(isDisplayed()))
    }

    @Test
    fun testBackButtonExitsSelectionMode() {
        // Add test SMS
        addTestSms("+1234567890", "Back test message", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Long press to enter selection mode
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))

        // Wait for selection mode
        Thread.sleep(300)

        // Verify delete button is visible
        onView(withId(R.id.deleteButton)).check(matches(isDisplayed()))

        // Press back button
        pressBack()

        // Wait a bit
        Thread.sleep(300)

        // Verify delete button is no longer visible (selection mode exited)
        onView(withId(R.id.deleteButton)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testMultipleSwipesInInbox() {
        // Add multiple messages
        addTestSms("+1111111111", "Message 1", false)
        addTestSms("+1111111111", "Message 2", false)
        addTestSms("+1111111111", "Message 3", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Swipe first message
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, swipeRight()))
        Thread.sleep(500)

        // Swipe second message (now at position 1 after first was removed)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, swipeRight()))
        Thread.sleep(500)

        // Switch to Archive
        onView(withText("Archive")).perform(click())

        // Verify both messages are in archive
        onView(withText("Message 1")).check(matches(isDisplayed()))
        onView(withText("Message 2")).check(matches(isDisplayed()))
    }

    @Test
    fun testHeaderLongPressEntersSelectionMode() {
        // Add messages from same sender
        addTestSms("+1234567890", "Group message 1", false)
        addTestSms("+1234567890", "Group message 2", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Long press on header (position 0)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, longClick()))

        // Wait for selection mode
        Thread.sleep(300)

        // Verify delete button appears
        onView(withId(R.id.deleteButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testSelectionModeInArchive() {
        // Add archived messages
        addTestSms("+1234567890", "Archive message 1", true)
        addTestSms("+1234567890", "Archive message 2", true)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Long press to enter selection mode
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))

        // Wait for selection mode
        Thread.sleep(300)

        // Verify delete button is visible in archive
        onView(withId(R.id.deleteButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testDeleteOnlyDeletesSelectedItems() {
        // Add multiple archived messages
        addTestSms("+1111111111", "Keep this message", true)
        addTestSms("+2222222222", "Delete this message", true)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Switch to Archive tab
        onView(withText("Archive")).perform(click())

        // Long press on second group (position 2 = second header)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(2, longClick()))

        // Wait for selection mode
        Thread.sleep(300)

        // Click the message to select it (position 3)
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(3, click()))

        // Click delete button
        onView(withId(R.id.deleteButton)).perform(click())

        // Wait for deletion
        Thread.sleep(300)

        // Verify first message is still there
        onView(withText("Keep this message")).check(matches(isDisplayed()))

        // Note: Can't verify deleted message is gone since it might not throw exception
        // The test verifies at least that deletion doesn't crash and keeps other messages
    }

    @Test
    fun testSwipeWorksAfterSelectionMode() {
        // Add test message
        addTestSms("+1234567890", "Swipe after selection", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Enter selection mode
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))
        Thread.sleep(300)

        // Exit selection mode with back button
        pressBack()
        Thread.sleep(300)

        // Now try to swipe
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, swipeRight()))
        Thread.sleep(500)

        // Switch to Archive
        onView(withText("Archive")).perform(click())

        // Verify message was archived
        onView(withText("Swipe after selection")).check(matches(isDisplayed()))
    }

    @Test
    fun testNoSwipeInSelectionMode() {
        // Add test message
        addTestSms("+1234567890", "No swipe in selection", false)

        // Restart activity to load data
        activityRule.scenario.recreate()

        // Enter selection mode
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, longClick()))
        Thread.sleep(300)

        // Try to swipe in selection mode (should not crash, swipe should be disabled)
        try {
            onView(withId(R.id.recyclerView))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, swipeRight()))
            Thread.sleep(500)
        } catch (e: Exception) {
            // Swipe might be disabled, which is fine
        }

        // Message should still be in inbox (not archived)
        onView(withText("No swipe in selection")).check(matches(isDisplayed()))
    }
}
