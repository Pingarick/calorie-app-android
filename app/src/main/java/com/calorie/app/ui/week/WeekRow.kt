package com.calorie.app.ui.week

/** Строка экрана недели: день с итогом или блюдо внутри дня. */
sealed class WeekRow {
    data class Day(
        val offset: Int,
        val dateKey: String,
        val title: String,
        val kcal: Double
    ) : WeekRow()

    data class Dish(
        val slot: String,
        val title: String,
        val leftover: Boolean,
        val kcal: Double
    ) : WeekRow()
}
