package com.smsreceiver

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class ListItem {
    data class Header(val title: String, var isSelected: Boolean = false, var isCollapsed: Boolean = false) : ListItem()
    data class Message(val sms: SmsData, var isSelected: Boolean = false) : ListItem()
}

class GroupedSmsAdapter(
    private val items: MutableList<ListItem>,
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Boolean,
    private val onRefreshNeeded: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_MESSAGE = 1
    }

    var isSelectionMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.headerText)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
        val collapseIcon: TextView = view.findViewById(R.id.collapseIcon)
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderText: TextView = view.findViewById(R.id.senderText)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.Header -> VIEW_TYPE_HEADER
            is ListItem.Message -> VIEW_TYPE_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_sms, parent, false)
                MessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.headerText.text = item.title
                headerHolder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                headerHolder.checkBox.isChecked = item.isSelected
                headerHolder.checkBox.setOnCheckedChangeListener(null) // Remove old listener
                headerHolder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    item.isSelected = isChecked
                    onItemClick(position)
                }

                // Update collapse icon rotation
                headerHolder.collapseIcon.rotation = if (item.isCollapsed) -90f else 0f

                holder.itemView.setOnClickListener {
                    if (isSelectionMode) {
                        onItemClick(position)
                    } else {
                        // Toggle collapse/expand
                        toggleCollapse(position)
                    }
                }
                holder.itemView.setOnLongClickListener {
                    onItemLongClick(position)
                }
            }
            is ListItem.Message -> {
                val messageHolder = holder as MessageViewHolder
                messageHolder.senderText.text = item.sms.sender
                messageHolder.messageText.text = item.sms.message
                messageHolder.timestampText.text = item.sms.timestamp

                // Enable link clicking when not in selection mode
                if (isSelectionMode) {
                    messageHolder.messageText.movementMethod = null
                } else {
                    messageHolder.messageText.movementMethod = LinkMovementMethod.getInstance()
                }

                messageHolder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                messageHolder.checkBox.isChecked = item.isSelected
                messageHolder.checkBox.setOnCheckedChangeListener(null) // Remove old listener
                messageHolder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    item.isSelected = isChecked
                }
                holder.itemView.setOnClickListener {
                    if (isSelectionMode) {
                        item.isSelected = !item.isSelected
                        messageHolder.checkBox.isChecked = item.isSelected
                        onItemClick(position)
                    }
                }
                holder.itemView.setOnLongClickListener {
                    onItemLongClick(position)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItemAtPosition(position: Int): ListItem? {
        return if (position in items.indices) items[position] else null
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getSelectedItems(): List<SmsData> {
        return items.filterIsInstance<ListItem.Message>()
            .filter { it.isSelected }
            .map { it.sms }
    }

    fun clearSelections() {
        items.forEach {
            when (it) {
                is ListItem.Header -> it.isSelected = false
                is ListItem.Message -> it.isSelected = false
            }
        }
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int {
        return items.filterIsInstance<ListItem.Message>().count { it.isSelected }
    }

    private fun toggleCollapse(headerPosition: Int) {
        val headerItem = items.getOrNull(headerPosition) as? ListItem.Header ?: return

        // Toggle the collapse state
        headerItem.isCollapsed = !headerItem.isCollapsed

        if (headerItem.isCollapsed) {
            // Find all child messages under this header and remove them
            val childPositions = mutableListOf<Int>()
            var pos = headerPosition + 1
            while (pos < items.size) {
                when (items[pos]) {
                    is ListItem.Header -> break // Stop at next header
                    is ListItem.Message -> {
                        childPositions.add(pos)
                        pos++
                    }
                }
            }

            // Remove child messages from display
            childPositions.reversed().forEach { position ->
                items.removeAt(position)
            }
            notifyItemChanged(headerPosition) // Update icon
            notifyItemRangeRemoved(headerPosition + 1, childPositions.size)
        } else {
            // Expanding - need to restore items from original data
            notifyItemChanged(headerPosition) // Update icon first
            onRefreshNeeded() // Request MainActivity to refresh with expanded state
        }
    }

    fun restoreCollapsedState(newItems: List<ListItem>) {
        // Preserve collapse state when items are updated
        val collapsedHeaders = items.filterIsInstance<ListItem.Header>()
            .filter { it.isCollapsed }
            .map { it.title }
            .toSet()

        items.clear()

        val filteredItems = mutableListOf<ListItem>()
        var i = 0
        while (i < newItems.size) {
            val item = newItems[i]
            if (item is ListItem.Header) {
                // Restore collapsed state if it was previously collapsed
                if (item.title in collapsedHeaders) {
                    item.isCollapsed = true
                    filteredItems.add(item)
                    // Skip child messages
                    i++
                    while (i < newItems.size && newItems[i] is ListItem.Message) {
                        i++
                    }
                    continue
                }
            }
            filteredItems.add(item)
            i++
        }

        items.addAll(filteredItems)
    }
}
