package com.smsreceiver

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var emailToggle: SwitchMaterial
    private lateinit var emailInput: TextInputEditText
    private lateinit var smsToggle: SwitchMaterial
    private lateinit var phoneInput: TextInputEditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set up action bar
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize views
        emailToggle = findViewById(R.id.emailToggle)
        emailInput = findViewById(R.id.emailInput)
        smsToggle = findViewById(R.id.smsToggle)
        phoneInput = findViewById(R.id.phoneInput)
        saveButton = findViewById(R.id.saveButton)

        // Load saved settings
        loadSettings()

        // Save button click handler
        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("forwarding_settings", Context.MODE_PRIVATE)

        emailToggle.isChecked = prefs.getBoolean("email_enabled", true)
        emailInput.setText(prefs.getString("email_address", "tregister@hotmail.com"))

        smsToggle.isChecked = prefs.getBoolean("sms_enabled", true)
        phoneInput.setText(prefs.getString("phone_number", "0552316516"))
    }

    private fun saveSettings() {
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        // Basic validation
        if (emailToggle.isChecked && email.isEmpty()) {
            Toast.makeText(this, "Please enter an email address", Toast.LENGTH_SHORT).show()
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
