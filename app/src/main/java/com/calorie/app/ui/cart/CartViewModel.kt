package com.calorie.app.ui.cart

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.PantryItem
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.domain.cart.CartCalculator
import com.calorie.app.domain.cart.CartLine
import com.calorie.app.domain.cart.CartSection
import com.calorie.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PantryLine(val productId: String, val name: String, val grams: Double)

data class CartUiState(
    val sections: List<CartSection> = emptyList(),
    val total: Double = 0.0,
    val budget: Int = 0,
    val hasPlan: Boolean = false,
    val pantry: List<PantryLine> = emptyList()
)

class CartViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val repo = DiaryRepository(db)

    private val dishes = MutableStateFlow<Map<String, Dish>>(emptyMap())
    private val ingredients = MutableStateFlow<Map<String, List<DishIngredient>>>(emptyMap())

    /** Корзина считается по тому же диапазону, на который собирается рацион. */
    private val planFlow = db.planDao().observeRange(DateUtils.keyFor(0), DateUtils.keyFor(6))

    private val base = combine(
        planFlow,
        db.productDao().observeAll(),
        db.pantryDao().observeAll()
    ) { plan, products, pantry -> Triple(plan, products, pantry) }

    private val recipes = combine(dishes, ingredients) { byId, recipe -> byId to recipe }

    val state: StateFlow<CartUiState> = combine(
        base,
        recipes,
        repo.observeProfile()
    ) { baseData, recipeData, profile ->
        val (plan, products, pantry) = baseData
        val (byId, recipe) = recipeData
        val productById = products.associateBy { it.id }
        val cart = CartCalculator.build(
            plan = plan,
            dishes = byId,
            ingredients = recipe,
            products = productById,
            pantry = pantry.associate { it.productId to it.grams }
        )
        CartUiState(
            sections = cart.sections,
            total = cart.total,
            budget = profile?.weeklyBudget ?: 0,
            hasPlan = plan.isNotEmpty(),
            pantry = pantry.mapNotNull { item ->
                productById[item.productId]?.let { PantryLine(item.productId, it.name, item.grams) }
            }.sortedBy { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartUiState())

    val rows: StateFlow<List<CartRow>> = state.map { st ->
        st.sections.flatMap { section ->
            listOf<CartRow>(CartRow.Section(section.category, section.total)) +
                section.lines.map { CartRow.Line(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val allDishes = withContext(Dispatchers.IO) { db.dishDao().all() }
            val allIngredients = withContext(Dispatchers.IO) { db.dishDao().allIngredients() }
            dishes.value = allDishes.associateBy { it.id }
            ingredients.value = allIngredients.groupBy { it.dishId }
        }
    }

    fun setBudget(value: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val profile = repo.getProfile() ?: UserProfile()
                repo.saveProfile(profile.copy(weeklyBudget = value.coerceAtLeast(0)))
            }
        }
    }

    /** Отмечает, что продукт уже есть дома: из корзины уходит вся требуемая порция. */
    fun markAtHome(line: CartLine) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.pantryDao().upsert(PantryItem(productId = line.productId, grams = line.grams))
            }
        }
    }

    fun removeFromPantry(productId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.pantryDao().delete(productId) }
        }
    }

    fun clearPantry() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.pantryDao().clear() }
        }
    }
}
