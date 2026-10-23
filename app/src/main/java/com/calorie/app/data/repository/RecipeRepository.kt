package com.calorie.app.data.repository

import com.calorie.app.data.local.AppDatabase
import com.calorie.app.data.local.model.Recipe
import com.calorie.app.data.remote.RecipeClient
import com.calorie.app.domain.recipe.RecipeParser
import com.calorie.app.domain.recipe.RecipeStep

/** Достаёт рецепт из кэша или просит модель написать шаги и сохраняет их. */
class RecipeRepository(
    private val db: AppDatabase,
    private val client: RecipeClient = RecipeClient()
) {

    suspend fun cached(dishId: String): List<RecipeStep>? =
        db.recipeDao().byDish(dishId)?.let { RecipeParser.parse(it.stepsJson) }

    suspend fun generate(dishId: String, servings: Double): Result<List<RecipeStep>> = runCatching {
        val dish = db.dishDao().byId(dishId) ?: error("Блюдо не найдено")
        val ingredients = db.dishDao().ingredientsFor(dishId)
        val products = db.productDao().all().associateBy { it.id }
        val lines = ingredients.mapNotNull { ingredient ->
            products[ingredient.productId]?.let { product ->
                product.name to ingredient.gramsPerServing * servings
            }
        }
        require(lines.isNotEmpty()) { "У блюда нет ингредиентов" }

        val steps = client.steps(dish.title, lines, servings)
        db.recipeDao().upsert(Recipe(dishId = dishId, stepsJson = RecipeParser.toJson(steps)))
        steps
    }
}
