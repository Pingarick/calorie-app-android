package com.calorie.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.calorie.app.data.local.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_message ORDER BY id")
    fun observeAll(): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_message")
    suspend fun clear()
}
