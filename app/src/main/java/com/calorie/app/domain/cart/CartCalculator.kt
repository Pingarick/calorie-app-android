package com.calorie.app.domain.cart

import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.data.local.model.Product
import kotlin.math.roundToInt

data class CartLine(
    val productId: String,
    val name: String,
    val category: String,
    val grams: Double,
    val price: Double
)

data class CartSection(
    val category: String,
    val lines: List<CartLine>,
    val total: Double
)

data class CartTotal(
    val sections: List<CartSection>,
    val total: Double,
    val lines: Int
) {
    val isEmpty: Boolean get() = lines == 0
}

/**
 * Сворачивает рацион в список покупок: суммирует ингредиенты по продуктам,
 * считает граммы и цену. Чистая функция — проверяется без базы.
 */
object CartCalculator {

    /** Порядок отделов в магазине, а не алфавит. Неизвестные категории уходят в конец. */
    private val CATEGORY_ORDER = listOf(
        "овощи", "фрукты", "мясо", "рыба", "яйца", "молочное",
        "крупы", "бобовые", "хлеб", "жиры", "орехи", "сладкое", "прочее"
    )

    fun build(
        plan: List<PlanItem>,
        dishes: Map<String, Dish>,
        ingredients: Map<String, List<DishIngredient>>,
        products: Map<String, Product>,
        pantry: Map<String, Double> = emptyMap()
    ): CartTotal {
        val grams = HashMap<String, Double>()
        for (item in plan) {
            val dish = dishes[item.dishId] ?: continue
            val recipe = ingredients[dish.id] ?: continue
            for (ingredient in recipe) {
                grams[ingredient.productId] =
                    (grams[ingredient.productId] ?: 0.0) + ingredient.gramsPerServing * item.servings
            }
        }

        val lines = grams.mapNotNull { (productId, required) ->
            val product = products[productId] ?: return@mapNotNull null
            // То, что уже есть дома, не покупаем: сумма и граммы уменьшаются.
            val amount = (required - (pantry[productId] ?: 0.0)).coerceAtLeast(0.0)
            if (amount <= 0.0) return@mapNotNull null
            CartLine(
                productId = productId,
                name = product.name,
                category = product.category,
                grams = amount,
                price = amount / 100.0 * product.pricePer100
            )
        }

        val sections = lines
            .groupBy { it.category }
            .map { (category, items) ->
                CartSection(
                    category = category,
                    lines = items.sortedByDescending { it.price },
                    total = items.sumOf { it.price }
                )
            }
            .sortedBy { section ->
                val index = CATEGORY_ORDER.indexOf(section.category)
                if (index >= 0) index else CATEGORY_ORDER.size
            }

        return CartTotal(
            sections = sections,
            total = (lines.sumOf { it.price } * 100).roundToInt() / 100.0,
            lines = lines.size
        )
    }
}
