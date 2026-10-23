package com.calorie.app.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Продукт с составом на 100 г. Локальный каталог, из него собираются блюда и корзина. */
@Entity(tableName = "product")
data class Product(
    @PrimaryKey val id: String,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double,
    val category: String = "прочее",
    /** Цена за 100 г в рублях. 0 — цена неизвестна. */
    @ColumnInfo(defaultValue = "0") val pricePer100: Double = 0.0
)

/**
 * Блюдо каталога. Состав хранится по ингредиентам, а макросы продублированы
 * на порцию: планировщику не нужно пересчитывать их на каждом шаге.
 */
@Entity(tableName = "dish")
data class Dish(
    @PrimaryKey val id: String,
    val title: String,
    /** Слоты через запятую: BREAKFAST,LUNCH,DINNER,SNACK. */
    val slots: String,
    val cuisine: String = "домашняя",
    val technique: String = "плита",
    val timeMin: Int,
    val baseServings: Int = 1,
    /** Суммарный вес порции, г. */
    val gramsPerServing: Double,
    val kcalPerServing: Double,
    val proteinPerServing: Double,
    val fatPerServing: Double,
    val carbsPerServing: Double
)

/** Ингредиент блюда: сколько грамм продукта нужно на одну порцию. */
@Entity(tableName = "dish_ingredient", primaryKeys = ["dishId", "productId"])
data class DishIngredient(
    val dishId: String,
    val productId: String,
    val gramsPerServing: Double
)

/** Слот плана: конкретное блюдо в конкретный день. */
@Entity(tableName = "plan_item")
data class PlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,
    /** MealSlot.name. */
    val slot: String,
    val dishId: String,
    val servings: Double,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    /** Заблокированная запись не пересобирается автоматически. */
    val locked: Boolean = false,
    /** Отмечено «приготовил» — попало в дневник. */
    val cooked: Boolean = false,
    val orderIndex: Int = 0
)
