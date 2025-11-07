package com.smsreceiver

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class ListItem {
    data class Header(val title: String, var isSelected: Boolean = false) : ListItem()
    data class Message(val sms: SmsData, var isSelected: Boolean = false) : ListItem()
}

class GroupedSmsAdapter(
    private val items: MutableList<ListItem>,
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Boolean
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
                holder.itemView.setOnClickListener {
                    if (isSelectionMode) onItemClick(position)
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
}
