package com.amarjeet.motionspot

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MotionDetector {

    @Volatile
    var sensitivity: Float = 0.6f

    @Volatile
    private var resetFlag = false

    private var prev: IntArray? = null
    private var prevKey = 0
    private var hits = 0

    fun reset() {
        resetFlag = true
    }

    private fun toRaw(ux: Float, uy: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> Pair(uy, 1f - ux)
        180 -> Pair(1f - ux, 1f - uy)
        270 -> Pair(1f - uy, ux)
        else -> Pair(ux, uy)
    }

    fun analyze(image: ImageProxy, box: RectF): Boolean {
        if (resetFlag) {
            resetFlag = false
            prev = null
            hits = 0
        }

        val w = image.width
        val h = image.height
        val rotation = image.imageInfo.rotationDegrees
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixStride = plane.pixelStride

        val a = toRaw(box.left, box.top, rotation)
        val b = toRaw(box.right, box.bottom, rotation)
        val rx0 = min(a.first, b.first)
        val rx1 = max(a.first, b.first)
        val ry0 = min(a.second, b.second)
        val ry1 = max(a.second, b.second)

        val x0 = (rx0 * w).toInt().coerceIn(1, w - 4)
        val x1 = (rx1 * w).toInt().coerceIn(x0 + 2, w - 2)
        val y0 = (ry0 * h).toInt().coerceIn(1, h - 4)
        val y1 = (ry1 * h).toInt().coerceIn(y0 + 2, h - 2)

        val grid = GRID
        val cur = IntArray(grid * grid)
        for (j in 0 until grid) {
            val y = y0 + ((y1 - y0) * (j + 0.5f) / grid).toInt()
            for (i in 0 until grid) {
                val x = x0 + ((x1 - x0) * (i + 0.5f) / grid).toInt()
                var sum = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val idx = (y + dy) * rowStride + (x + dx) * pixStride
                        sum += buf.get(idx).toInt() and 0xFF
                    }
                }
                cur[j * grid + i] = sum / 9
            }
        }

        val key = (((x0 * 31 + y0) * 31 + x1) * 31 + y1) * 31 + w
        val p = prev
        if (p == null || key != prevKey) {
            prev = cur
            prevKey = key
            hits = 0
            return false
        }

        var total = 0L
        for (k in cur.indices) total += cur[k] - p[k]
        val mean = total.toFloat() / cur.size

        val s = sensitivity
        val pixThreshold = 40f - 25f * s
        val fractionThreshold = 0.10f - 0.085f * s

        var changed = 0
        for (k in cur.indices) {
            if (abs(cur[k] - p[k] - mean) > pixThreshold) changed++
        }
        prev = cur

        val fraction = changed.toFloat() / cur.size
        if (fraction >= fractionThreshold) hits++ else hits = 0
        return hits >= 2
    }

    companion object {
        private const val GRID = 32
    }
}
