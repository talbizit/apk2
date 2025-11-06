package com.smsreceiver

import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fast unit tests to validate XML layouts using Robolectric.
 * These run much faster than instrumentation tests and catch XML errors early.
 *
 * Catches issues like:
 * - Missing namespace declarations (xmlns:app, xmlns:tools)
 * - Invalid attribute references
 * - Malformed XML structure
 * - Resource reference errors
 * - Missing required views
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LayoutXmlValidationTest {

    @Test
    fun testActivityMainXmlIsValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)

        try {
            val view = inflater.inflate(R.layout.activity_main, null)
            assert(view != null) { "activity_main.xml failed to inflate" }
        } catch (e: Exception) {
            throw AssertionError("activity_main.xml has errors: ${e.message}", e)
        }
    }

    @Test
    fun testActivitySettingsXmlIsValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)

        try {
            val view = inflater.inflate(R.layout.activity_settings, null)
            assert(view != null) { "activity_settings.xml failed to inflate" }
        } catch (e: Exception) {
            throw AssertionError("activity_settings.xml has errors: ${e.message}", e)
        }
    }

    @Test
    fun testItemSmsXmlIsValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)

        try {
            val view = inflater.inflate(R.layout.item_sms, null)
            assert(view != null) { "item_sms.xml failed to inflate" }
        } catch (e: Exception) {
            throw AssertionError("item_sms.xml has errors: ${e.message}", e)
        }
    }

    @Test
    fun testItemHeaderXmlIsValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)

        try {
            val view = inflater.inflate(R.layout.item_header, null)
            assert(view != null) { "item_header.xml failed to inflate" }
        } catch (e: Exception) {
            throw AssertionError("item_header.xml has errors: ${e.message}", e)
        }
    }

    /**
     * Comprehensive test that validates all layouts at once.
     * If any layout has XML errors, this test will fail and report which ones.
     */
    @Test
    fun testAllLayoutXmlFilesAreValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)

        val layouts = mapOf(
            "activity_main" to R.layout.activity_main,
            "activity_settings" to R.layout.activity_settings,
            "item_sms" to R.layout.item_sms,
            "item_header" to R.layout.item_header
        )

        val errors = mutableListOf<String>()

        for ((name, layoutId) in layouts) {
            try {
                val view = inflater.inflate(layoutId, null)
                assert(view != null) { "$name.xml returned null view" }
            } catch (e: Exception) {
                errors.add("$name.xml: ${e.message}")
            }
        }

        if (errors.isNotEmpty()) {
            throw AssertionError(
                "XML validation failed for ${errors.size} layout(s):\n" +
                errors.joinToString("\n") { "  - $it" }
            )
        }
    }

    /**
     * Test that verifies activity_main has all required view IDs.
     * This catches errors where views are renamed or removed.
     */
    @Test
    fun testActivityMainHasAllRequiredViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.activity_main, null)

        val requiredViewIds = listOf(
            R.id.recyclerView,
            R.id.statusText,
            R.id.clearButton,
            R.id.deleteButton,
            R.id.settingsButton,
            R.id.tabLayout
        )

        val missingViews = mutableListOf<String>()

        for (viewId in requiredViewIds) {
            if (view.findViewById<android.view.View>(viewId) == null) {
                val viewName = context.resources.getResourceEntryName(viewId)
                missingViews.add(viewName)
            }
        }

        if (missingViews.isNotEmpty()) {
            throw AssertionError(
                "activity_main.xml is missing required views: ${missingViews.joinToString(", ")}"
            )
        }
    }

    /**
     * Test that verifies activity_settings has all required view IDs.
     */
    @Test
    fun testActivitySettingsHasAllRequiredViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.activity_settings, null)

        val requiredViewIds = listOf(
            R.id.emailToggle,
            R.id.emailInput,
            R.id.senderEmailInput,
            R.id.emailPasswordInput,
            R.id.smsToggle,
            R.id.phoneInput,
            R.id.saveButton
        )

        val missingViews = mutableListOf<String>()

        for (viewId in requiredViewIds) {
            if (view.findViewById<android.view.View>(viewId) == null) {
                val viewName = context.resources.getResourceEntryName(viewId)
                missingViews.add(viewName)
            }
        }

        if (missingViews.isNotEmpty()) {
            throw AssertionError(
                "activity_settings.xml is missing required views: ${missingViews.joinToString(", ")}"
            )
        }
    }
}
