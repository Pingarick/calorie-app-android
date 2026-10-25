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
        val banned = db.bannedDao().all().map { it.productId }.toSet()
        val ingredientsByDish = db.dishDao().allIngredients().groupBy { it.dishId }
        val dishes = db.dishDao().all().filter { dish ->
            ingredientsByDish[dish.id].orEmpty().none { it.productId in banned }
        }
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
        // Если меняем день готовки, остатки перестают быть остатками:
        // готовим их отдельно, иначе продукты на них не попадут в корзину.
        if (item.leftover) shrinkBatch(item) else releaseLeftovers(item)

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
            batchServings = 1.0,
            leftover = false,
            cooked = false
        )
        db.planDao().deleteById(item.id)
        db.planDao().insertAll(listOf(updated))
        return updated
    }

    suspend fun remove(item: PlanItem) {
        if (item.leftover) shrinkBatch(item) else releaseLeftovers(item)
        db.planDao().deleteById(item.id)
    }

    /**
     * Остатки от кастрюли, которую больше не готовим, становятся обычными порциями:
     * их продукты снова попадают в корзину, а день не остаётся с пустым слотом.
     */
    private suspend fun releaseLeftovers(head: PlanItem) {
        if (head.batchServings <= 1.0) return
        val week = db.planDao().range(head.dateKey, DateUtils.keyFor(6))
        week.asSequence()
            .filter {
                it.dateKey > head.dateKey &&
                    it.slot == head.slot &&
                    it.dishId == head.dishId &&
                    it.leftover
            }
            .take((head.batchServings - 1).toInt().coerceAtLeast(0))
            .forEach { db.planDao().updateBatch(it.id, 1.0, false) }
    }

    /** Убрали одну порцию из кастрюли — уменьшаем и закупку на неё. */
    private suspend fun shrinkBatch(leftover: PlanItem) {
        val before = db.planDao().range(DateUtils.keyFor(0), leftover.dateKey)
        val head = before.lastOrNull {
            it.dateKey < leftover.dateKey &&
                it.slot == leftover.slot &&
                it.dishId == leftover.dishId &&
                !it.leftover
        } ?: return
        if (head.batchServings > 1.0) {
            db.planDao().updateBatchServings(head.id, head.batchServings - 1.0)
        }
    }

    /** Сколько блюд останется с учётом запрещённых продуктов. */
    suspend fun allowedDishCount(): Int {
        val banned = db.bannedDao().all().map { it.productId }.toSet()
        val ingredientsByDish = db.dishDao().allIngredients().groupBy { it.dishId }
        return db.dishDao().all().count { dish ->
            ingredientsByDish[dish.id].orEmpty().none { it.productId in banned }
        }
    }

    /**
     * Минимум вариантов по приёмам пищи. Если лук вырезал все супы, общий счёт
     * может остаться большим, а обед — без выбора. Для предупреждения важнее минимум.
     */
    suspend fun minDishesPerSlot(): Int {
        val banned = db.bannedDao().all().map { it.productId }.toSet()
        val ingredientsByDish = db.dishDao().allIngredients().groupBy { it.dishId }
        val allowed = db.dishDao().all().filter { dish ->
            ingredientsByDish[dish.id].orEmpty().none { it.productId in banned }
        }
        if (allowed.isEmpty()) return 0
        return MealSlot.entries.minOf { slot ->
            allowed.count { slot.name in it.slots.split(',') }
        }
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
        batchServings = batchServings,
        leftover = leftover,
        orderIndex = orderIndex
    )

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
