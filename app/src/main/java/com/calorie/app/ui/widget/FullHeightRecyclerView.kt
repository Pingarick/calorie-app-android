package com.calorie.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView для вложения внутрь ScrollView с layout_height="wrap_content".
 *
 * Проблема: ScrollView меряет ребёнка со спеком AT_MOST(остаток вьюпорта), а
 * LinearLayoutManager разрешает «бесконечную» укладку всех детей только когда
 * режим спека UNSPECIFIED И текущая высота RecyclerView == 0. Иначе он набивает
 * строки лишь до высоты, которую уже имеет, и хвост списка обрезается по сгибу.
 *
 * Поэтому меряем ребёнка всегда с UNSPECIFIED: список укладывается целиком,
 * а ScrollView потом скроллит его как обычный контент.
 */
class FullHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    }
}
