package com.calorie.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ScrollView
import kotlin.math.abs

/**
 * ScrollView, который дополнительно распознаёт горизонтальные свайпы.
 *
 * Вертикальный скролл остаётся целиком за [ScrollView]. Свайп считаем сами, а не
 * через `OnTouchListener`: список приёмов пищи — это RecyclerView, который съедает
 * DOWN/MOVE даже когда сам не скроллится, поэтому до слушателя родителя события
 * не доходят. `dispatchTouchEvent` вызывается для всей ветки поддерева всегда,
 * независимо от того, кто из детей поглотил жест, — поэтому считаем здесь.
 *
 * Жест засчитывается только если он горизонтальный и прошёл [minDistance];
 * иначе это обычный вертикальный скролл/тап и мы ничего не делаем.
 */
class DaySwipeScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {

    /**
     * Свайп влево — следующий день. Вернуть `true`, если день действительно сменился:
     * иначе (мы уже на сегодняшнем) гаптика не сработает.
     */
    var onSwipeLeft: (() -> Boolean)? = null

    /** Свайп вправо — предыдущий день. `true`, если день сменился. */
    var onSwipeRight: (() -> Boolean)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minDistance = 64 * resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f

    /** Направление жеста решается один раз на первом движении за слопом. */
    private var horizontal = false
    private var decided = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                horizontal = false
                decided = false
            }

            MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    decided = true
                    horizontal = abs(dx) > abs(dy)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (horizontal && decided) {
                    val dx = ev.x - downX
                    when {
                        dx <= -minDistance -> fire(onSwipeLeft)
                        dx >= minDistance -> fire(onSwipeRight)
                    }
                }
                horizontal = false
                decided = false
            }

            MotionEvent.ACTION_CANCEL -> {
                horizontal = false
                decided = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun fire(action: (() -> Boolean)?) {
        if (action?.invoke() == true) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
