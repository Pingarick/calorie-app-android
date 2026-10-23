package com.calorie.app

import com.calorie.app.data.local.model.Dish
import com.calorie.app.domain.planner.MealPlanner
import com.calorie.app.domain.planner.MealSlot
import com.calorie.app.domain.preferences.DishPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MealPlannerTest {

    private fun dish(
        id: String,
        slot: MealSlot,
        kcal: Double,
        protein: Double = 25.0,
        timeMin: Int = 20
    ) = Dish(
        id = id,
        title = "Блюдо $id",
        slots = slot.name,
        cuisine = "домашняя",
        technique = "плита",
        timeMin = timeMin,
        baseServings = 1,
        gramsPerServing = 300.0,
        kcalPerServing = kcal,
        proteinPerServing = protein,
        fatPerServing = kcal * 0.3 / 9.0,
        carbsPerServing = kcal * 0.4 / 4.0
    )

    private val dishes = listOf(
        dish("b1", MealSlot.BREAKFAST, 350.0),
        dish("b2", MealSlot.BREAKFAST, 420.0),
        dish("b3", MealSlot.BREAKFAST, 300.0),
        dish("b4", MealSlot.BREAKFAST, 460.0),
        dish("l1", MealSlot.LUNCH, 600.0),
        dish("l2", MealSlot.LUNCH, 520.0),
        dish("l3", MealSlot.LUNCH, 680.0),
        dish("l4", MealSlot.LUNCH, 450.0),
        dish("d1", MealSlot.DINNER, 540.0),
        dish("d2", MealSlot.DINNER, 410.0),
        dish("d3", MealSlot.DINNER, 620.0),
        dish("d4", MealSlot.DINNER, 480.0),
        dish("s1", MealSlot.SNACK, 180.0),
        dish("s2", MealSlot.SNACK, 250.0),
        dish("s3", MealSlot.SNACK, 150.0),
        dish("s4", MealSlot.SNACK, 210.0)
    )

    private val dates = (0..6).map { "2026-10-${20 + it}" }

    @Test
    fun weekHasFourMealsPerDay() {
        val plan = MealPlanner.planWeek(dates, 2000, dishes, seed = 7L)
        assertEquals(dates.size * MealSlot.entries.size, plan.size)
        dates.forEach { date ->
            val slots = plan.filter { it.dateKey == date }.map { it.slot }
            assertEquals(MealSlot.entries.toSet(), slots.toSet())
        }
    }

    @Test
    fun dailyCaloriesStayCloseToTarget() {
        val target = 2000
        val plan = MealPlanner.planWeek(dates, target, dishes, seed = 7L)
        plan.groupBy { it.dateKey }.forEach { (date, meals) ->
            val kcal = meals.sumOf { it.kcal }
            assertTrue(
                "день $date: $kcal ккал при цели $target",
                abs(kcal - target) <= target * 0.10
            )
        }
    }

    @Test
    fun sameSeedGivesSamePlan() {
        val first = MealPlanner.planWeek(dates, 2000, dishes, seed = 42L)
        val second = MealPlanner.planWeek(dates, 2000, dishes, seed = 42L)
        val key = { meals: List<com.calorie.app.domain.planner.PlannedMeal> ->
            meals.map { "${it.dateKey}|${it.slot}|${it.dish.id}|${it.servings}" }
        }
        assertEquals(key(first), key(second))
    }

    @Test
    fun dishRepeatsAtMostTwice() {
        val plan = MealPlanner.planWeek(dates, 2000, dishes, seed = 3L)
        val usage = plan.groupingBy { it.dish.id }.eachCount()
        assertTrue("повторы: $usage", usage.values.all { it <= 2 })
    }

    @Test
    fun emptyInputProducesEmptyPlan() {
        assertTrue(MealPlanner.planWeek(emptyList(), 2000, dishes).isEmpty())
        assertTrue(MealPlanner.planWeek(dates, 2000, emptyList()).isEmpty())
    }

    @Test
    fun preferenceWeightOrdersDishes() {
        val now = 1_000_000_000_000L
        val dish = dish("x", MealSlot.LUNCH, 500.0)
        val liked = MealPlanner.baseScore(dish, 0, DishPreference(weight = 1.0), now)
        val neutral = MealPlanner.baseScore(dish, 0, DishPreference(), now)
        val disliked = MealPlanner.baseScore(dish, 0, DishPreference(weight = -1.0), now)
        assertTrue("liked=$liked neutral=$neutral disliked=$disliked", liked > neutral && neutral > disliked)
    }

    @Test
    fun recentlyCookedDishScoresLower() {
        val now = 1_000_000_000_000L
        val dish = dish("x", MealSlot.LUNCH, 500.0)
        val monthAgo = MealPlanner.baseScore(dish, 0, DishPreference(lastCookedAt = now - 30 * 86_400_000L), now)
        val justNow = MealPlanner.baseScore(dish, 0, DishPreference(lastCookedAt = now), now)
        assertTrue("justNow=$justNow monthAgo=$monthAgo", monthAgo > justNow)
    }

    @Test
    fun batchDishProducesLeftovers() {
        val soup = dish("soup", MealSlot.LUNCH, 300.0).copy(batchServings = 4)
        val pool = listOf(
            dish("b1", MealSlot.BREAKFAST, 350.0),
            dish("b2", MealSlot.BREAKFAST, 400.0),
            soup,
            dish("d1", MealSlot.DINNER, 540.0),
            dish("d2", MealSlot.DINNER, 410.0),
            dish("s1", MealSlot.SNACK, 180.0),
            dish("s2", MealSlot.SNACK, 250.0)
        )
        val plan = MealPlanner.planWeek(dates, 2000, pool, seed = 5L)

        val leftovers = plan.filter { it.leftover }
        assertTrue("должны быть остатки", leftovers.isNotEmpty())
        assertTrue("остатки только от супа", leftovers.all { it.dish.id == "soup" })

        val firstSoup = plan.first { it.dish.id == "soup" && !it.leftover }
        assertTrue("кастрюля минимум на 2 порции", firstSoup.batchServings >= 2.0)

        plan.groupBy { it.dateKey }.forEach { (date, meals) ->
            val kcal = meals.sumOf { it.kcal }
            assertTrue("день $date: $kcal ккал", abs(kcal - 2000) <= 250)
        }
    }
}
