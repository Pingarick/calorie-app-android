package com.calorie.app.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сырое событие взаимодействия с блюдом. Из них считается вес предпочтения.
 * Хранить события, а не только итоговый вес, нужно, чтобы историю можно было
 * пересчитать, объяснить и удалить.
 */
@Entity(tableName = "preference_event")
data class PreferenceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** COOKED, REMOVED, REPLACED, POSTPONED, RATED_POS, RATED_NEG. */
    val type: String,
    /** Пока только DISH; продукты и кухни добавим, когда появятся. */
    val entityType: String,
    val entityId: String,
    /** Сила сигнала на момент события. */
    val weight: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/** Выученный вес сущности: от -1 (не любит) до +1 (любит). */
@Entity(tableName = "preference")
data class Preference(
    /** "DISH:chicken_buck". */
    @PrimaryKey val key: String,
    val entityType: String,
    val entityId: String,
    val weight: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
