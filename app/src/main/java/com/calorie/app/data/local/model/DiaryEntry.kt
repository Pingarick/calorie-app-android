package com.calorie.app.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Одна запись дневника: приём пищи / блюдо в определённый день. */
@Entity(
    tableName = "diary_entry",
    indices = [Index(value = ["dateKey"])]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Дата как строка yyyy-MM-dd в локальном TZ. */
    val dateKey: String,
    /** Завтрак / Обед / Ужин / Перекус. */
    val meal: String,
    val mealTimeMs: Long = System.currentTimeMillis(),
    /** Название блюда. */
    val name: String,
    @ColumnInfo(name = "grams") val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    /** Урл/путь к фото блюда (если добавлено по фото), иначе пусто. */
    val photoPath: String = ""
)
