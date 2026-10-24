package com.calorie.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.calorie.app.data.local.model.BannedProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface BannedDao {
    @Query("SELECT * FROM banned_product")
    fun observeAll(): Flow<List<BannedProduct>>

    @Query("SELECT * FROM banned_product")
    suspend fun all(): List<BannedProduct>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BannedProduct)

    @Query("DELETE FROM banned_product WHERE productId = :productId")
    suspend fun delete(productId: String)

    @Query("DELETE FROM banned_product")
    suspend fun clear()
}
