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
import android.graphics.Paint
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
import android.widget.ImageButton
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
import javax.mail.*
import javax.mail.internet.*
import java.util.Properties
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var groupedAdapter: GroupedSmsAdapter
    private lateinit var statusText: TextView
    private lateinit var clearButton: Button
    private lateinit var deleteButton: ImageButton
    private lateinit var settingsButton: Button
    private lateinit var tabLayout: TabLayout
    private val smsList = mutableListOf<SmsData>()

    private val SMS_PERMISSION_CODE = 100
    private val NOTIFICATION_PERMISSION_CODE = 101

    // ContentObserver to monitor SMS database
    private var smsObserver: ContentObserver? = null
    private var lastSmsId = 0L

    // Track current tab (0 = Inbox, 1 = Saved, 2 = Receipts, 3 = Archive)
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

                val smsData = SmsData(sender, message, date, timestamp, isArchived = false, archiveTimestamp = 0L, tags = mutableSetOf())
                smsList.add(0, smsData)
                refreshDisplay()
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
        settingsButton = findViewById(R.id.settingsButton)
        tabLayout = findViewById(R.id.tabLayout)

        groupedAdapter = GroupedSmsAdapter(
            items = mutableListOf(),
            onItemClick = { position -> handleItemClick(position) },
            onItemLongClick = { position -> handleItemLongClick(position) },
            onRefreshNeeded = { refreshDisplay() }
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

        // Clear button is now hidden - functionality moved to long-press menu
        clearButton.setOnClickListener { }

        deleteButton.setOnClickListener {
            val selectedItems = groupedAdapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                smsList.removeAll { sms -> selectedItems.any { it.sender == sms.sender && it.message == sms.message && it.timestamp == sms.timestamp } }
                saveAllSms()
                exitSelectionMode()
                refreshDisplay()
                Toast.makeText(this, "${selectedItems.size} message(s) deleted permanently", Toast.LENGTH_SHORT).show()
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        checkAndRequestPermissions()
        loadStoredSms()
        refreshDisplay()
        updateTabTitles()
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS)
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
                    val tagsString = if (parts.size > 6) parts[6] else ""
                    val tags = if (tagsString.isNotEmpty()) tagsString.split(",").toMutableSet() else mutableSetOf()

                    // Migrate old isArchived to tag system
                    if (isArchived && !tags.contains("archived")) {
                        tags.add("archived")
                    }

                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0], timestampMillis, false, archiveTimestamp, tags))
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
                    val tagsString = if (parts.size > 6) parts[6] else ""
                    val tags = if (tagsString.isNotEmpty()) tagsString.split(",").toMutableSet() else mutableSetOf()

                    // Migrate old isArchived to tag system
                    if (isArchived && !tags.contains("archived")) {
                        tags.add("archived")
                    }

                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0], timestampMillis, false, archiveTimestamp, tags))
                }
            }
        }
        refreshDisplay()
    }

    private fun setupSwipeGesture() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) {
            private val archiveBackground = ColorDrawable(Color.parseColor("#4CAF50"))
            private val forwardBackground = ColorDrawable(Color.parseColor("#2196F3"))
            private val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 48f
                isFakeBoldText = true
                textAlign = Paint.Align.LEFT
            }

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

                    when (direction) {
                        ItemTouchHelper.RIGHT -> {
                            // Archive/Unarchive functionality (toggle "archived" tag)
                            if (sms.tags.contains("archived")) {
                                // Remove archived tag
                                sms.tags.remove("archived")
                                sms.archiveTimestamp = 0L
                                Toast.makeText(this@MainActivity, "Unarchived", Toast.LENGTH_SHORT).show()
                            } else {
                                // Add archived tag (preserves other tags)
                                sms.tags.add("archived")
                                sms.archiveTimestamp = System.currentTimeMillis()
                                Toast.makeText(this@MainActivity, "Archived", Toast.LENGTH_SHORT).show()
                            }
                            saveAllSms()
                            refreshDisplay()
                        }
                        ItemTouchHelper.LEFT -> {
                            // Forward functionality
                            forwardMessage(sms)
                            // Restore the view since we're not removing the message
                            groupedAdapter.notifyItemChanged(position)
                        }
                    }
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
                    // Swipe right - archive/unarchive
                    archiveBackground.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dX.toInt(),
                        itemView.bottom
                    )
                    archiveBackground.draw(c)

                    // Draw text based on whether message has "archived" tag
                    val smsItem = item as? ListItem.Message
                    val text = if (smsItem?.sms?.tags?.contains("archived") == true) "Unarchive" else "Archive"
                    val textX = itemView.left.toFloat() + 40f
                    val textY = itemView.top + (itemView.height / 2f) + (textPaint.textSize / 3f)

                    c.drawText(text, textX, textY, textPaint)
                } else if (dX < 0) {
                    // Swipe left - forward
                    forwardBackground.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    forwardBackground.draw(c)

                    // Draw text
                    val text = "Forward"
                    val textWidth = textPaint.measureText(text)
                    val textX = itemView.right.toFloat() - textWidth - 40f
                    val textY = itemView.top + (itemView.height / 2f) + (textPaint.textSize / 3f)

                    c.drawText(text, textX, textY, textPaint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun refreshDisplay() {
        val items = when (currentTab) {
            0 -> groupByInbox()      // No tags + not archived
            1 -> groupBySaved()      // "saved" tag + not archived
            2 -> groupByReceipts()   // "receipts" tag + not archived
            3 -> groupByArchive()    // Archived (tags preserved)
            else -> groupByInbox()
        }
        groupedAdapter.restoreCollapsedState(items)
        groupedAdapter.notifyDataSetChanged()
        updateTabTitles()
    }

    private fun updateTabTitles() {
        val inboxCount = smsList.count { it.tags.isEmpty() }
        val savedCount = smsList.count { it.tags.contains("saved") }
        val receiptsCount = smsList.count { it.tags.contains("receipts") }
        val archiveCount = smsList.count { it.tags.contains("archived") }

        tabLayout.getTabAt(0)?.text = "Inbox ($inboxCount)"
        tabLayout.getTabAt(1)?.text = "Saved ($savedCount)"
        tabLayout.getTabAt(2)?.text = "Receipts ($receiptsCount)"
        tabLayout.getTabAt(3)?.text = "Archive ($archiveCount)"
    }

    private fun groupByInbox(): List<ListItem> {
        // Inbox = messages with no tags at all
        val inboxMessages = smsList.filter { it.tags.isEmpty() }
        return groupBySender(inboxMessages)
    }

    private fun groupBySaved(): List<ListItem> {
        // Saved = messages with "saved" tag (may have other tags too)
        val savedMessages = smsList.filter { it.tags.contains("saved") }
        return groupBySender(savedMessages)
    }

    private fun groupByReceipts(): List<ListItem> {
        // Receipts = messages with "receipts" tag (may have other tags too)
        val receiptsMessages = smsList.filter { it.tags.contains("receipts") }
        return groupBySender(receiptsMessages)
    }

    private fun groupByArchive(): List<ListItem> {
        // Archive = messages with "archived" tag (may have other tags too)
        val archivedMessages = smsList.filter { it.tags.contains("archived") }
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
            val tagsString = sms.tags.joinToString(",")
            // isArchived field is deprecated (always "0"), archived status is now in tags
            "${sms.timestamp}|${sms.sender}|$escapedMessage|0|${sms.archiveTimestamp}|${sms.timestampMillis}|$tagsString"
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
                    val smsData = SmsData(address, body, date, timestamp, isArchived = false, archiveTimestamp = 0L, tags = mutableSetOf())
                    smsList.add(0, smsData)

                    lastSmsId = id
                }
                saveAllSms()
                refreshDisplay()
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
        val item = groupedAdapter.getItemAtPosition(position)

        // Show tags dialog for any message
        if (item is ListItem.Message) {
            showMoveToDialog(item.sms)
            return true
        }

        // For headers in Archive tab: multi-select delete mode
        if (!isSelectionMode && currentTab == 3 && item is ListItem.Header) {
            enterSelectionMode()
            item.isSelected = true
            selectGroupItems(position, true)
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
        // Status text is hidden - selection shown via UI highlighting
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateClearButtonText() {
        // Button is now hidden - functionality in long-press menu
    }

    private fun showArchiveAllConfirmation() {
        val inboxCount = smsList.count { it.tags.isEmpty() }

        if (inboxCount == 0) {
            Toast.makeText(this, "Inbox is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Archive All Messages")
            .setMessage("Archive all $inboxCount message(s) from Inbox?\n\nYou can restore them from the Archive tab later.")
            .setPositiveButton("Archive All") { _, _ ->
                // Archive all inbox messages (add archived tag)
                smsList.forEach {
                    if (it.tags.isEmpty()) {
                        it.tags.add("archived")
                        it.archiveTimestamp = System.currentTimeMillis()
                    }
                }
                saveAllSms()
                refreshDisplay()
                Toast.makeText(this, "All messages archived", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteArchiveConfirmation() {
        val archiveCount = smsList.count { it.tags.contains("archived") }

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
                smsList.removeAll { it.tags.contains("archived") }
                saveAllSms()
                refreshDisplay()
                Toast.makeText(this, "Archive cleared permanently", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showClearTagConfirmation(tag: String, tagDisplayName: String) {
        val tagCount = smsList.count { it.tags.contains(tag) }

        if (tagCount == 0) {
            Toast.makeText(this, "$tagDisplayName folder is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear $tagDisplayName Folder")
            .setMessage("Remove $tagCount message(s) from $tagDisplayName folder?\n\nMessages will move to Inbox or remain in other folders.")
            .setPositiveButton("Clear") { _, _ ->
                // Remove tag from all messages that have it
                smsList.forEach {
                    if (it.tags.contains(tag)) {
                        it.tags.remove(tag)
                    }
                }
                saveAllSms()
                refreshDisplay()
                Toast.makeText(this, "$tagDisplayName folder cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoveToDialog(sms: SmsData) {
        val tagOptions = arrayOf("Inbox", "Saved", "Receipts", "Archive")
        val checkedItems = booleanArrayOf(
            sms.tags.isEmpty(),                    // Inbox = no tags
            sms.tags.contains("saved"),            // Saved
            sms.tags.contains("receipts"),         // Receipts
            sms.tags.contains("archived")          // Archive
        )

        AlertDialog.Builder(this)
            .setTitle("Tags")
            .setMultiChoiceItems(tagOptions, checkedItems) { _, which, isChecked ->
                // Handle checkbox changes
                when (which) {
                    0 -> { // Inbox
                        if (isChecked) {
                            // Clear all tags when Inbox is selected
                            checkedItems[1] = false
                            checkedItems[2] = false
                            checkedItems[3] = false
                        }
                    }
                    1, 2, 3 -> { // Saved, Receipts, Archive
                        if (isChecked) {
                            // Uncheck Inbox when any tag is added
                            checkedItems[0] = false
                        }
                    }
                }
            }
            .setPositiveButton("OK") { _, _ ->
                // Apply the selected tags
                sms.tags.clear()

                if (checkedItems[0]) {
                    // Inbox selected - no tags
                    sms.tags.clear()
                } else {
                    // Add selected tags
                    if (checkedItems[1]) sms.tags.add("saved")
                    if (checkedItems[2]) sms.tags.add("receipts")
                    if (checkedItems[3]) sms.tags.add("archived")
                }

                saveAllSms()
                refreshDisplay()

                val tagNames = when {
                    sms.tags.isEmpty() -> "Inbox"
                    else -> sms.tags.joinToString(", ") { it.capitalize() }
                }
                Toast.makeText(this, "Tags: $tagNames", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun forwardMessage(sms: SmsData) {
        val prefs = getSharedPreferences("forwarding_settings", Context.MODE_PRIVATE)

        val emailEnabled = prefs.getBoolean("email_enabled", false)
        val emailAddress = prefs.getString("email_address", "tregister@hotmail.com") ?: "tregister@hotmail.com"
        val senderEmail = prefs.getString("sender_email", "") ?: ""
        val emailPassword = prefs.getString("email_password", "") ?: ""

        val smsEnabled = prefs.getBoolean("sms_enabled", false)
        val phoneNumber = prefs.getString("phone_number", "0552316516") ?: "0552316516"

        var forwardingCount = 0

        // Forward via email (in background thread)
        if (emailEnabled && emailAddress.isNotEmpty() && senderEmail.isNotEmpty() && emailPassword.isNotEmpty()) {
            forwardingCount++
            thread {
                try {
                    forwardViaEmail(sms, emailAddress, senderEmail, emailPassword)
                    runOnUiThread {
                        Toast.makeText(this, "Email sent to $emailAddress", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        val errorMsg = when {
                            e.message?.contains("535") == true || e.message?.contains("5.7") == true ->
                                "Email failed: Wrong password. Use App Password from account.microsoft.com → Security → App passwords"
                            e.message?.contains("Authentication") == true ->
                                "Email failed: Authentication error. Check your email and App Password in Settings"
                            else -> "Email failed: ${e.message}"
                        }
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Forward via SMS
        if (smsEnabled && phoneNumber.isNotEmpty()) {
            forwardingCount++
            try {
                forwardViaSms(sms, phoneNumber)
                Toast.makeText(this, "SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        if (forwardingCount == 0) {
            Toast.makeText(this, "Configure forwarding in settings first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun forwardViaEmail(sms: SmsData, toEmail: String, fromEmail: String, password: String) {
        // Determine SMTP settings based on sender email domain
        val (smtpHost, smtpPort) = when {
            fromEmail.endsWith("@gmail.com") -> Pair("smtp.gmail.com", "587")
            fromEmail.endsWith("@hotmail.com") || fromEmail.endsWith("@outlook.com") || fromEmail.endsWith("@live.com") ->
                Pair("smtp.office365.com", "587")
            fromEmail.endsWith("@yahoo.com") -> Pair("smtp.mail.yahoo.com", "587")
            else -> Pair("smtp.gmail.com", "587") // default
        }

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort)
            put("mail.smtp.ssl.trust", smtpHost)
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(fromEmail, password)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                subject = "Forwarded SMS from ${sms.sender}"
                setText("""
                    From: ${sms.sender}
                    Time: ${sms.timestamp}

                    Message:
                    ${sms.message}
                """.trimIndent())
            }

            Transport.send(message)
        } catch (e: Exception) {
            throw Exception("Failed to send email: ${e.message}")
        }
    }

    private fun forwardViaSms(sms: SmsData, phoneNumber: String) {
        // Check if we have SMS sending permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            throw Exception("SMS permission not granted")
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }

            val forwardedMessage = "Fwd from ${sms.sender}: ${sms.message}"

            // Split message if it's too long
            val parts = smsManager.divideMessage(forwardedMessage)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, forwardedMessage, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
        } catch (e: Exception) {
            throw Exception("Failed to send SMS: ${e.message}")
        }
    }

    private fun updateStatus() {
        // Status text is hidden - counts shown in tab titles
    }
}
