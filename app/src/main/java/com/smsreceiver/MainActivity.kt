package com.smsreceiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var groupedAdapter: GroupedSmsAdapter
    private lateinit var statusText: TextView
    private lateinit var clearButton: Button
    private lateinit var deleteButton: Button
    private lateinit var tabLayout: TabLayout
    private val smsList = mutableListOf<SmsData>()

    private val SMS_PERMISSION_CODE = 100
    private val NOTIFICATION_PERMISSION_CODE = 101

    // ContentObserver to monitor SMS database
    private var smsObserver: ContentObserver? = null
    private var lastSmsId = 0L

    // Track current tab (0 = Inbox, 1 = Archive)
    private var currentTab = 0

    // Selection mode tracking
    private var isSelectionMode = false

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SmsReceiver.SMS_RECEIVED_ACTION) {
                val sender = intent.getStringExtra(SmsReceiver.EXTRA_SENDER) ?: "Unknown"
                val message = intent.getStringExtra(SmsReceiver.EXTRA_MESSAGE) ?: ""
                val timestamp = intent.getLongExtra(SmsReceiver.EXTRA_TIMESTAMP, System.currentTimeMillis())

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = dateFormat.format(Date(timestamp))

                val smsData = SmsData(sender, message, date, timestamp, isArchived = false)
                smsList.add(0, smsData)
                refreshDisplay()
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
        deleteButton = findViewById(R.id.deleteButton)
        tabLayout = findViewById(R.id.tabLayout)

        groupedAdapter = GroupedSmsAdapter(
            items = mutableListOf(),
            onItemClick = { position -> handleItemClick(position) },
            onItemLongClick = { position -> handleItemLongClick(position) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = groupedAdapter

        // Setup swipe to archive/restore
        setupSwipeGesture()

        // Setup tab switching
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateClearButtonText()
                refreshDisplay()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        clearButton.setOnClickListener {
            if (currentTab == 0) {
                // Archive all inbox messages
                smsList.forEach { if (!it.isArchived) it.isArchived = true; it.archiveTimestamp = System.currentTimeMillis() }
                saveAllSms()
                refreshDisplay()
                updateStatus()
                Toast.makeText(this, "All messages archived", Toast.LENGTH_SHORT).show()
            } else {
                // Show confirmation dialog for permanent deletion
                showDeleteArchiveConfirmation()
            }
        }

        deleteButton.setOnClickListener {
            val selectedItems = groupedAdapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                smsList.removeAll { sms -> selectedItems.any { it.sender == sms.sender && it.message == sms.message && it.timestamp == sms.timestamp } }
                saveAllSms()
                exitSelectionMode()
                refreshDisplay()
                updateStatus()
                Toast.makeText(this, "${selectedItems.size} message(s) deleted permanently", Toast.LENGTH_SHORT).show()
            }
        }

        checkAndRequestPermissions()
        updateClearButtonText()
        loadStoredSms()
        refreshDisplay()
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
                if (parts.size >= 3) {
                    // Unescape special characters
                    val unescapedMessage = parts[2]
                        .replace("\\\\", "\u0001")
                        .replace("\\n", "\n")
                        .replace("\\|", "|")
                        .replace("\u0001", "\\")

                    val isArchived = if (parts.size > 3) parts[3] == "1" else false
                    val archiveTimestamp = if (parts.size > 4) parts[4].toLongOrNull() ?: 0L else 0L
                    val timestampMillis = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L

                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0], timestampMillis, isArchived, archiveTimestamp))
                }
            }
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
                if (parts.size >= 3) {
                    // Unescape special characters
                    val unescapedMessage = parts[2]
                        .replace("\\\\", "\u0001")
                        .replace("\\n", "\n")
                        .replace("\\|", "|")
                        .replace("\u0001", "\\")

                    val isArchived = if (parts.size > 3) parts[3] == "1" else false
                    val archiveTimestamp = if (parts.size > 4) parts[4].toLongOrNull() ?: 0L else 0L
                    val timestampMillis = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L

                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0], timestampMillis, isArchived, archiveTimestamp))
                }
            }
        }
        refreshDisplay()
        updateStatus()
    }

    private fun setupSwipeGesture() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private val background = ColorDrawable(Color.parseColor("#4CAF50"))

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = groupedAdapter.getItemAtPosition(position)

                if (item is ListItem.Message) {
                    val sms = item.sms
                    if (currentTab == 0) {
                        // Archive the message
                        sms.isArchived = true
                        sms.archiveTimestamp = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, "Message archived", Toast.LENGTH_SHORT).show()
                    } else {
                        // Restore to inbox
                        sms.isArchived = false
                        sms.archiveTimestamp = 0L
                        Toast.makeText(this@MainActivity, "Message restored to inbox", Toast.LENGTH_SHORT).show()
                    }
                    saveAllSms()
                    refreshDisplay()
                    updateStatus()
                } else {
                    // Can't swipe headers, restore view
                    groupedAdapter.notifyItemChanged(position)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView

                // Only allow swiping messages, not headers
                val item = groupedAdapter.getItemAtPosition(viewHolder.adapterPosition)
                if (item !is ListItem.Message) {
                    return
                }

                if (dX > 0) {
                    background.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dX.toInt(),
                        itemView.bottom
                    )
                    background.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun refreshDisplay() {
        val items = if (currentTab == 0) {
            groupByInbox()
        } else {
            groupByArchive()
        }
        groupedAdapter.updateItems(items)
    }

    private fun groupByInbox(): List<ListItem> {
        val inboxMessages = smsList.filter { !it.isArchived }
        return groupBySender(inboxMessages)
    }

    private fun groupByArchive(): List<ListItem> {
        val archivedMessages = smsList.filter { it.isArchived }
        return groupByTimeCategories(archivedMessages)
    }

    private fun groupBySender(messages: List<SmsData>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        val grouped = messages.groupBy { it.sender }

        for ((sender, senderMessages) in grouped) {
            result.add(ListItem.Header(sender))
            senderMessages.forEach { result.add(ListItem.Message(it)) }
        }

        return result
    }

    private fun groupByTimeCategories(messages: List<SmsData>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        val now = System.currentTimeMillis()

        val categories = mapOf(
            "Last 7 Days" to messages.filter { daysSince(it.archiveTimestamp, now) <= 7 },
            "Last Month" to messages.filter { daysSince(it.archiveTimestamp, now) in 8..30 },
            "Last Quarter" to messages.filter { daysSince(it.archiveTimestamp, now) in 31..90 },
            "Last Year" to messages.filter { daysSince(it.archiveTimestamp, now) in 91..365 },
            "Older" to messages.filter { daysSince(it.archiveTimestamp, now) > 365 }
        )

        for ((category, categoryMessages) in categories) {
            if (categoryMessages.isNotEmpty()) {
                result.add(ListItem.Header(category))
                // Group by sender within each time category
                val senderGroups = categoryMessages.groupBy { it.sender }
                for ((sender, senderMessages) in senderGroups) {
                    result.add(ListItem.Header("  $sender"))
                    senderMessages.forEach { result.add(ListItem.Message(it)) }
                }
            }
        }

        return result
    }

    private fun daysSince(timestamp: Long, now: Long): Long {
        return TimeUnit.MILLISECONDS.toDays(now - timestamp)
    }

    private fun saveAllSms() {
        val prefs = getSharedPreferences("sms_storage", Context.MODE_PRIVATE)
        val lines = smsList.map { sms ->
            val escapedMessage = sms.message
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("|", "\\|")
            "${sms.timestamp}|${sms.sender}|$escapedMessage|${if (sms.isArchived) "1" else "0"}|${sms.archiveTimestamp}|${sms.timestampMillis}"
        }
        prefs.edit().putString("sms_list", lines.joinToString("\n")).apply()
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

                    // Update UI
                    val smsData = SmsData(address, body, date, timestamp, isArchived = false)
                    smsList.add(0, smsData)

                    lastSmsId = id
                }
                saveAllSms()
                refreshDisplay()
                updateStatus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleItemClick(position: Int) {
        if (!isSelectionMode) return

        val item = groupedAdapter.getItemAtPosition(position)
        when (item) {
            is ListItem.Header -> {
                // Select/deselect all messages in this group
                selectGroupItems(position, item.isSelected)
            }
            is ListItem.Message -> {
                // Individual message selection handled in adapter
            }
            null -> {}
        }
        updateSelectionCount()
    }

    private fun handleItemLongClick(position: Int): Boolean {
        if (!isSelectionMode && currentTab == 1) { // Only in Archive tab
            enterSelectionMode()
            val item = groupedAdapter.getItemAtPosition(position)
            when (item) {
                is ListItem.Header -> {
                    item.isSelected = true
                    selectGroupItems(position, true)
                }
                is ListItem.Message -> {
                    item.isSelected = true
                }
                null -> {}
            }
            groupedAdapter.notifyDataSetChanged()
            updateSelectionCount()
            return true
        }
        return false
    }

    private fun selectGroupItems(headerPosition: Int, isSelected: Boolean) {
        // Find all messages under this header until the next header
        var pos = headerPosition + 1
        while (pos < groupedAdapter.itemCount) {
            val nextItem = groupedAdapter.getItemAtPosition(pos)
            when (nextItem) {
                is ListItem.Header -> break // Stop at next header
                is ListItem.Message -> {
                    nextItem.isSelected = isSelected
                    pos++
                }
                null -> break
            }
        }
        groupedAdapter.notifyDataSetChanged()
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        groupedAdapter.isSelectionMode = true
        deleteButton.visibility = View.VISIBLE
        clearButton.visibility = View.GONE
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        groupedAdapter.isSelectionMode = false
        groupedAdapter.clearSelections()
        deleteButton.visibility = View.GONE
        clearButton.visibility = View.VISIBLE
    }

    private fun updateSelectionCount() {
        val count = groupedAdapter.getSelectedCount()
        statusText.text = "Selected: $count message(s)"
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
            updateStatus()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateClearButtonText() {
        clearButton.text = if (currentTab == 0) {
            "Archive All Messages"
        } else {
            "Clear Archive"
        }
    }

    private fun showDeleteArchiveConfirmation() {
        val archiveCount = smsList.count { it.isArchived }

        if (archiveCount == 0) {
            Toast.makeText(this, "Archive is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null)
        val input = EditText(this).apply {
            hint = "Type DELETE to confirm"
            setPadding(50, 40, 50, 40)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Permanently Delete Archive")
            .setMessage("This will permanently delete $archiveCount archived message(s).\n\nThis action CANNOT be undone!\n\nType DELETE in capital letters to confirm:")
            .setView(input)
            .setPositiveButton("DELETE") { _, _ ->
                // This will be enabled/disabled by the TextWatcher
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val isValid = s.toString() == "DELETE"
                    positiveButton.isEnabled = isValid
                    positiveButton.alpha = if (isValid) 1.0f else 0.5f
                }
            })

            positiveButton.setOnClickListener {
                // Perform the deletion
                smsList.removeAll { it.isArchived }
                saveAllSms()
                refreshDisplay()
                updateStatus()
                Toast.makeText(this, "Archive cleared permanently", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updateStatus() {
        val hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val inboxCount = smsList.count { !it.isArchived }
        val archiveCount = smsList.count { it.isArchived }

        statusText.text = if (hasPermissions) {
            "✓ SMS Receiver Active\nInbox: $inboxCount | Archive: $archiveCount"
        } else {
            "⚠ Permissions needed\nInbox: $inboxCount | Archive: $archiveCount"
        }
    }
}
