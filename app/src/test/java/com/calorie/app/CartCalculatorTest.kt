package com.calorie.app

import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.data.local.model.Product
import com.calorie.app.domain.cart.CartCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartCalculatorTest {

    private fun product(id: String, price: Double, category: String = "прочее") = Product(
        id = id,
        name = "Продукт $id",
        kcal100 = 100.0,
        protein100 = 10.0,
        fat100 = 5.0,
        carbs100 = 10.0,
        category = category,
        pricePer100 = price
    )

    private fun dish(id: String) = Dish(
        id = id,
        title = "Блюдо $id",
        slots = "LUNCH",
        technique = "плита",
        timeMin = 20,
        gramsPerServing = 300.0,
        kcalPerServing = 500.0,
        proteinPerServing = 30.0,
        fatPerServing = 20.0,
        carbsPerServing = 50.0
    )

    private fun item(dishId: String, servings: Double) = PlanItem(
        dateKey = "2026-10-22",
        slot = "LUNCH",
        dishId = dishId,
        servings = servings,
        grams = 0.0,
        kcal = 0.0,
        protein = 0.0,
        fat = 0.0,
        carbs = 0.0
    )

    @Test
    fun sumsIngredientsAcrossDishes() {
        val cart = CartCalculator.build(
            plan = listOf(item("a", 2.0), item("b", 1.0)),
            dishes = mapOf("a" to dish("a"), "b" to dish("b")),
            ingredients = mapOf(
                "a" to listOf(DishIngredient("a", "p", 100.0)),
                "b" to listOf(DishIngredient("b", "p", 50.0))
            ),
            products = mapOf("p" to product("p", 40.0))
        )
        assertEquals(1, cart.lines)
        assertEquals(250.0, cart.sections.first().lines.first().grams, 1e-9)
    }

    @Test
    fun priceIsCountedPer100Grams() {
        val cart = CartCalculator.build(
            plan = listOf(item("a", 2.0)),
            dishes = mapOf("a" to dish("a")),
            ingredients = mapOf("a" to listOf(DishIngredient("a", "p", 100.0))),
            products = mapOf("p" to product("p", 40.0))
        )
        // 2 порции по 100 г = 200 г, при 40 ₽ за 100 г это 80 ₽
        assertEquals(80.0, cart.total, 1e-9)
    }

    @Test
    fun groupsByCategoryAndCountsTotal() {
        val cart = CartCalculator.build(
            plan = listOf(item("a", 1.0), item("b", 1.0)),
            dishes = mapOf("a" to dish("a"), "b" to dish("b")),
            ingredients = mapOf(
                "a" to listOf(DishIngredient("a", "veg", 100.0)),
                "b" to listOf(DishIngredient("b", "meat", 100.0))
            ),
            products = mapOf(
                "veg" to product("veg", 10.0, "овощи"),
                "meat" to product("meat", 50.0, "мясо")
            )
        )
        assertEquals(2, cart.sections.size)
        assertEquals(60.0, cart.total, 1e-9)
        assertEquals("овощи", cart.sections.first().category)
    }

    @Test
    fun emptyPlanGivesEmptyCart() {
        val cart = CartCalculator.build(emptyList(), emptyMap(), emptyMap(), emptyMap())
        assertTrue(cart.isEmpty)
        assertEquals(0.0, cart.total, 1e-9)
    }

    @Test
    fun unknownDishIsSkipped() {
        val cart = CartCalculator.build(
            plan = listOf(item("missing", 1.0)),
            dishes = emptyMap(),
            ingredients = emptyMap(),
            products = mapOf("p" to product("p", 40.0))
        )
        assertTrue(cart.isEmpty)
    }

    @Test
    fun pantrySubtractsFromRequiredAmount() {
        val cart = CartCalculator.build(
            plan = listOf(item("a", 2.0)),
            dishes = mapOf("a" to dish("a")),
            ingredients = mapOf("a" to listOf(DishIngredient("a", "p", 100.0))),
            products = mapOf("p" to product("p", 40.0)),
            pantry = mapOf("p" to 100.0)
        )
        // Нужно 200 г, дома 100 г — покупаем 100 г за 40 ₽.
        assertEquals(100.0, cart.sections.first().lines.first().grams, 1e-9)
        assertEquals(40.0, cart.total, 1e-9)
    }

    @Test
    fun pantryCoversProductCompletely() {
        val cart = CartCalculator.build(
            plan = listOf(item("a", 1.0)),
            dishes = mapOf("a" to dish("a")),
            ingredients = mapOf("a" to listOf(DishIngredient("a", "p", 100.0))),
            products = mapOf("p" to product("p", 40.0)),
            pantry = mapOf("p" to 500.0)
        )
        assertTrue(cart.isEmpty)
        assertEquals(0.0, cart.total, 1e-9)
    }
}
