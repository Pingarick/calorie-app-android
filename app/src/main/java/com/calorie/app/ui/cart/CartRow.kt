package com.calorie.app.ui.cart

import com.calorie.app.domain.cart.CartLine

/** Строка корзины: заголовок отдела или продукт. */
sealed class CartRow {
    data class Section(val category: String, val total: Double) : CartRow()
    data class Line(val line: CartLine) : CartRow()
}
