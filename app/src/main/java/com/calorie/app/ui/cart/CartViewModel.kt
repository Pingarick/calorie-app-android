package com.calorie.app.ui.cart

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.domain.cart.CartCalculator
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

data class CartUiState(
    val sections: List<CartSection> = emptyList(),
    val total: Double = 0.0,
    val budget: Int = 0,
    val hasPlan: Boolean = false
)

class CartViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val repo = DiaryRepository(db)

    private val dishes = MutableStateFlow<Map<String, Dish>>(emptyMap())
    private val ingredients = MutableStateFlow<Map<String, List<DishIngredient>>>(emptyMap())

    /** Корзина считается по тому же диапазону, на который собирается рацион. */
    private val planFlow = db.planDao().observeRange(DateUtils.keyFor(0), DateUtils.keyFor(6))

    val state: StateFlow<CartUiState> = combine(
        planFlow,
        db.productDao().observeAll(),
        dishes,
        ingredients,
        repo.observeProfile()
    ) { plan, products, byId, recipe, profile ->
        val cart = CartCalculator.build(plan, byId, recipe, products.associateBy { it.id })
        CartUiState(
            sections = cart.sections,
            total = cart.total,
            budget = profile?.weeklyBudget ?: 0,
            hasPlan = plan.isNotEmpty()
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
}
