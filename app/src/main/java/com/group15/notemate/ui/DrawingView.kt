package com.group15.notemate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import com.group15.notemate.model.StrokePoint

/**
 * MVVM: View. The area the student writes on with a finger or a stylus.
 * It draws the stroke being written and reports each finished stroke with [onStrokeFinished];
 * the ViewModel keeps the strokes and gives them back with [setStrokes].
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onStrokeFinished: ((List<StrokePoint>) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = MaterialColors.getColor(this@DrawingView, com.google.android.material.R.attr.colorOnSurface)
    }

    private val finishedPath = Path()
    private val currentPath = Path()
    private val currentPoints = mutableListOf<StrokePoint>()

    /** Shows [strokes] (the finished strokes kept by the ViewModel) instead of what is on the canvas. */
    fun setStrokes(strokes: List<List<StrokePoint>>) {
        finishedPath.reset()
        for (stroke in strokes) {
            for ((index, point) in stroke.withIndex()) {
                // moveTo + lineTo to the same point draws a dot, e.g. on an "i"
                if (index == 0) finishedPath.moveTo(point.x, point.y)
                finishedPath.lineTo(point.x, point.y)
            }
        }
        currentPath.reset()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(finishedPath, paint)
        canvas.drawPath(currentPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val point = StrokePoint(event.x, event.y, event.eventTime)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Keep the touch here even when the view is inside a scrolling parent
                parent?.requestDisallowInterceptTouchEvent(true)
                currentPoints.clear()
                currentPoints += point
                currentPath.moveTo(point.x, point.y)
                currentPath.lineTo(point.x, point.y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPoints += point
                currentPath.lineTo(point.x, point.y)
            }
            MotionEvent.ACTION_UP -> {
                currentPoints += point
                currentPath.lineTo(point.x, point.y)
                onStrokeFinished?.invoke(currentPoints.toList())
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> currentPath.reset()
            else -> return false
        }
        invalidate()
        return true
    }

    // Accessibility: a finished stroke counts as a click
    override fun performClick(): Boolean = super.performClick()

    private companion object {
        const val STROKE_WIDTH_DP = 4f
    }
}
