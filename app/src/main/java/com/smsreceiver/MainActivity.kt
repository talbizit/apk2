package com.smsreceiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var smsAdapter: SmsAdapter
    private lateinit var statusText: TextView
    private lateinit var clearButton: Button
    private val smsList = mutableListOf<SmsData>()

    private val SMS_PERMISSION_CODE = 100
    private val NOTIFICATION_PERMISSION_CODE = 101

    // ContentObserver to monitor SMS database
    private var smsObserver: ContentObserver? = null
    private var lastSmsId = 0L

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SmsReceiver.SMS_RECEIVED_ACTION) {
                val sender = intent.getStringExtra(SmsReceiver.EXTRA_SENDER) ?: "Unknown"
                val message = intent.getStringExtra(SmsReceiver.EXTRA_MESSAGE) ?: ""
                val timestamp = intent.getLongExtra(SmsReceiver.EXTRA_TIMESTAMP, System.currentTimeMillis())

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = dateFormat.format(Date(timestamp))

                val smsData = SmsData(sender, message, date)
                smsAdapter.addSms(smsData)
                updateStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        statusText = findViewById(R.id.statusText)
        clearButton = findViewById(R.id.clearButton)

        smsAdapter = SmsAdapter(smsList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = smsAdapter

        clearButton.setOnClickListener {
            smsAdapter.clearAll()
            clearStoredSms()
            updateStatus()
            Toast.makeText(this, "Messages cleared", Toast.LENGTH_SHORT).show()
        }

        checkAndRequestPermissions()
        loadStoredSms()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()

        // Register broadcast receiver
        val filter = IntentFilter(SmsReceiver.SMS_RECEIVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }

        // Register ContentObserver to monitor SMS database
        registerSmsObserver()

        // Reload SMS list when app comes back to foreground
        refreshSmsListFromStorage()
    }

    override fun onPause() {
        super.onPause()

        // Unregister broadcast receiver
        try {
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            // Receiver was not registered
        }

        // Unregister ContentObserver
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_SMS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                SMS_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Permissions granted! SMS Receiver is active.", Toast.LENGTH_LONG).show()
                    updateStatus()
                } else {
                    Toast.makeText(this, "SMS permissions are required for this app to work", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadStoredSms() {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val storedData = prefs.getString("sms_list", "") ?: ""

        if (storedData.isNotBlank()) {
            val lines = storedData.split("\n").filter { it.isNotBlank() }
            for (line in lines) {
                val parts = line.split("|")
                if (parts.size == 3) {
                    // Unescape special characters (must handle \\ first with temp marker)
                    val unescapedMessage = parts[2]
                        .replace("\\\\", "\u0001")  // Temp marker for escaped backslash
                        .replace("\\n", "\n")        // Unescape newlines
                        .replace("\\|", "|")         // Unescape pipe
                        .replace("\u0001", "\\")     // Restore backslash
                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0]))
                }
            }
            smsAdapter.notifyDataSetChanged()
        }
    }

    private fun clearStoredSms() {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun refreshSmsListFromStorage() {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val storedData = prefs.getString("sms_list", "") ?: ""

        smsList.clear()
        if (storedData.isNotBlank()) {
            val lines = storedData.split("\n").filter { it.isNotBlank() }
            for (line in lines) {
                val parts = line.split("|")
                if (parts.size == 3) {
                    // Unescape special characters (must handle \\ first with temp marker)
                    val unescapedMessage = parts[2]
                        .replace("\\\\", "\u0001")  // Temp marker for escaped backslash
                        .replace("\\n", "\n")        // Unescape newlines
                        .replace("\\|", "|")         // Unescape pipe
                        .replace("\u0001", "\\")     // Restore backslash
                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0]))
                }
            }
        }
        smsAdapter.notifyDataSetChanged()
        updateStatus()
    }

    private fun registerSmsObserver() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Get the last SMS ID from database
        lastSmsId = getLastSmsId()

        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkForNewSms()
            }
        }

        // Monitor SMS inbox
        contentResolver.registerContentObserver(
            Uri.parse("content://sms/inbox"),
            true,
            smsObserver!!
        )
    }

    private fun getLastSmsId(): Long {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return 0L
        }

        try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id"),
                null,
                null,
                "_id DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(it.getColumnIndexOrThrow("_id"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    private fun checkForNewSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "body", "date"),
                "_id > ?",
                arrayOf(lastSmsId.toString()),
                "_id ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("_id"))
                    val address = it.getString(it.getColumnIndexOrThrow("address"))
                    val body = it.getString(it.getColumnIndexOrThrow("body"))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow("date"))

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val date = dateFormat.format(Date(timestamp))

                    // Save to storage
                    saveSmsToStorage(address, body, timestamp)

                    // Update UI
                    val smsData = SmsData(address, body, date)
                    smsAdapter.addSms(smsData)

                    lastSmsId = id
                }
                updateStatus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSmsToStorage(sender: String, message: String, timestamp: Long) {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val existingData = prefs.getString("sms_list", "") ?: ""

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(timestamp))

        // Escape special characters to prevent parsing issues
        val escapedMessage = message
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\n", "\\n")    // Escape newlines
            .replace("|", "\\|")     // Escape pipe delimiter

        val newEntry = "$date|$sender|$escapedMessage\n"
        val updatedData = newEntry + existingData

        // Keep only last 100 messages
        val lines = updatedData.split("\n")
        val limitedData = lines.take(100).joinToString("\n")

        prefs.edit().putString("sms_list", limitedData).apply()
    }

    private fun updateStatus() {
        val hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val count = smsAdapter.itemCount

        statusText.text = if (hasPermissions) {
            "✓ SMS Receiver Active\nMessages received: $count"
        } else {
            "⚠ Permissions needed\nMessages received: $count"
        }
    }
}
