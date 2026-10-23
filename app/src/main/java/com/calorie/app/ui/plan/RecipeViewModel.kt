package com.calorie.app.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.repository.RecipeRepository
import com.calorie.app.domain.recipe.RecipeStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class RecipeLine(val name: String, val grams: Double)

data class RecipeUiState(
    val dishId: String? = null,
    val title: String = "",
    val portion: String = "",
    val ingredients: List<RecipeLine> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasRecipe: Boolean = false
)

/** Состояние диалога рецепта: состав под выбранную порцию и шаги из кэша или модели. */
class RecipeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val repo = RecipeRepository(db)

    private val _state = MutableStateFlow(RecipeUiState())
    val state: StateFlow<RecipeUiState> = _state

    private var dishId: String? = null
    private var servings: Double = 1.0

    fun open(id: String, amount: Double, batchServings: Double = 1.0) {
        dishId = id
        servings = amount
        _state.value = RecipeUiState(dishId = id, loading = true)

        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                val dish = db.dishDao().byId(id)
                val products = db.productDao().all().associateBy { it.id }
                // Для кастрюли показываем продукты на все порции, а не на одну тарелку.
                val batchFactor = if (batchServings > 0) batchServings else 1.0
                val lines = db.dishDao().ingredientsFor(id).mapNotNull { ingredient ->
                    products[ingredient.productId]?.let { product ->
                        RecipeLine(product.name, ingredient.gramsPerServing * amount * batchFactor)
                    }
                }
                Triple(dish, lines, repo.cached(id))
            }
            val dish = data.first
            if (dish == null) {
                _state.value = RecipeUiState(error = "Блюдо не найдено")
                return@launch
            }
            val steps = data.third.orEmpty()
            val portion = if (batchServings > 1) {
                String.format(
                    Locale.US,
                    "%.1f порц. · %d г · готовим %d порции",
                    amount,
                    (dish.gramsPerServing * amount).toInt(),
                    batchServings.toInt()
                )
            } else {
                String.format(
                    Locale.US,
                    "%.1f порц. · %d г",
                    amount,
                    (dish.gramsPerServing * amount).toInt()
                )
            }
            _state.value = RecipeUiState(
                dishId = id,
                title = dish.title,
                portion = portion,
                ingredients = data.second,
                steps = steps,
                hasRecipe = steps.isNotEmpty()
            )
        }
    }

    fun generate() {
        val id = dishId ?: return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repo.generate(id, servings) }
            result.onSuccess { steps ->
                _state.value = _state.value.copy(
                    loading = false,
                    steps = steps,
                    hasRecipe = true
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Не удалось получить рецепт"
                )
            }
        }
    }
}
