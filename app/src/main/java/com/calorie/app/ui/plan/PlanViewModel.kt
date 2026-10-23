package com.calorie.app.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.data.repository.PreferenceRepository
import com.calorie.app.data.seed.SeedData
import com.calorie.app.domain.plan.PlanEditor
import com.calorie.app.domain.planner.MealSlot
import com.calorie.app.domain.preferences.PreferenceEngine
import com.calorie.app.util.DateUtils
import com.calorie.app.util.dailyTargetKcal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Сводка дня рациона: сколько запланировано против нормы. */
data class PlanDaySummary(
    val plannedKcal: Double = 0.0,
    val targetKcal: Int = 0,
    val plannedProtein: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val repo = DiaryRepository(db)
    private val preferences = PreferenceRepository(db)
    private val editor = PlanEditor(db)

    /** 0 = сегодня, дальше вперёд до +6: план смотрим вперёд, а не назад. */
    private val offset = MutableStateFlow(0)
    private val dishes = MutableStateFlow<Map<String, Dish>>(emptyMap())
    private val _busy = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val busy: StateFlow<Boolean> = _busy
    val message: StateFlow<String?> = _message

    val canGoPrev: StateFlow<Boolean> = offset.map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canGoNext: StateFlow<Boolean> = offset.map { it < 6 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val dateKey: StateFlow<String> = offset.map { DateUtils.keyFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateUtils.todayKey())

    val dayTitle: StateFlow<String> = dateKey.map { DateUtils.display(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateUtils.todayKey())

    val profile = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val items: StateFlow<List<PlanItem>> = dateKey.flatMapLatest { key ->
        db.planDao().observeForDate(key)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<PlanDaySummary> = combine(items, profile) { list, p ->
        PlanDaySummary(
            plannedKcal = list.sumOf { it.kcal },
            targetKcal = dailyTargetKcal(p),
            plannedProtein = list.sumOf { it.protein }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlanDaySummary())

    val rows: StateFlow<List<PlanRow>> = combine(items, dishes) { list, map ->
        buildRows(list, map)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, buildRows(emptyList(), emptyMap()))

    init {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) {
                SeedData.ensure(db)
                db.dishDao().all()
            }
            dishes.value = all.associateBy { it.id }
        }
    }

    fun goToPrev() {
        if (offset.value > 0) offset.value -= 1
    }

    fun goToNext() {
        if (offset.value < 6) offset.value += 1
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Собирает рацион на 7 дней вперёд от сегодня. */
    fun generateWeek() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val all = withContext(Dispatchers.IO) {
                    SeedData.ensure(db)
                    db.dishDao().all()
                }
                if (all.isEmpty()) {
                    _message.value = "Каталог блюд пуст"
                    return@launch
                }
                val target = dailyTargetKcal(profile.value)
                val dates = (0..6).map { DateUtils.keyFor(it) }
                val snapshot = withContext(Dispatchers.IO) { preferences.snapshot() }
                val ok = withContext(Dispatchers.IO) {
                    editor.regenerate(dates, target, snapshot)
                }
                if (!ok) {
                    _message.value = "Не удалось собрать рацион"
                    return@launch
                }
                dishes.value = all.associateBy { it.id }
                _message.value = "Рацион собран на неделю"
            } finally {
                _busy.value = false
            }
        }
    }

    fun remove(item: PlanItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                preferences.record(PreferenceEngine.TYPE_REMOVED, item.dishId)
                editor.remove(item)
            }
        }
    }

    /** «Не сегодня»: убирает блюдо из плана и запоминает, что его отложили. */
    fun postpone(item: PlanItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                preferences.record(PreferenceEngine.TYPE_POSTPONED, item.dishId)
                editor.remove(item)
            }
            _message.value = "Отложено на другой раз"
        }
    }

    /** Меняет блюдо на ближайшее по калорийности из того же слота. */
    fun replace(item: PlanItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                preferences.record(PreferenceEngine.TYPE_REPLACED, item.dishId)
                val next = editor.replace(item)
                if (next == null) _message.value = "Больше нечего предложить"
            }
        }
    }

    /** Переносит блюдо в дневник и помечает слот приготовленным. */
    fun cook(item: PlanItem) {
        viewModelScope.launch {
            val dish = dishes.value[item.dishId]
                ?: withContext(Dispatchers.IO) { db.dishDao().byId(item.dishId) }
            if (dish == null) {
                _message.value = "Блюдо не найдено"
                return@launch
            }
            withContext(Dispatchers.IO) {
                repo.addEntry(
                    DiaryEntry(
                        // Пишем на сегодня: готовка может быть заранее, а приём пищи — сейчас.
                        dateKey = DateUtils.todayKey(),
                        meal = MealSlot.byName(item.slot).label,
                        name = dish.title,
                        grams = item.grams,
                        kcal = item.kcal,
                        protein = item.protein,
                        fat = item.fat,
                        carbs = item.carbs
                    )
                )
                db.planDao().markCooked(item.id)
                preferences.record(PreferenceEngine.TYPE_COOKED, item.dishId)
            }
            _message.value = "Добавлено в дневник"
        }
    }

    private fun buildRows(list: List<PlanItem>, byId: Map<String, Dish>): List<PlanRow> {
        val bySlot = list.groupBy { it.slot }
        return MealSlot.entries.flatMap { slot ->
            val slots = bySlot[slot.name].orEmpty()
            val header = PlanRow.MealHeader(slot, slots.sumOf { it.kcal })
            if (slots.isEmpty()) {
                listOf(header, PlanRow.EmptySlot(slot))
            } else {
                listOf(header) + slots.mapNotNull { item ->
                    byId[item.dishId]?.let { PlanRow.EntryRow(item, it) }
                }
            }
        }
    }
}
