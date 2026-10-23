package com.calorie.app.domain.planner

import com.calorie.app.data.local.model.Dish
import com.calorie.app.domain.preferences.DishPreference
import com.calorie.app.domain.preferences.PreferenceEngine
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/** Приём пищи и его доля в дневной калорийности. */
enum class MealSlot(val label: String, val share: Double) {
    BREAKFAST("Завтрак", 0.25),
    LUNCH("Обед", 0.35),
    DINNER("Ужин", 0.30),
    SNACK("Перекус", 0.10);

    companion object {
        fun byName(name: String?): MealSlot = entries.firstOrNull { it.name == name } ?: BREAKFAST
    }
}

data class PlannedMeal(
    val dateKey: String,
    val slot: MealSlot,
    val dish: Dish,
    val servings: Double,
    val orderIndex: Int,
    /** Сколько порций готовится сразу. Для остатков 0. */
    val batchServings: Double = 1.0,
    val leftover: Boolean = false
) {
    val kcal: Double get() = dish.kcalPerServing * servings
    val protein: Double get() = dish.proteinPerServing * servings
    val fat: Double get() = dish.fatPerServing * servings
    val carbs: Double get() = dish.carbsPerServing * servings
    val grams: Double get() = dish.gramsPerServing * servings
}

/**
 * Собирает неделю без единого запроса к модели: перебирает допустимые блюда,
 * подгоняет порции под цель и выбирает вариант с лучшим балансом.
 *
 * Предпочтения влияют на выбор двумя способами: выученный вес блюда и давность
 * готовки. Оба приходят снаружи, планировщик остаётся чистой функцией и тестируется
 * без базы.
 */
object MealPlanner {

    private const val MIN_SERVINGS = 0.5
    private const val MAX_SERVINGS = 2.0
    private const val ATTEMPTS = 80
    private const val MAX_USES = 2

    fun planWeek(
        dates: List<String>,
        targetKcal: Int,
        dishes: List<Dish>,
        seed: Long = 1L,
        preferences: Map<String, DishPreference> = emptyMap(),
        now: Long = System.currentTimeMillis()
    ): List<PlannedMeal> {
        if (dates.isEmpty() || dishes.isEmpty() || targetKcal <= 0) return emptyList()

        val candidates = MealSlot.entries.associateWith { slot ->
            val fit = dishes.filter { slot.name in it.slots.split(',') }
            // Если под слот ничего нет — берём общий пул, но слот останется заполненным.
            if (fit.isEmpty()) dishes else fit
        }
        val random = Random(seed)

        var best: List<PlannedMeal> = emptyList()
        var bestScore = Double.NEGATIVE_INFINITY
        repeat(ATTEMPTS) {
            val attempt = greedy(dates, targetKcal, candidates, preferences, now, random)
            val score = objective(attempt, targetKcal)
            if (score > bestScore) {
                bestScore = score
                best = attempt
            }
        }
        return best
    }

    private fun greedy(
        dates: List<String>,
        targetKcal: Int,
        candidates: Map<MealSlot, List<Dish>>,
        preferences: Map<String, DishPreference>,
        now: Long,
        random: Random
    ): List<PlannedMeal> {
        val usage = HashMap<String, Int>()
        val result = ArrayList<PlannedMeal>(dates.size * MealSlot.entries.size)
        val leftoverDish = HashMap<MealSlot, Dish>()
        val leftoverCount = HashMap<MealSlot, Int>()

        dates.forEachIndexed { dayIndex, date ->
            val day = ArrayList<PlannedMeal>(MealSlot.entries.size)
            for (slot in MealSlot.entries) {
                // Сначала закрываем остатки от прошлой кастрюли: их не готовим и не покупаем.
                val pending = leftoverCount[slot] ?: 0
                val pendingDish = leftoverDish[slot]
                if (pending > 0 && pendingDish != null) {
                    leftoverCount[slot] = pending - 1
                    day += PlannedMeal(
                        dateKey = date,
                        slot = slot,
                        dish = pendingDish,
                        servings = 1.0,
                        orderIndex = slot.ordinal,
                        batchServings = 0.0,
                        leftover = true
                    )
                    continue
                }

                val pool = candidates.getValue(slot)
                val fresh = pool.filter { (usage[it.id] ?: 0) < MAX_USES }
                val from = if (fresh.isEmpty()) pool else fresh
                val dish = from.maxByOrNull { candidate ->
                    val pref = preferences[candidate.id] ?: DishPreference()
                    baseScore(candidate, usage[candidate.id] ?: 0, pref, now) + random.nextDouble() * 0.35
                } ?: continue
                usage[dish.id] = (usage[dish.id] ?: 0) + 1

                // Большое блюдо варится один раз и закрывает до трёх приёмов пищи.
                val daysLeft = dates.size - dayIndex
                val portions = if (dish.batchServings > 1) {
                    minOf(dish.batchServings - 1, daysLeft - 1, 2)
                } else {
                    0
                }
                if (portions > 0) {
                    leftoverDish[slot] = dish
                    leftoverCount[slot] = portions
                }

                day += PlannedMeal(
                    dateKey = date,
                    slot = slot,
                    dish = dish,
                    servings = 1.0,
                    orderIndex = slot.ordinal,
                    batchServings = (1 + portions).toDouble(),
                    leftover = false
                )
            }
            fitDay(day, targetKcal)
            result += day
        }
        return result
    }

    /**
     * Оценка блюда без случайной составляющей. Здесь видно всю логику «привыкания»:
     * вес предпочтения, новизна, штраф за недавнюю готовку, повторы и время.
     */
    internal fun baseScore(
        dish: Dish,
        used: Int,
        pref: DishPreference,
        now: Long
    ): Double {
        val novelty = PreferenceEngine.novelty(pref.lastCookedAt, now)
        val recency = PreferenceEngine.recencyPenalty(pref.lastCookedAt, now)
        val singleSlot = if (dish.slots.split(',').size == 1) 0.2 else 0.0
        val quicker = (60 - dish.timeMin.coerceAtMost(60)) / 300.0
        val bulk = if (dish.batchServings > 1) 0.15 else 0.0
        return pref.weight + novelty * 0.8 - used * 1.2 + recency + singleSlot + quicker + bulk
    }

    /** Масштабирует порции дня под цель, зажимая их в разумный диапазон. */
    private fun fitDay(day: MutableList<PlannedMeal>, targetKcal: Int) {
        if (day.isEmpty()) return
        val current = day.sumOf { it.kcal }
        if (current <= 0.0) return
        val factor = targetKcal / current
        for (i in day.indices) {
            val meal = day[i]
            val servings = (meal.servings * factor).coerceIn(MIN_SERVINGS, MAX_SERVINGS)
            day[i] = meal.copy(servings = round1(servings))
        }
    }

    private fun objective(plan: List<PlannedMeal>, targetKcal: Int): Double {
        if (plan.isEmpty()) return Double.NEGATIVE_INFINITY
        var score = 0.0
        val proteinTarget = targetKcal * 0.30 / 4.0

        for (day in plan.groupBy { it.dateKey }.values) {
            val kcal = day.sumOf { it.kcal }
            score -= abs(kcal - targetKcal) / targetKcal * 2.0
            score += min(day.sumOf { it.protein } / proteinTarget, 1.2) * 0.4
        }

        val usage = plan.groupingBy { it.dish.id }.eachCount()
        score -= usage.values.sumOf { maxOf(0, it - 1) } * 0.8
        score += min(plan.map { it.dish.cuisine }.distinct().size, 4) * 0.1
        return score
    }

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}
