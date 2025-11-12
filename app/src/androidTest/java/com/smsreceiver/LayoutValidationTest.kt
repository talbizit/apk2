package com.smsreceiver

import android.content.Context
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests to validate XML layouts can be inflated without errors.
 * Catches issues like:
 * - Missing namespace declarations (xmlns:app, xmlns:tools)
 * - Invalid attribute references
 * - Malformed XML structure
 * - Resource reference errors
 */
@RunWith(AndroidJUnit4::class)
class LayoutValidationTest {

    private lateinit var context: Context
    private lateinit var inflater: LayoutInflater

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inflater = LayoutInflater.from(context)
    }

    @Test
    fun testActivityMainLayoutInflates() {
        try {
            inflater.inflate(R.layout.activity_main, null)
        } catch (e: Exception) {
            throw AssertionError("activity_main.xml failed to inflate: ${e.message}", e)
        }
    }

    @Test
    fun testActivitySettingsLayoutInflates() {
        try {
            inflater.inflate(R.layout.activity_settings, null)
        } catch (e: Exception) {
            throw AssertionError("activity_settings.xml failed to inflate: ${e.message}", e)
        }
    }

    @Test
    fun testItemSmsLayoutInflates() {
        try {
            inflater.inflate(R.layout.item_sms, null)
        } catch (e: Exception) {
            throw AssertionError("item_sms.xml failed to inflate: ${e.message}", e)
        }
    }

    @Test
    fun testItemHeaderLayoutInflates() {
        try {
            inflater.inflate(R.layout.item_header, null)
        } catch (e: Exception) {
            throw AssertionError("item_header.xml failed to inflate: ${e.message}", e)
        }
    }

    /**
     * Test that all layouts can be inflated together.
     * This catches cross-layout issues and ensures consistent XML structure.
     */
    @Test
    fun testAllLayoutsInflate() {
        val layouts = listOf(
            R.layout.activity_main,
            R.layout.activity_settings,
            R.layout.item_sms,
            R.layout.item_header
        )

        val errors = mutableListOf<String>()

        for (layoutId in layouts) {
            try {
                inflater.inflate(layoutId, null)
            } catch (e: Exception) {
                val layoutName = context.resources.getResourceEntryName(layoutId)
                errors.add("$layoutName: ${e.message}")
            }
        }

        if (errors.isNotEmpty()) {
            throw AssertionError("${errors.size} layout(s) failed to inflate:\n${errors.joinToString("\n")}")
        }
    }

    /**
     * Test that critical views exist in activity_main
     */
    @Test
    fun testActivityMainHasRequiredViews() {
        val view = inflater.inflate(R.layout.activity_main, null)

        // Check critical views exist
        assert(view.findViewById<android.view.View>(R.id.recyclerView) != null) {
            "activity_main missing recyclerView"
        }
        assert(view.findViewById<android.view.View>(R.id.statusText) != null) {
            "activity_main missing statusText"
        }
        assert(view.findViewById<android.view.View>(R.id.clearButton) != null) {
            "activity_main missing clearButton"
        }
        assert(view.findViewById<android.view.View>(R.id.deleteButton) != null) {
            "activity_main missing deleteButton"
        }
        assert(view.findViewById<android.view.View>(R.id.settingsButton) != null) {
            "activity_main missing settingsButton"
        }
        assert(view.findViewById<android.view.View>(R.id.markAllReadButton) != null) {
            "activity_main missing markAllReadButton"
        }
        assert(view.findViewById<android.view.View>(R.id.tabLayout) != null) {
            "activity_main missing tabLayout"
        }
        assert(view.findViewById<android.view.View>(R.id.topBarContainer) != null) {
            "activity_main missing topBarContainer"
        }
    }

    /**
     * Test that critical views exist in activity_settings
     */
    @Test
    fun testActivitySettingsHasRequiredViews() {
        val view = inflater.inflate(R.layout.activity_settings, null)

        // Check critical views exist
        assert(view.findViewById<android.view.View>(R.id.emailToggle) != null) {
            "activity_settings missing emailToggle"
        }
        assert(view.findViewById<android.view.View>(R.id.emailInput) != null) {
            "activity_settings missing emailInput"
        }
        assert(view.findViewById<android.view.View>(R.id.senderEmailInput) != null) {
            "activity_settings missing senderEmailInput"
        }
        assert(view.findViewById<android.view.View>(R.id.emailPasswordInput) != null) {
            "activity_settings missing emailPasswordInput"
        }
        assert(view.findViewById<android.view.View>(R.id.smsToggle) != null) {
            "activity_settings missing smsToggle"
        }
        assert(view.findViewById<android.view.View>(R.id.phoneInput) != null) {
            "activity_settings missing phoneInput"
        }
        assert(view.findViewById<android.view.View>(R.id.saveButton) != null) {
            "activity_settings missing saveButton"
        }
    }

    /**
     * Test that item_sms has required views
     */
    @Test
    fun testItemSmsHasRequiredViews() {
        val view = inflater.inflate(R.layout.item_sms, null)

        assert(view.findViewById<android.view.View>(R.id.senderText) != null) {
            "item_sms missing senderText (hidden, but still present for code compatibility)"
        }
        assert(view.findViewById<android.view.View>(R.id.messageText) != null) {
            "item_sms missing messageText"
        }
        assert(view.findViewById<android.view.View>(R.id.timestampText) != null) {
            "item_sms missing timestampText"
        }
        assert(view.findViewById<android.view.View>(R.id.checkBox) != null) {
            "item_sms missing checkBox"
        }
        assert(view.findViewById<android.view.View>(R.id.forwardButton) != null) {
            "item_sms missing forwardButton"
        }
        assert(view.findViewById<android.view.View>(R.id.messageCard) != null) {
            "item_sms missing messageCard"
        }
    }

    /**
     * Test that item_header has required views
     */
    @Test
    fun testItemHeaderHasRequiredViews() {
        val view = inflater.inflate(R.layout.item_header, null)

        assert(view.findViewById<android.view.View>(R.id.headerText) != null) {
            "item_header missing headerText"
        }
        assert(view.findViewById<android.view.View>(R.id.checkBox) != null) {
            "item_header missing checkBox"
        }
    }
}
