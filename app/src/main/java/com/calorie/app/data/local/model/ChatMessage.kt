package com.calorie.app.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Сообщение встроенного чата. actionJson хранит применённое действие, если оно было. */
@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "user" или "assistant". */
    val role: String,
    val text: String,
    val actionJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
