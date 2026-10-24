package com.calorie.app.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Продукт, который не предлагать: «не люблю лук» убирает все блюда с ним. */
@Entity(tableName = "banned_product")
data class BannedProduct(
    @PrimaryKey val productId: String
)
