package com.calorie.app.ui.main

import com.calorie.app.data.local.model.DiaryEntry

/**
 * Строка «таблицы» дневника: заголовок приёма пищи (Завтрак/Обед/Ужин/Перекус
 * с подытогом), подпись пустого слота или запись.
 *
 * Пустой слот тоже занимает строку — иначе таблица «схлопывалась» и вместо
 * Завтрака/Обеда/Ужина был просто список того, что успели съесть.
 */
sealed interface DiaryRow {
    interface HasId { val id: String }

    /** [canAdd] — сегодняшний день, у заголовка есть кнопка добавления в этот слот. */
    data class MealHeader(val meal: String, val kcal: Double, val canAdd: Boolean) :
        DiaryRow, HasId {
        override val id get() = "meal_$meal"
    }

    data class EmptySlot(val meal: String) : DiaryRow, HasId {
        override val id get() = "empty_$meal"
    }

    data class EntryRow(val entry: DiaryEntry) : DiaryRow, HasId {
        override val id get() = "entry_${entry.id}"
    }
}
