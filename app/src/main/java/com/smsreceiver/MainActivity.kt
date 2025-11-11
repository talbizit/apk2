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
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
    private lateinit var deleteButton: FloatingActionButton
    private lateinit var settingsButton: Button
    private lateinit var markAllReadButton: Button
    private lateinit var tabLayout: TabLayout
    private lateinit var archiveSubfolderRow: LinearLayout
    private lateinit var archiveAllButton: Button
    private lateinit var archiveSpamButton: Button
    private val smsList = mutableListOf<SmsData>()

    private val SMS_PERMISSION_CODE = 100
    private val NOTIFICATION_PERMISSION_CODE = 101

    // ContentObserver to monitor SMS database
    private var smsObserver: ContentObserver? = null
    private var lastSmsId = 0L

    // Track current tab (0 = Inbox, 1 = Saved, 2 = Receipts, 3 = Archive)
    private var currentTab = 0

    // Track Archive subfolder (0 = All, 1 = Spam)
    private var archiveSubfolder = 0

    // Viewport tracking for auto-read
    private val viewportHandler = Handler(Looper.getMainLooper())
    private val visibleMessages = mutableMapOf<SmsData, Runnable>()

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

                val smsData = SmsData(sender, message, date, timestamp, isArchived = false, archiveTimestamp = 0L, tags = mutableSetOf(), isRead = false)
                applyAutoTagging(smsData)
                smsList.add(0, smsData)
                saveAllSms()
                refreshDisplay()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hide action bar for maximum screen space
        supportActionBar?.hide()

        recyclerView = findViewById(R.id.recyclerView)
        statusText = findViewById(R.id.statusText)
        clearButton = findViewById(R.id.clearButton)
        deleteButton = findViewById(R.id.deleteButton)
        settingsButton = findViewById(R.id.settingsButton)
        markAllReadButton = findViewById(R.id.markAllReadButton)
        tabLayout = findViewById(R.id.tabLayout)
        archiveSubfolderRow = findViewById(R.id.archiveSubfolderRow)
        archiveAllButton = findViewById(R.id.archiveAllButton)
        archiveSpamButton = findViewById(R.id.archiveSpamButton)

        groupedAdapter = GroupedSmsAdapter(
            items = mutableListOf(),
            onItemClick = { position -> handleItemClick(position) },
            onItemLongClick = { position -> handleItemLongClick(position) },
            onRefreshNeeded = { refreshDisplay() },
            onSenderClick = { sender -> handleSenderClick(sender) },
            getContactName = { phoneNumber -> getContactName(phoneNumber) },
            onForwardClick = { sms -> forwardMessage(sms) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = groupedAdapter

        // Setup viewport tracking for auto-read
        setupViewportTracking()

        // Setup swipe to archive/restore
        setupSwipeGesture()

        // Setup tab switching
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0

                // Show/hide archive subfolder row
                if (currentTab == 3) {
                    archiveSubfolderRow.visibility = View.VISIBLE
                    archiveSubfolder = 0  // Reset to "All"
                    updateArchiveSubfolderButtons()
                } else {
                    archiveSubfolderRow.visibility = View.GONE
                }

                updateClearButtonText()
                refreshDisplay()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup archive subfolder buttons
        archiveAllButton.setOnClickListener {
            archiveSubfolder = 0
            updateArchiveSubfolderButtons()
            refreshDisplay()
        }

        archiveSpamButton.setOnClickListener {
            archiveSubfolder = 1
            updateArchiveSubfolderButtons()
            refreshDisplay()
        }

        // Clear button is now hidden - functionality moved to long-press menu
        clearButton.setOnClickListener { }

        deleteButton.setOnClickListener {
            val selectedItems = groupedAdapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Delete ${selectedItems.size} message(s)?")
                    .setMessage("Are you sure?")
                    .setPositiveButton("Delete") { _, _ ->
                        // Move selected messages to trash
                        for (selectedMsg in selectedItems) {
                            val sms = smsList.find { it.sender == selectedMsg.sender && it.message == selectedMsg.message && it.timestamp == selectedMsg.timestamp }
                            sms?.let {
                                it.tags.add("trash")
                            }
                        }
                        saveAllSms()
                        exitSelectionMode()
                        refreshDisplay()
                        Toast.makeText(this, "${selectedItems.size} message(s) deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show()
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        markAllReadButton.setOnClickListener {
            // Get messages in current folder only
            val currentFolderMessages = when (currentTab) {
                0 -> smsList.filter { it.tags.isEmpty() && !it.tags.contains("trash") }
                1 -> smsList.filter { it.tags.contains("saved") && !it.tags.contains("archived") && !it.tags.contains("trash") }
                2 -> smsList.filter { it.tags.contains("receipts") && !it.tags.contains("archived") && !it.tags.contains("trash") }
                3 -> {
                    // Archive: filter by subfolder
                    smsList.filter {
                        it.tags.contains("archived") && !it.tags.contains("trash") &&
                        (archiveSubfolder == 0 || (archiveSubfolder == 1 && it.tags.contains("spam")))
                    }
                }
                else -> emptyList()
            }

            val unreadCount = currentFolderMessages.count { !it.isRead }
            if (unreadCount > 0) {
                val folderName = when (currentTab) {
                    0 -> "Inbox"
                    1 -> "Saved"
                    2 -> "Receipts"
                    3 -> if (archiveSubfolder == 1) "Archive (Spam)" else "Archive"
                    else -> "folder"
                }

                AlertDialog.Builder(this)
                    .setTitle("Mark All as Read")
                    .setMessage("Mark $unreadCount unread message(s) in $folderName as read?")
                    .setPositiveButton("📭 Mark as Read") { _, _ ->
                        currentFolderMessages.forEach { it.isRead = true }
                        saveAllSms()
                        refreshDisplay()
                        Toast.makeText(this, "$unreadCount message(s) marked as read", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "No unread messages in current folder", Toast.LENGTH_SHORT).show()
            }
        }

        checkAndRequestPermissions()
        loadStoredSms()
        refreshDisplay()
        updateTabTitles()

        // Trigger initial viewport tracking
        recyclerView.post { updateVisibleMessages() }
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_CONTACTS)
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
                    Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
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
                    val isRead = if (parts.size > 7) parts[7] == "1" else false

                    // Migrate old isArchived to tag system
                    if (isArchived && !tags.contains("archived")) {
                        tags.add("archived")
                    }

                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0], timestampMillis, false, archiveTimestamp, tags, isRead))
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
                    val isRead = if (parts.size > 7) parts[7] == "1" else false

                    // Migrate old isArchived to tag system
                    if (isArchived && !tags.contains("archived")) {
                        tags.add("archived")
                    }

                    smsList.add(SmsData(parts[1], unescapedMessage, parts[0], timestampMillis, false, archiveTimestamp, tags, isRead))
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
                            // Reveal forward button (user must click to actually forward)
                            groupedAdapter.revealForwardButton(position)
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

    private fun setupViewportTracking() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateVisibleMessages()
            }
        })
    }

    private fun updateVisibleMessages() {
        // Check if auto-read is enabled
        val prefs = getSharedPreferences("forwarding_settings", Context.MODE_PRIVATE)
        val autoReadEnabled = prefs.getBoolean("auto_read_enabled", false)
        if (!autoReadEnabled) return

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) return

        // Get currently visible unread messages
        val currentlyVisibleMessages = mutableSetOf<SmsData>()
        for (position in firstVisible..lastVisible) {
            val item = groupedAdapter.getItemAtPosition(position)
            if (item is ListItem.Message && !item.sms.isRead) {
                currentlyVisibleMessages.add(item.sms)
            }
        }

        // Remove runnables for messages no longer visible
        val iterator = visibleMessages.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!currentlyVisibleMessages.contains(entry.key)) {
                viewportHandler.removeCallbacks(entry.value)
                iterator.remove()
            }
        }

        // Schedule mark-as-read for newly visible messages
        for (sms in currentlyVisibleMessages) {
            if (!visibleMessages.containsKey(sms)) {
                val runnable = Runnable {
                    sms.isRead = true
                    saveAllSms()
                    refreshDisplay()
                    visibleMessages.remove(sms)
                }
                visibleMessages[sms] = runnable
                viewportHandler.postDelayed(runnable, 5000) // 5 seconds
            }
        }
    }

    private fun refreshDisplay() {
        val items = when (currentTab) {
            0 -> groupByInbox()      // No tags + not archived
            1 -> groupBySaved()      // "saved" tag + not archived
            2 -> groupByReceipts()   // "receipts" tag + not archived
            3 -> groupByArchive()    // Archived (filtered by subfolder)
            else -> groupByInbox()
        }

        // Show empty state if no messages
        if (items.isEmpty()) {
            val emptyMessage = when (currentTab) {
                0 -> "No messages in Inbox"
                1 -> "No saved messages"
                2 -> "No receipts"
                3 -> if (archiveSubfolder == 1) "No spam in Archive" else "Archive is empty"
                else -> "No messages"
            }
            val emptyItems = listOf(ListItem.Header(emptyMessage))
            groupedAdapter.restoreCollapsedState(emptyItems)
        } else {
            groupedAdapter.restoreCollapsedState(items)
        }
        groupedAdapter.notifyDataSetChanged()
        updateTabTitles()
    }

    private fun updateTabTitles() {
        val inboxMessages = smsList.filter { it.tags.isEmpty() && !it.tags.contains("trash") }
        val savedMessages = smsList.filter { it.tags.contains("saved") && !it.tags.contains("archived") && !it.tags.contains("trash") }
        val receiptsMessages = smsList.filter { it.tags.contains("receipts") && !it.tags.contains("archived") && !it.tags.contains("trash") }
        val archiveMessages = smsList.filter { it.tags.contains("archived") && !it.tags.contains("trash") }

        val inboxCount = inboxMessages.size
        val inboxUnread = inboxMessages.count { !it.isRead }
        val savedCount = savedMessages.size
        val savedUnread = savedMessages.count { !it.isRead }
        val receiptsCount = receiptsMessages.size
        val receiptsUnread = receiptsMessages.count { !it.isRead }
        val archiveCount = archiveMessages.size
        val archiveUnread = archiveMessages.count { !it.isRead }

        tabLayout.getTabAt(0)?.text = formatTabTitle("Inbox", inboxCount, inboxUnread)
        tabLayout.getTabAt(1)?.text = formatTabTitle("Saved", savedCount, savedUnread)
        tabLayout.getTabAt(2)?.text = formatTabTitle("Receipts", receiptsCount, receiptsUnread)
        tabLayout.getTabAt(3)?.text = formatTabTitle("Archive", archiveCount, archiveUnread)
    }

    private fun formatTabTitle(name: String, total: Int, unread: Int): String {
        return if (unread > 0) {
            "$name ($total, +$unread new)"
        } else {
            "$name ($total)"
        }
    }

    private fun groupByInbox(): List<ListItem> {
        // Inbox = messages with no tags at all (excluding trash)
        val inboxMessages = smsList.filter { it.tags.isEmpty() && !it.tags.contains("trash") }
        return groupBySender(inboxMessages)
    }

    private fun groupBySaved(): List<ListItem> {
        // Saved = messages with "saved" tag BUT NOT archived or trash
        // Archived and trash messages only appear in Archive/Trash tabs
        val savedMessages = smsList.filter { it.tags.contains("saved") && !it.tags.contains("archived") && !it.tags.contains("trash") }
        return groupBySender(savedMessages)
    }

    private fun groupByReceipts(): List<ListItem> {
        // Receipts = messages with "receipts" tag BUT NOT archived or trash
        // Archived and trash messages only appear in Archive/Trash tabs
        val receiptsMessages = smsList.filter { it.tags.contains("receipts") && !it.tags.contains("archived") && !it.tags.contains("trash") }
        return groupBySender(receiptsMessages)
    }

    private fun groupByArchive(): List<ListItem> {
        // Archive = messages with "archived" tag BUT NOT trash (may have other tags too)
        // Filtered by subfolder: 0 = All, 1 = Spam only
        val archivedMessages = smsList.filter {
            it.tags.contains("archived") && !it.tags.contains("trash") &&
            (archiveSubfolder == 0 || (archiveSubfolder == 1 && it.tags.contains("spam")))
        }
        return groupByTimeCategories(archivedMessages)
    }

    private fun groupBySender(messages: List<SmsData>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        val grouped = messages.groupBy { it.sender }

        for ((sender, senderMessages) in grouped) {
            val count = senderMessages.size
            val hasUnread = senderMessages.any { !it.isRead }
            val asterisk = if (hasUnread) "*" else ""
            val headerTitle = "$asterisk$sender ($count)"
            result.add(ListItem.Header(headerTitle))
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
                val categoryCount = categoryMessages.size
                val categoryHasUnread = categoryMessages.any { !it.isRead }
                val categoryAsterisk = if (categoryHasUnread) "*" else ""
                result.add(ListItem.Header("$categoryAsterisk$category ($categoryCount)"))
                // Group by sender within each time category
                val senderGroups = categoryMessages.groupBy { it.sender }
                for ((sender, senderMessages) in senderGroups) {
                    val senderCount = senderMessages.size
                    val senderHasUnread = senderMessages.any { !it.isRead }
                    val senderAsterisk = if (senderHasUnread) "*" else ""
                    result.add(ListItem.Header("  $senderAsterisk$sender ($senderCount)"))
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
            val isReadString = if (sms.isRead) "1" else "0"
            // Format: timestamp|sender|message|deprecated|archiveTimestamp|timestampMillis|tags|isRead
            "${sms.timestamp}|${sms.sender}|$escapedMessage|0|${sms.archiveTimestamp}|${sms.timestampMillis}|$tagsString|$isReadString"
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
                    val smsData = SmsData(address, body, date, timestamp, isArchived = false, archiveTimestamp = 0L, tags = mutableSetOf(), isRead = false)
                    applyAutoTagging(smsData)
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

        // For messages in other tabs (not Archive): show full tags dialog
        if (item is ListItem.Message && currentTab != 3) {
            showMoveToDialog(item.sms)
            return true
        }

        // For Archive tab: enter selection mode for both headers and messages
        if (currentTab == 3 && !isSelectionMode) {
            when (item) {
                is ListItem.Header -> {
                    enterSelectionMode()
                    item.isSelected = true
                    selectGroupItems(position, true)
                    groupedAdapter.notifyDataSetChanged()
                    updateSelectionCount()
                    return true
                }
                is ListItem.Message -> {
                    enterSelectionMode()
                    item.isSelected = true
                    groupedAdapter.notifyDataSetChanged()
                    updateSelectionCount()
                    return true
                }
                else -> {}
            }
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
        // Count non-archived messages with this tag
        val tagCount = smsList.count { it.tags.contains(tag) && !it.tags.contains("archived") }

        if (tagCount == 0) {
            Toast.makeText(this, "$tagDisplayName folder is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear $tagDisplayName Folder")
            .setMessage("Remove $tagCount message(s) from $tagDisplayName folder?\n\nMessages will move to Inbox or remain in other folders.")
            .setPositiveButton("Clear") { _, _ ->
                // Remove tag from all non-archived messages that have it
                smsList.forEach {
                    if (it.tags.contains(tag) && !it.tags.contains("archived")) {
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
        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null)

        // Create custom view
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        // Buttons row (Inbox and Archive)
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }

        val inboxButton = Button(this).apply {
            text = "📥 Inbox"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = 8
            }
        }

        val archiveButton = Button(this).apply {
            text = if (sms.tags.contains("archived")) "📂 Unarchive" else "📂 Archive"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 8
            }
        }

        buttonRow.addView(inboxButton)
        buttonRow.addView(archiveButton)
        layout.addView(buttonRow)

        // Mark Read/Unread button
        val readButton = Button(this).apply {
            text = if (sms.isRead) "📩 Mark as Unread" else "📭 Mark as Read"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }
        layout.addView(readButton)

        // Load custom tags from settings
        val tagPrefs = getSharedPreferences("tag_settings", Context.MODE_PRIVATE)
        val customTagsString = tagPrefs.getString("custom_tags", "") ?: ""
        val customTags = if (customTagsString.isNotEmpty()) {
            customTagsString.split(",")
        } else {
            emptyList()
        }

        // Create checkboxes for default and custom tags
        val allTags = listOf("saved", "receipts", "spam") + customTags
        val checkboxes = mutableMapOf<String, CheckBox>()

        for (tag in allTags) {
            val checkbox = CheckBox(this).apply {
                text = tag.capitalize()
                isChecked = sms.tags.contains(tag)
                textSize = 16f
                setPadding(0, 10, 0, 10)
            }
            checkboxes[tag] = checkbox
            layout.addView(checkbox)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Tags")
            .setView(layout)
            .setNegativeButton("✕ Close", null)
            .create()

        // Inbox button click - remove all tags
        inboxButton.setOnClickListener {
            sms.tags.clear()
            saveAllSms()
            refreshDisplay()
            Toast.makeText(this, "All tags removed → Inbox", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Archive button click - toggle archived tag
        archiveButton.setOnClickListener {
            if (sms.tags.contains("archived")) {
                // Unarchive - remove archived tag, keep others
                sms.tags.remove("archived")
                sms.archiveTimestamp = 0L
                Toast.makeText(this, "Unarchived", Toast.LENGTH_SHORT).show()
            } else {
                // Archive - add archived tag
                sms.tags.add("archived")
                sms.archiveTimestamp = System.currentTimeMillis()
                Toast.makeText(this, "Archived", Toast.LENGTH_SHORT).show()
            }
            saveAllSms()
            refreshDisplay()
            dialog.dismiss()
        }

        // Mark Read/Unread button click
        readButton.setOnClickListener {
            sms.isRead = !sms.isRead
            saveAllSms()
            refreshDisplay()
            val status = if (sms.isRead) "read" else "unread"
            Toast.makeText(this, "Marked as $status", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Set checkbox listeners for all tags
        for ((tag, checkbox) in checkboxes) {
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sms.tags.add(tag)

                    // Special handling for spam tag
                    if (tag == "spam") {
                        dialog.dismiss()
                        showAddToSpamListDialog(sms.sender)
                    }
                } else {
                    sms.tags.remove(tag)
                }
                saveAllSms()
                refreshDisplay()
                val action = if (isChecked) "added" else "removed"
                Toast.makeText(this, "${tag.capitalize()} tag $action", Toast.LENGTH_SHORT).show()

                if (tag != "spam") {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showAddToSpamListDialog(sender: String) {
        // Check if sender is already in spam list
        val spamPrefs = getSharedPreferences("spam_settings", Context.MODE_PRIVATE)
        val spamSendersString = spamPrefs.getString("spam_senders", "") ?: ""
        val spamSenders = if (spamSendersString.isNotEmpty()) {
            spamSendersString.split(",").toMutableSet()
        } else {
            mutableSetOf()
        }

        if (spamSenders.contains(sender)) {
            // Already in spam list
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Add to Spam List?")
            .setMessage("Add \"$sender\" to spam list?\n\nFuture messages from this sender will automatically be marked as spam.")
            .setPositiveButton("Add to Spam List") { _, _ ->
                spamSenders.add(sender)
                spamPrefs.edit().putString("spam_senders", spamSenders.joinToString(",")).apply()
                Toast.makeText(this, "\"$sender\" added to spam list", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun updateArchiveSubfolderButtons() {
        // Highlight selected subfolder button
        if (archiveSubfolder == 0) {
            archiveAllButton.setTextColor(0xFFFFFFFF.toInt())
            archiveSpamButton.setTextColor(0xFFB0B0B0.toInt())
        } else {
            archiveAllButton.setTextColor(0xFFB0B0B0.toInt())
            archiveSpamButton.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun handleSenderClick(sender: String) {
        // Check if sender is a contact
        val contactName = getContactName(sender)
        if (contactName == null) {
            // Not a contact, show dialog to add
            showAddContactDialog(sender)
        } else {
            // Already a contact, show toast
            Toast.makeText(this, "Contact: $contactName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        // Try multiple lookup strategies for better contact matching
        val strategies = listOf(
            phoneNumber,  // Original number
            phoneNumber.replace(Regex("[^0-9]"), ""),  // Remove all non-digits
            if (phoneNumber.startsWith("0")) phoneNumber.substring(1) else null,  // Remove leading 0
            if (!phoneNumber.startsWith("+")) "+972${phoneNumber.removePrefix("0")}" else null  // Try with country code
        ).filterNotNull()

        for (number in strategies) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            try {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            // Return name only if it's different from the phone number
                            if (name != null && !name.matches(Regex("[0-9+\\-\\s()]+"))) {
                                return name
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun showAddContactDialog(phoneNumber: String) {
        val input = EditText(this).apply {
            hint = "Contact name"
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("Add Contact")
            .setMessage("Add \"$phoneNumber\" to contacts?")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val contactName = input.text.toString().trim()
                if (contactName.isNotEmpty()) {
                    addContact(contactName, phoneNumber)
                } else {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addContact(name: String, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            }
            startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to add contact", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Contact was added, refresh display to show names
            Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show()
            refreshDisplay()
        }
    }

    private fun applyAutoTagging(sms: SmsData) {
        // Load all tag keywords from SharedPreferences
        val keywordsPrefs = getSharedPreferences("tag_keywords", Context.MODE_PRIVATE)

        // Get all possible tags (default + custom)
        val tagPrefs = getSharedPreferences("tag_settings", Context.MODE_PRIVATE)
        val customTagsString = tagPrefs.getString("custom_tags", "") ?: ""
        val customTags = if (customTagsString.isNotEmpty()) {
            customTagsString.split(",").toSet()
        } else {
            emptySet()
        }

        val defaultTags = setOf("saved", "receipts", "archived", "spam")
        val allTags = defaultTags + customTags

        // Check each tag's keywords
        for (tag in allTags) {
            val keywordsString = keywordsPrefs.getString(tag, "") ?: ""
            if (keywordsString.isNotEmpty()) {
                val keywords = keywordsString.split(",")
                // Check if message contains any of the keywords
                if (keywords.any { keyword ->
                    sms.message.contains(keyword, ignoreCase = true) ||
                    sms.sender.contains(keyword, ignoreCase = true)
                }) {
                    sms.tags.add(tag)
                }
            }
        }

        // For receipts, initialize default keywords if not set
        if (!keywordsPrefs.contains("receipts")) {
            val defaultReceiptKeywords = listOf("חשבונית", "קבלה", "שובר")
            if (defaultReceiptKeywords.any { sms.message.contains(it, ignoreCase = true) }) {
                sms.tags.add("receipts")
            }
        }

        // Also check spam sender list for backward compatibility
        val spamPrefs = getSharedPreferences("spam_settings", Context.MODE_PRIVATE)
        val spamSendersString = spamPrefs.getString("spam_senders", "") ?: ""
        if (spamSendersString.isNotEmpty()) {
            val spamSenders = spamSendersString.split(",").toSet()
            if (spamSenders.contains(sms.sender)) {
                sms.tags.add("spam")
            }
        }
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
