package com.amarjeet.motionspot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class BoxOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var box = RectF(0.2f, 0.2f, 0.8f, 0.8f)
        set(value) {
            field = value
            invalidate()
        }
    var editable = true
    var active = false
    var onBoxChanged: ((RectF) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private var startX = 0f
    private var startY = 0f
    private var previousBox = RectF(0.2f, 0.2f, 0.8f, 0.8f)

    private fun imageRect(): RectF {
        val vw = width.toFloat()
        val vh = height.toFloat()
        val aspect = 3f / 4f
        var w = vw
        var h = w / aspect
        if (h > vh) {
            h = vh
            w = h * aspect
        }
        val l = (vw - w) / 2f
        val t = (vh - h) / 2f
        return RectF(l, t, l + w, t + h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = imageRect()
        val color = if (active) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252")
        fillPaint.color = color
        fillPaint.alpha = 50
        strokePaint.color = color
        val rect = RectF(
            r.left + box.left * r.width(),
            r.top + box.top * r.height(),
            r.left + box.right * r.width(),
            r.top + box.bottom * r.height()
        )
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, strokePaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editable) return false
        val r = imageRect()
        val nx = ((e.x - r.left) / r.width()).coerceIn(0f, 1f)
        val ny = ((e.y - r.top) / r.height()).coerceIn(0f, 1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousBox = RectF(box)
                startX = nx
                startY = ny
                box = RectF(nx, ny, nx, ny)
            }
            MotionEvent.ACTION_MOVE -> {
                box = RectF(min(startX, nx), min(startY, ny), max(startX, nx), max(startY, ny))
            }
            MotionEvent.ACTION_UP -> {
                box = RectF(min(startX, nx), min(startY, ny), max(startX, nx), max(startY, ny))
                if (box.width() < 0.05f || box.height() < 0.05f) {
                    box = previousBox
                } else {
                    onBoxChanged?.invoke(RectF(box))
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                box = previousBox
            }
        }
        return true
    }
}
