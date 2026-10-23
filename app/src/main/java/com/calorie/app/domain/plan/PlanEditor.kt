package com.calorie.app.domain.plan

import com.calorie.app.data.local.AppDatabase
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.domain.planner.MealPlanner
import com.calorie.app.domain.planner.MealSlot
import com.calorie.app.domain.planner.PlannedMeal
import com.calorie.app.domain.preferences.DishPreference
import com.calorie.app.util.DateUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Операции над планом, общие для экрана рациона и чата: пересборка недели,
 * замена и удаление блюда. Никакой логики интерфейса — только данные.
 */
class PlanEditor(private val db: AppDatabase) {

    suspend fun itemFor(dateKey: String, slot: String): PlanItem? =
        db.planDao().forDate(dateKey).firstOrNull { it.slot == slot }

    suspend fun regenerate(
        dates: List<String>,
        targetKcal: Int,
        preferences: Map<String, DishPreference>,
        seed: Long = System.currentTimeMillis()
    ): Boolean {
        val dishes = db.dishDao().all()
        if (dishes.isEmpty()) return false
        val planned = MealPlanner.planWeek(
            dates = dates,
            targetKcal = targetKcal,
            dishes = dishes,
            seed = seed,
            preferences = preferences
        )
        if (planned.isEmpty()) return false
        db.planDao().clearRange(dates.first(), dates.last())
        db.planDao().insertAll(planned.map { it.toEntity() })
        return true
    }

    /** Меняет блюдо на ближайшее по калорийности, стараясь не повторять его в неделе. */
    suspend fun replace(item: PlanItem): PlanItem? {
        val all = db.dishDao().all()
        val week = db.planDao().range(DateUtils.keyFor(0), DateUtils.keyFor(6))
        val slot = MealSlot.byName(item.slot)
        val usage = week.groupingBy { it.dishId }.eachCount()
        val candidates = all
            .filter { slot.name in it.slots.split(',') && it.id != item.dishId }
            .sortedBy { abs(it.kcalPerServing - item.kcal) }
        val next = candidates.firstOrNull { (usage[it.id] ?: 0) < 2 }
            ?: candidates.firstOrNull()
            ?: return null

        val servings = (item.kcal / next.kcalPerServing).coerceIn(0.5, 2.0)
        val updated = item.copy(
            id = 0,
            dishId = next.id,
            servings = round1(servings),
            grams = next.gramsPerServing * servings,
            kcal = next.kcalPerServing * servings,
            protein = next.proteinPerServing * servings,
            fat = next.fatPerServing * servings,
            carbs = next.carbsPerServing * servings,
            cooked = false
        )
        db.planDao().deleteById(item.id)
        db.planDao().insertAll(listOf(updated))
        return updated
    }

    suspend fun remove(item: PlanItem) {
        db.planDao().deleteById(item.id)
    }

    private fun PlannedMeal.toEntity() = PlanItem(
        dateKey = dateKey,
        slot = slot.name,
        dishId = dish.id,
        servings = servings,
        grams = grams,
        kcal = kcal,
        protein = protein,
        fat = fat,
        carbs = carbs,
        orderIndex = orderIndex
    )

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
