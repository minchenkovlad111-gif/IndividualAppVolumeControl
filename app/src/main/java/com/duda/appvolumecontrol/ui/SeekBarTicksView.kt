package com.duda.appvolumecontrol.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class SeekBarTicksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val minorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0B0B0.toInt()
        strokeWidth = dpToPx(1f)
    }

    private val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6D6D6D.toInt()
        strokeWidth = dpToPx(2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val minorTop = height * 0.55f
        val majorTop = height * 0.25f
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val widthSpan = right - left

        for (percent in 0..100 step 10) {
            val x = left + widthSpan * (percent / 100f)
            val isMajor = percent % 25 == 0
            val paint = if (isMajor) majorPaint else minorPaint
            val top = if (isMajor) majorTop else minorTop
            canvas.drawLine(x, top, x, height, paint)
        }
    }

    private fun dpToPx(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
