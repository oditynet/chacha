package com.example.chacha

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView(context: Context) : View(context) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dimPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val crop = RectF()
    private val touchSlop = 24f
    private var dragging = false
    private var resizing = false
    private var lastX = 0f
    private var lastY = 0f

    fun getCropRect(): RectF = RectF(crop)
    fun resetToCenter() {
        val w = width.toFloat()
        val h = height.toFloat()
        val side = min(w, h) * 0.8f
        val l = (w - side) / 2f
        val t = (h - side) / 2f
        crop.set(l, t, l + side, t + side)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (crop.isEmpty) resetToCenter()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Затемнение вне рамки
        canvas.drawRect(0f, 0f, width.toFloat(), crop.top, dimPaint)
        canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, dimPaint)
        canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, dimPaint)
        // Рамка
        canvas.drawRect(crop, borderPaint)
        // Уголок-хэндл справа снизу
        canvas.drawCircle(crop.right, crop.bottom, 18f, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val nearHandle = abs(event.x - crop.right) < touchSlop &&
                        abs(event.y - crop.bottom) < touchSlop
                resizing = nearHandle
                dragging = !nearHandle && crop.contains(event.x, event.y)
                lastX = event.x
                lastY = event.y
                return dragging || resizing
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (resizing) {
                    crop.right = (crop.right + dx).coerceIn(crop.left + 60f, width.toFloat())
                    crop.bottom = (crop.bottom + dy).coerceIn(crop.top + 60f, height.toFloat())
                    invalidate()
                } else if (dragging) {
                    moveCrop(dx, dy)
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                resizing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveCrop(dx: Float, dy: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cw = crop.width()
        val ch = crop.height()
        crop.left = (crop.left + dx).coerceIn(0f, w - cw)
        crop.top = (crop.top + dy).coerceIn(0f, h - ch)
        crop.right = crop.left + cw
        crop.bottom = crop.top + ch
    }
}