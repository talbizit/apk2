package com.smsreceiver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SmsAdapter(private val smsList: MutableList<SmsData>) :
    RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {

    class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderText: TextView = view.findViewById(R.id.senderText)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms, parent, false)
        return SmsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val sms = smsList[position]
        holder.senderText.text = sms.sender
        holder.messageText.text = sms.message
        holder.timestampText.text = sms.timestamp
    }

    override fun getItemCount() = smsList.size

    fun addSms(sms: SmsData) {
        smsList.add(0, sms)
        notifyItemInserted(0)
    }

    fun clearAll() {
        smsList.clear()
        notifyDataSetChanged()
    }
}
