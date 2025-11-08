package com.smsreceiver

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var emailToggle: SwitchMaterial
    private lateinit var emailInput: TextInputEditText
    private lateinit var senderEmailInput: TextInputEditText
    private lateinit var emailPasswordInput: TextInputEditText
    private lateinit var smsToggle: SwitchMaterial
    private lateinit var phoneInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var tagListContainer: LinearLayout
    private lateinit var addTagButton: Button

    private val defaultTags = setOf("saved", "receipts", "archived")
    private val customTags = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set up action bar
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize views
        emailToggle = findViewById(R.id.emailToggle)
        emailInput = findViewById(R.id.emailInput)
        senderEmailInput = findViewById(R.id.senderEmailInput)
        emailPasswordInput = findViewById(R.id.emailPasswordInput)
        smsToggle = findViewById(R.id.smsToggle)
        phoneInput = findViewById(R.id.phoneInput)
        saveButton = findViewById(R.id.saveButton)
        tagListContainer = findViewById(R.id.tagListContainer)
        addTagButton = findViewById(R.id.addTagButton)

        // Load saved settings
        loadSettings()
        loadCustomTags()
        refreshTagList()

        // Save button click handler
        saveButton.setOnClickListener {
            saveSettings()
        }

        // Add tag button click handler
        addTagButton.setOnClickListener {
            showAddTagDialog()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("forwarding_settings", Context.MODE_PRIVATE)

        emailToggle.isChecked = prefs.getBoolean("email_enabled", true)
        emailInput.setText(prefs.getString("email_address", "tregister@hotmail.com"))
        senderEmailInput.setText(prefs.getString("sender_email", ""))
        emailPasswordInput.setText(prefs.getString("email_password", ""))

        smsToggle.isChecked = prefs.getBoolean("sms_enabled", true)
        phoneInput.setText(prefs.getString("phone_number", "0552316516"))
    }

    private fun loadCustomTags() {
        val prefs = getSharedPreferences("tag_settings", Context.MODE_PRIVATE)
        val tagsString = prefs.getString("custom_tags", "") ?: ""
        customTags.clear()
        if (tagsString.isNotEmpty()) {
            customTags.addAll(tagsString.split(","))
        }
    }

    private fun saveCustomTags() {
        val prefs = getSharedPreferences("tag_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_tags", customTags.joinToString(",")).apply()
    }

    private fun refreshTagList() {
        tagListContainer.removeAllViews()

        // Show default tags (read-only)
        for (tag in defaultTags) {
            val tagView = createTagView(tag, isDefault = true)
            tagListContainer.addView(tagView)
        }

        // Show custom tags (editable/deletable)
        for (tag in customTags) {
            val tagView = createTagView(tag, isDefault = false)
            tagListContainer.addView(tagView)
        }
    }

    private fun createTagView(tag: String, isDefault: Boolean): LinearLayout {
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFFE0E0E0.toInt())
        }

        val tagText = TextView(this).apply {
            text = tag.capitalize()
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        tagRow.addView(tagText)

        if (!isDefault) {
            // Rename button
            val renameButton = Button(this).apply {
                text = "Rename"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
                setOnClickListener {
                    showRenameTagDialog(tag)
                }
            }
            tagRow.addView(renameButton)

            // Delete button
            val deleteButton = Button(this).apply {
                text = "Delete"
                textSize = 12f
                setTextColor(0xFFFF0000.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    showDeleteTagConfirmation(tag)
                }
            }
            tagRow.addView(deleteButton)
        } else {
            val defaultLabel = TextView(this).apply {
                text = "(default)"
                textSize = 12f
                setTextColor(0xFF666666.toInt())
            }
            tagRow.addView(defaultLabel)
        }

        return tagRow
    }

    private fun showAddTagDialog() {
        val input = EditText(this).apply {
            hint = "Tag name"
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Add New Tag")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val tagName = input.text.toString().trim().lowercase()
                if (tagName.isEmpty()) {
                    Toast.makeText(this, "Tag name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (defaultTags.contains(tagName) || customTags.contains(tagName)) {
                    Toast.makeText(this, "Tag already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                customTags.add(tagName)
                saveCustomTags()
                refreshTagList()
                Toast.makeText(this, "Tag added: $tagName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameTagDialog(oldTag: String) {
        val input = EditText(this).apply {
            setText(oldTag)
            hint = "Tag name"
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Tag")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newTag = input.text.toString().trim().lowercase()
                if (newTag.isEmpty()) {
                    Toast.makeText(this, "Tag name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newTag == oldTag) {
                    return@setPositiveButton
                }
                if (defaultTags.contains(newTag) || customTags.contains(newTag)) {
                    Toast.makeText(this, "Tag already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Rename in custom tags
                customTags.remove(oldTag)
                customTags.add(newTag)
                saveCustomTags()

                // Rename in all messages
                renameTagInAllMessages(oldTag, newTag)

                refreshTagList()
                Toast.makeText(this, "Tag renamed: $oldTag → $newTag", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteTagConfirmation(tag: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Tag")
            .setMessage("Delete tag '$tag'?\n\nThis will remove the tag from all messages.")
            .setPositiveButton("Delete") { _, _ ->
                customTags.remove(tag)
                saveCustomTags()

                // Remove from all messages
                removeTagFromAllMessages(tag)

                refreshTagList()
                Toast.makeText(this, "Tag deleted: $tag", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameTagInAllMessages(oldTag: String, newTag: String) {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val storedData = prefs.getString("sms_list", "") ?: ""

        if (storedData.isBlank()) return

        val lines = storedData.split("\n").filter { it.isNotBlank() }
        val updatedLines = mutableListOf<String>()

        for (line in lines) {
            val parts = line.split("|").toMutableList()
            if (parts.size >= 7) {
                val tagsString = parts[6]
                if (tagsString.isNotEmpty()) {
                    val tags = tagsString.split(",").toMutableSet()
                    if (tags.contains(oldTag)) {
                        tags.remove(oldTag)
                        tags.add(newTag)
                        parts[6] = tags.joinToString(",")
                    }
                }
            }
            updatedLines.add(parts.joinToString("|"))
        }

        prefs.edit().putString("sms_list", updatedLines.joinToString("\n")).apply()
    }

    private fun removeTagFromAllMessages(tag: String) {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val storedData = prefs.getString("sms_list", "") ?: ""

        if (storedData.isBlank()) return

        val lines = storedData.split("\n").filter { it.isNotBlank() }
        val updatedLines = mutableListOf<String>()

        for (line in lines) {
            val parts = line.split("|").toMutableList()
            if (parts.size >= 7) {
                val tagsString = parts[6]
                if (tagsString.isNotEmpty()) {
                    val tags = tagsString.split(",").toMutableSet()
                    tags.remove(tag)
                    parts[6] = tags.joinToString(",")
                }
            }
            updatedLines.add(parts.joinToString("|"))
        }

        prefs.edit().putString("sms_list", updatedLines.joinToString("\n")).apply()
    }

    private fun saveSettings() {
        val email = emailInput.text.toString().trim()
        val senderEmail = senderEmailInput.text.toString().trim()
        val emailPassword = emailPasswordInput.text.toString()
        val phone = phoneInput.text.toString().trim()

        // Basic validation
        if (emailToggle.isChecked && email.isEmpty()) {
            Toast.makeText(this, "Please enter a forward-to email address", Toast.LENGTH_SHORT).show()
            return
        }

        if (emailToggle.isChecked && senderEmail.isEmpty()) {
            Toast.makeText(this, "Please enter your sender email address", Toast.LENGTH_SHORT).show()
            return
        }

        if (emailToggle.isChecked && emailPassword.isEmpty()) {
            Toast.makeText(this, "Please enter your email password", Toast.LENGTH_SHORT).show()
            return
        }

        if (smsToggle.isChecked && phone.isEmpty()) {
            Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
            return
        }

        // Save to SharedPreferences
        val prefs = getSharedPreferences("forwarding_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("email_enabled", emailToggle.isChecked)
            putString("email_address", email)
            putString("sender_email", senderEmail)
            putString("email_password", emailPassword)
            putBoolean("sms_enabled", smsToggle.isChecked)
            putString("phone_number", phone)
            apply()
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
