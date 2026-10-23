package com.calorie.app.ui.prefs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.repository.PreferenceRepository
import com.calorie.app.domain.preferences.PreferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Одна строка экрана «что я знаю»: блюдо, вес и когда его готовили. */
data class KnownPrefRow(
    val dish: Dish,
    val weight: Double,
    val lastCookedAt: Long?
)

class KnownPrefsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val repo = PreferenceRepository(db)
    private val dishes = MutableStateFlow<Map<String, Dish>>(emptyMap())

    val rows: StateFlow<List<KnownPrefRow>> = combine(
        db.preferenceDao().observeAll(),
        db.preferenceDao().observeLastCooked(),
        dishes
    ) { weights, cooked, byId ->
        val weightById = weights.associate { it.entityId to it.weight }
        val lastById = cooked.associate { it.entityId to it.lastAt }
        (weightById.keys + lastById.keys)
            .mapNotNull { id ->
                byId[id]?.let { dish ->
                    KnownPrefRow(dish, weightById[id] ?: 0.0, lastById[id])
                }
            }
            .sortedWith(compareByDescending<KnownPrefRow> { it.weight }.thenBy { it.dish.title })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { db.dishDao().all() }
            dishes.value = all.associateBy { it.id }
        }
    }

    fun like(row: KnownPrefRow) = rate(row, PreferenceEngine.TYPE_RATED_POS)

    fun dislike(row: KnownPrefRow) = rate(row, PreferenceEngine.TYPE_RATED_NEG)

    fun forget(row: KnownPrefRow) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.forget(row.dish.id) }
        }
    }

    private fun rate(row: KnownPrefRow, type: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.record(type, row.dish.id) }
        }
    }
}
