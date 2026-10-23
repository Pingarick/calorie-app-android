package com.calorie.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.calorie.app.data.local.model.PantryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_item")
    fun observeAll(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_item")
    suspend fun all(): List<PantryItem>

    @Upsert
    suspend fun upsert(item: PantryItem)

    @Query("DELETE FROM pantry_item WHERE productId = :productId")
    suspend fun delete(productId: String)

    @Query("DELETE FROM pantry_item")
    suspend fun clear()
}
