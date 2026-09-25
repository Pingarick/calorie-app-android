package com.calorie.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.calorie.app.R

/**
 * Кольцо прогресса калорий. Рисуется вручную, а не через CircularProgressIndicator:
 * нужна толстая дорожка с закруглёнными концами и текст внутри, а не индикатор загрузки.
 *
 * [setProgress] принимает долю 0..1 (значения больше 1 подрезаются), всё, что не
 * конечное число, трактуется как 0 — деления на нулевую норму в вёрстке быть не должно.
 */
class CalorieRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ring_track)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.calorie_primary)
    }

    private val oval = RectF()
    private var fraction = 0f

    private val stroke = resources.getDimension(R.dimen.ring_stroke)

    init {
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
    }

    fun setProgress(value: Float) {
        val v = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        if (v == fraction) return
        fraction = v
        invalidate()
    }

    fun setProgressColor(color: Int) {
        if (progressPaint.color == color) return
        progressPaint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resources.getDimensionPixelSize(R.dimen.ring_size)
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val d = minOf(w, h).toFloat() - stroke
        val left = (w - d) / 2f
        val top = (h - d) / 2f
        oval.set(left, top, left + d, top + d)
    }

    override fun onDraw(canvas: Canvas) {
        if (oval.isEmpty) return
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        if (fraction > 0f) {
            canvas.drawArc(oval, -90f, 360f * fraction, false, progressPaint)
        }
    }
}
