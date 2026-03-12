package com.duda.appvolumecontrol.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

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

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPx(2f)
        strokeCap = Paint.Cap.ROUND
    }

    private val segmentRects = mutableListOf<SegmentRect>()
    private val markerLines = mutableListOf<MarkerLine>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val minorTop = height * 0.55f
        val majorTop = height * 0.25f
        val segmentTop = height * 0.15f
        val segmentBottom = height * 0.9f
        val markerTop = height * 0.15f
        val markerBottom = height * 0.95f
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val widthSpan = right - left

        for (segment in segmentRects) {
            val start = segment.start.coerceIn(0f, 1f)
            val end = segment.end.coerceIn(0f, 1f)
            if (end <= start) continue
            val xStart = left + widthSpan * start
            val xEnd = left + widthSpan * end
            segmentPaint.color = segment.color
            canvas.drawRoundRect(RectF(xStart, segmentTop, xEnd, segmentBottom), dpToPx(3f), dpToPx(3f), segmentPaint)
        }

        for (percent in 0..100 step 10) {
            val x = left + widthSpan * (percent / 100f)
            val isMajor = percent % 25 == 0
            val paint = if (isMajor) majorPaint else minorPaint
            val top = if (isMajor) majorTop else minorTop
            canvas.drawLine(x, top, x, height, paint)
        }

        for (marker in markerLines) {
            val x = left + widthSpan * marker.position
            markerPaint.color = marker.color
            canvas.drawLine(x, markerTop, x, markerBottom, markerPaint)
        }
    }

    fun setCustomMarkers(markers: List<MarkerLine>, segments: List<SegmentRect>) {
        markerLines.clear()
        markerLines.addAll(markers)
        segmentRects.clear()
        segmentRects.addAll(segments)
        invalidate()
    }

    private fun dpToPx(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    data class MarkerLine(
        val position: Float,
        val color: Int,
    )

    data class SegmentRect(
        val start: Float,
        val end: Float,
        val color: Int,
    )
}
