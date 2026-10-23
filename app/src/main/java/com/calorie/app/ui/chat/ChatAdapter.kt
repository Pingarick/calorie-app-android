package com.calorie.app.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.calorie.app.R
import com.calorie.app.data.local.model.ChatMessage
import com.calorie.app.databinding.ItemChatMessageBinding

/** Пузыри чата: вопрос справа, ответ помощника слева. */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.Holder>() {

    private var rows: List<ChatMessage> = emptyList()

    fun submit(messages: List<ChatMessage>) {
        rows = messages
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

    inner class Holder(private val b: ItemChatMessageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(message: ChatMessage) {
            val context = b.root.context
            val isUser = message.role == "user"
            b.txtMessage.text = message.text
            b.root.gravity = if (isUser) Gravity.END else Gravity.START
            b.bubble.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (isUser) R.color.calorie_primary_container else R.color.card
                )
            )
        }
    }
}
