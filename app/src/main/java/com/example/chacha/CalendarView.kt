package com.example.chacha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

/**
 * Полупрозрачный виджет календаря текущего месяца.
 * Не принимает касания, обновляется автоматически
 * при смене даты/месяца через broadcast и периодический invalidate.
 */
class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private val locale: Locale = Locale.getDefault()

    // Названия дней недели начиная с понедельника
    private val weekDays: List<String> = run {
        val fmt = SimpleDateFormat("EE", locale)
        val base = Calendar.getInstance(locale).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        (0 until 7).map {
            val s = fmt.format(base.time).replace(".", "")
            base.add(Calendar.DAY_OF_MONTH, 1)
            s.take(2).replaceFirstChar { c -> c.uppercaseChar() }
        }
    }

    // Фон календаря — чуть темнее, полупрозрачный, скруглённый
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0) // чёрный ~47% — затемняет подложку
        style = Paint.Style.FILL
    }
    private val bgRect = RectF()

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }
    private val todayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 108, 140, 255)
        style = Paint.Style.FILL
    }
    private val todayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 60_000L)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    invalidate()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
            }
            try {
                context.registerReceiver(receiver, filter)
            } catch (_: Exception) {
            }
        }
        handler.post(tickRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tickRunnable)
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * 0.62f).toInt().coerceAtLeast((w * 0.45f).toInt())
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // --- Тёмный полупрозрачный фон со скруглением ---
        val corner = h * 0.10f
        bgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, corner, corner, bgPaint)

        val now = Calendar.getInstance(locale)
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        val today = now.get(Calendar.DAY_OF_MONTH)

        val df = SimpleDateFormat("LLLL yyyy", locale)
        val monthTitle = df.format(now.time).replaceFirstChar { it.uppercaseChar() }

        val padH = w * 0.05f
        val padV = h * 0.06f

        // Заголовок месяца
        monthPaint.textSize = h * 0.09f
        canvas.drawText(monthTitle, padH, padV + monthPaint.textSize, monthPaint)

        // Дни недели
        headerPaint.textSize = h * 0.065f
        val colW = (w - 2 * padH) / 7f
        val headerY = padV + h * 0.19f
        for (i in 0 until 7) {
            val x = padH + colW * (i + 0.5f)
            canvas.drawText(weekDays[i], x, headerY, headerPaint)
        }

        // Первое число месяца
        val firstOfMonth = Calendar.getInstance(locale).apply {
            clear()
            set(year, month, 1)
            firstDayOfWeek = Calendar.MONDAY
        }
        val dow = firstOfMonth.get(Calendar.DAY_OF_WEEK)
        val offset = ((dow + 5) % 7)
        val daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        val gridTop = headerY + h * 0.05f
        val gridBottom = h - padV * 0.5f
        val rowH = (gridBottom - gridTop) / 6f

        dayPaint.textSize = h * 0.075f
        todayTextPaint.textSize = h * 0.075f

        for (day in 1..daysInMonth) {
            val idx = offset + day - 1
            val row = idx / 7
            val col = idx % 7
            if (row >= 6) continue
            val cx = padH + colW * (col + 0.5f)
            val cy = gridTop + rowH * (row + 0.5f)

            if (day == today) {
                val r = min(colW, rowH) * 0.42f
                canvas.drawCircle(cx, cy, r, todayBgPaint)
                val fm = todayTextPaint.fontMetrics
                val baseline = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(day.toString(), cx, baseline, todayTextPaint)
            } else {
                val fm = dayPaint.fontMetrics
                val baseline = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(day.toString(), cx, baseline, dayPaint)
            }
        }
    }
}