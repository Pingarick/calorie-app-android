package com.calorie.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.DayAggRow
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.util.DateUtils
import com.calorie.app.util.DaySummary
import com.calorie.app.util.buildDaySummary
import com.calorie.app.util.dailyTargetKcal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Дневник: счётчик дней от сегодня (0), лента записей выбранного дня
 * и сводка «съедено vs норма». Данные живут в Room и обновляются сами.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DiaryRepository((app as CalorieApp).database)

    /** 0 = сегодня, -1 = вчера, и т.д. Не даём заглядывать в будущее. */
    private val offset = MutableStateFlow(0)
    val canGoNext: StateFlow<Boolean> = offset.map { it < 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Ключ выбранного дня (yyyy-MM-dd). */
    val dateKey: StateFlow<String> = offset
        .map { DateUtils.keyFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateUtils.todayKey())

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entries: StateFlow<List<DiaryEntry>> = dateKey.flatMapLatest { key ->
        repo.observeDay(key)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Текущий день — единственный, к которому можно добавлять фото/вручную. */
    val isToday: StateFlow<Boolean> = dateKey.map { it == DateUtils.todayKey() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Сгруппированная по приёмам пищи лента: заголовок слота + записи.
     *
     * Зависит и от [isToday], потому что кнопка «+» есть только у сегодняшнего дня,
     * а ещё потому что `entries` — StateFlow и гасит одинаковые значения: без второго
     * потока переход между двумя пустыми днями не дал бы ни одной эмиссии.
     */
    val grouped: StateFlow<List<DiaryRow>> = combine(entries, isToday) { list, today ->
        groupByMeal(list, canAdd = today)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, groupByMeal(emptyList(), canAdd = true))

    val summary: StateFlow<DaySummary> = combine(dateKey, profile) { key, prof ->
        key to prof
    }.flatMapLatest { (key, prof) ->
        repo.observeDayKbju(key).map { agg ->
            buildDaySummary(dailyTargetKcal(prof), if (isEmptyAgg(agg)) null else agg)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DaySummary())

    val dayTitle: StateFlow<String> = dateKey.map { DateUtils.display(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateUtils.todayKey())

    // ---------- Операции ----------

    fun goToPrev() { offset.value -= 1 }

    fun goToNext() {
        if (offset.value < 0) offset.value += 1
    }

    /** Возврат к сегодняшнему дню одним тапом. */
    fun goToToday() { offset.value = 0 }

    /** Добавить запись к текущему дню. */
    fun add(name: String, grams: Double, kcal: Double,
            protein: Double, fat: Double, carbs: Double,
            photoPath: String = "", meal: String? = null) {
        viewModelScope.launch {
            repo.addEntry(
                DiaryEntry(
                    dateKey = dateKey.value,
                    meal = meal ?: defaultMealSlot(),
                    name = name,
                    grams = grams,
                    kcal = kcal,
                    protein = protein,
                    fat = fat,
                    carbs = carbs,
                    photoPath = photoPath
                )
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteEntry(id) }
    }

    /**
     * Возврат удалённой записи по кнопке «Отменить»: вставляем копию с нулевым id,
     * чтобы комната выдала новый первичный ключ и запись встала на своё место в ленте.
     */
    fun restore(entry: DiaryEntry) {
        viewModelScope.launch { repo.addEntry(entry.copy(id = 0)) }
    }

    private fun isEmptyAgg(a: DayAggRow) =
        a.kcal <= 0.0 && a.protein <= 0.0 && a.fat <= 0.0 && a.carbs <= 0.0

    /** Слот приёма пищи по текущему времени суток. */
    fun defaultMealSlot(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (h) {
            in 4..10 -> MEAL_SLOTS[0]   // Завтрак
            in 11..15 -> MEAL_SLOTS[1]  // Обед
            in 16..22 -> MEAL_SLOTS[2]  // Ужин
            else -> MEAL_SLOTS[3]       // Перекус
        }
    }

    /**
     * Раскладывает записи дня по слотам в фиксированном порядке. Пустой слот остаётся
     * в таблице (заголовок + подпись «ничего не добавлено») — иначе по дню с одной
     * записью нельзя было понять, что обед пропущен.
     */
    private fun groupByMeal(list: List<DiaryEntry>, canAdd: Boolean): List<DiaryRow> {
        val byMeal = list.groupBy { it.meal }
        return MEAL_SLOTS.flatMap { meal ->
            val items = byMeal[meal].orEmpty()
            val header = DiaryRow.MealHeader(meal, items.sumOf { it.kcal }, canAdd)
            if (items.isEmpty()) listOf(header, DiaryRow.EmptySlot(meal))
            else listOf(header) + items.map { DiaryRow.EntryRow(it) }
        }
    }

    companion object {
        /** Порядок приёмов пищи на экране дня. */
        val MEAL_SLOTS = listOf("Завтрак", "Обед", "Ужин", "Перекус")
    }
}
