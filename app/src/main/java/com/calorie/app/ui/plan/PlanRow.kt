package com.calorie.app.ui.plan

import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.domain.planner.MealSlot

/** Строка экрана рациона: заголовок слота, блюдо или пустой слот. */
sealed class PlanRow {
    data class MealHeader(val slot: MealSlot, val kcal: Double) : PlanRow()
    data class EntryRow(val item: PlanItem, val dish: Dish) : PlanRow()
    data class EmptySlot(val slot: MealSlot) : PlanRow()
}
