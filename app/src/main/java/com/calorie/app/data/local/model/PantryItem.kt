package com.calorie.app.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Что уже есть дома: граммы продукта, которые не нужно покупать. */
@Entity(tableName = "pantry_item")
data class PantryItem(
    @PrimaryKey val productId: String,
    val grams: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
