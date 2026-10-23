package com.calorie.app.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Пошаговый рецепт блюда. Ингредиенты и граммовки живут в dish_ingredient,
 * поэтому здесь только шаги: их генерирует модель и кэширует, чтобы не платить дважды.
 */
@Entity(tableName = "recipe")
data class Recipe(
    @PrimaryKey val dishId: String,
    /** JSON вида {"steps":[{"text":"...","timer_min":10}]}. */
    val stepsJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
