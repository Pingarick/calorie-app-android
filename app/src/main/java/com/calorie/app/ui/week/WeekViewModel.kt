package com.calorie.app.ui.week

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.Dish
import com.calorie.app.domain.planner.MealSlot
import com.calorie.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Вся неделя одним списком: 7 дней, блюда по слотам и калории. */
class WeekViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val dishes = MutableStateFlow<Map<String, Dish>>(emptyMap())

    private val dates: List<String> = (0..6).map { DateUtils.keyFor(it) }

    val rows: StateFlow<List<WeekRow>> = combine(
        db.planDao().observeRange(dates.first(), dates.last()),
        dishes
    ) { plan, byId ->
        val byDate = plan.groupBy { it.dateKey }
        dates.flatMapIndexed { index, date ->
            val items = byDate[date].orEmpty().sortedBy { it.orderIndex }
            val day = WeekRow.Day(
                offset = index,
                dateKey = date,
                title = DateUtils.display(date),
                kcal = items.sumOf { it.kcal }
            )
            val meals = items.mapNotNull { item ->
                byId[item.dishId]?.let { dish ->
                    WeekRow.Dish(
                        slot = MealSlot.byName(item.slot).label,
                        title = dish.title,
                        leftover = item.leftover,
                        kcal = item.kcal
                    )
                }
            }
            listOf(day) + meals
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { db.dishDao().all() }
            dishes.value = all.associateBy { it.id }
        }
    }
}
