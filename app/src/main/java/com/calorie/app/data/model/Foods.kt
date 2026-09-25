package com.calorie.app.data.model

/**
 * Встроенная база типовых продуктов — состав на 100 грамм.
 * Упрощённый локальный справочник без внешнего API, чтобы дневник работал офлайн.
 */
data class FoodItem(
    val id: String,
    val name: String,
    val kcalPer100: Int,
    val proteinPer100: Double,
    val fatPer100: Double,
    val carbsPer100: Double
)

object Foods {
    val common = listOf(
        FoodItem("oat", "Овсянка (готовая)", 68, 2.5, 1.4, 12.0),
        FoodItem("egg", "Яйцо варёное (1 шт ~50г)", 143, 12.6, 9.5, 0.6),
        FoodItem("curd", "Творог 5%", 121, 17.2, 5.0, 1.8),
        FoodItem("buckwheat", "Гречка отварная", 110, 4.2, 1.1, 21.3),
        FoodItem("rice", "Рис отварной", 116, 2.6, 0.3, 24.9),
        FoodItem("chicken", "Куриная грудка (варёная)", 165, 29.0, 3.6, 0.0),
        FoodItem("beef", "Говядина отварная", 175, 25.0, 8.0, 0.0),
        FoodItem("pork", "Свинина постная", 242, 19.0, 18.0, 0.0),
        FoodItem("fish", "Треска (варёная)", 78, 17.0, 0.6, 0.0),
        FoodItem("salmon", "Лосось запечённый", 197, 20.0, 12.0, 0.0),
        FoodItem("potato", "Картофель отварной", 82, 1.9, 0.1, 16.6),
        FoodItem("bread", "Хлеб белый", 265, 8.9, 3.3, 49.0),
        FoodItem("apple", "Яблоко", 47, 0.4, 0.4, 9.8),
        FoodItem("banana", "Банан", 89, 1.1, 0.3, 22.8),
        FoodItem("orange", "Апельсин", 43, 0.9, 0.2, 9.0),
        FoodItem("avocado", "Авокадо", 160, 2.0, 14.7, 8.5),
        FoodItem("nuts", "Орехи (миндаль)", 579, 21.0, 50.0, 22.0),
        FoodItem("yogurt", "Йогурт питьевой 2.5%", 55, 3.0, 2.5, 4.1),
        FoodItem("cheese", "Сыр твёрдый (гауда)", 356, 25.0, 27.0, 2.2),
        FoodItem("beef_soup", "Куриный суп", 36, 3.0, 1.0, 4.0),
        FoodItem("borscht", "Борщ", 49, 3.0, 2.0, 5.0),
        FoodItem("salad", "Овощной салат (без масла)", 30, 1.2, 0.3, 6.0),
        FoodItem("chocolate", "Шоколад тёмный 70%", 598, 7.8, 42.0, 45.0),
        FoodItem("pasta", "Паста отварная", 131, 5.0, 1.1, 24.9),
        FoodItem("pancake", "Блины", 227, 6.1, 6.3, 35.0),
        FoodItem("pizza", "Пицца", 266, 11.0, 9.8, 33.3)
    )

    fun byId(id: String): FoodItem? = common.firstOrNull { it.id == id }
}
