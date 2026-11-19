package com.amarjeet.motionspot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SplashIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#FF5A4F")
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5BD38A")
        pathEffect = DashPathEffect(floatArrayOf(7f, 5f), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5A4F")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height) / 220f
        val ox = (width - 220f * s) / 2f
        val oy = (height - 220f * s) / 2f
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)

        ringPaint.alpha = 41
        canvas.drawCircle(110f, 110f, 102f, ringPaint)
        ringPaint.alpha = 82
        canvas.drawCircle(110f, 110f, 78f, ringPaint)
        ringPaint.alpha = 140
        canvas.drawCircle(110f, 110f, 54f, ringPaint)

        canvas.drawRoundRect(68f, 82f, 152f, 138f, 4f, 4f, boxPaint)

        dotPaint.alpha = 255
        canvas.drawCircle(126f, 108f, 8f, dotPaint)
        dotPaint.alpha = 128
        canvas.drawCircle(104f, 114f, 4f, dotPaint)
        dotPaint.alpha = 77
        canvas.drawCircle(90f, 119f, 2.5f, dotPaint)

        canvas.restore()
    }
}
