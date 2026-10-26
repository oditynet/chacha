package com.example.chacha

import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Полупрозрачный виджет календаря.
 * - Свайп влево/вправо — переключение месяцев.
 * - Маленькая затемнённая кнопка в правом верхнем углу — возврат к текущему месяцу.
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

    // ============================================================
    //  ФОН ВСЕГО КАЛЕНДАРЯ (на весь вид)
    // ============================================================
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)   // тёмный полупрозрачный фон (~78%)
        style = Paint.Style.FILL
    }
    private val bgRect = RectF()

    // ============================================================
    //  МАЛЕНЬКАЯ ПОДЛОЖКА КНОПКИ "ТЕКУЩИЙ МЕСЯЦ" (правый верх)
    // ============================================================
    private val resetBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(75, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val resetBgRect = RectF()

    // Кисти для текста
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

    // --- Свайпы / смещение месяца ---

    /** Сдвиг отображаемого месяца относительно текущего: 0 = текущий */
    private var monthOffset: Int = 0

    /** Горизонтальный сдвиг Canvas во время анимации перелистывания */
    private var slideTranslationX: Float = 0f

    private var slideAnimator: ValueAnimator? = null

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchLastX = 0f
    private var isDragging = false
    private var dragDirectionDecided = false

    private val swipeThresholdPx = 80f * resources.displayMetrics.density

    // --- Кнопка "текущий месяц" ---
    private val resetHitRect = RectF()
    private var resetTapCandidate = false
    private var resetPressed = false
    private val touchSlopPx = 16f * resources.displayMetrics.density

    private var switched = false

    init {
        isClickable = true
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    if (i?.action == Intent.ACTION_DATE_CHANGED) {
                        monthOffset = 0
                    }
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
        slideAnimator?.cancel()
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
        super.onDetachedFromWindow()
    }

    // --- Размеры и hit-зона кнопки "текущий месяц" ---

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateResetHitRect(w.toFloat(), h.toFloat())
    }

    /**
     * Hit-зона кнопки — совпадает с её визуальным размером:
     * ширина w/8, высота h/10, прижата к правому верхнему углу.
     */
    private fun updateResetHitRect(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) {
            resetHitRect.setEmpty()
            return
        }
        val hitW = w / 8f
        val hitH = h / 10f
        val margin = h * 0.02f
        resetHitRect.set(
            w - hitW - margin,
            margin,
            w - margin,
            margin + hitH
        )
    }

    // --- Жесты ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchLastX = event.x
                isDragging = true
                dragDirectionDecided = false
                slideAnimator?.cancel()
                slideTranslationX = 0f
                resetTapCandidate = resetHitRect.contains(event.x, event.y)
                resetPressed = resetTapCandidate
                if (resetPressed) invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return true
                if (resetTapCandidate) {
                    val dxTotal = event.x - touchStartX
                    val dyTotal = event.y - touchStartY
                    if (abs(dxTotal) > touchSlopPx || abs(dyTotal) > touchSlopPx) {
                        resetTapCandidate = false
                        if (resetPressed) {
                            resetPressed = false
                            invalidate()
                        }
                    }
                }

                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (!dragDirectionDecided) {
                    if (abs(dx) > 16f && abs(dx) > abs(dy)) {
                        dragDirectionDecided = true
                    } else if (abs(dy) > 16f && abs(dy) > abs(dx)) {
                        isDragging = false
                        slideTranslationX = 0f
                        resetTapCandidate = false
                        resetPressed = false
                        invalidate()
                        return true
                    }
                }
                if (dragDirectionDecided) {
                    slideTranslationX = dx * 0.35f
                    invalidate()
                }
                touchLastX = event.x
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    resetTapCandidate = false
                    resetPressed = false
                    invalidate()
                    return true
                }
                isDragging = false

                // Тап по кнопке "текущий месяц"
                if (resetTapCandidate && event.actionMasked == MotionEvent.ACTION_UP) {
                    resetTapCandidate = false
                    resetPressed = false
                    slideAnimator?.cancel()
                    slideTranslationX = 0f
                    if (monthOffset != 0) {
                        monthOffset = 0
                    }
                    invalidate()
                    return true
                }
                resetTapCandidate = false
                if (resetPressed) {
                    resetPressed = false
                    invalidate()
                }

                // Свайп
                val dx = event.x - touchStartX
                if (dragDirectionDecided && abs(dx) >= swipeThresholdPx) {
                    if (dx < 0) goToNextMonth() else goToPrevMonth()
                } else {
                    slideTranslationX = 0f
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun goToNextMonth() {
        animateMonthChange(+1)
    }

    private fun goToPrevMonth() {
        animateMonthChange(-1)
    }

    private fun animateMonthChange(direction: Int) {
        slideAnimator?.cancel()
        val w = width.toFloat().coerceAtLeast(1f)
        val exitTo = -direction * w
        val enterFrom = direction * w

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                slideTranslationX = if (t < 0.5f) {
                    exitTo * (t / 0.5f)
                } else {
                    enterFrom * (1f - (t - 0.5f) / 0.5f)
                }
                if (t >= 0.5f && !switched) {
                    switched = true
                    monthOffset += direction
                }
                invalidate()
            }
        }

        var cancelled = false
        animator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!cancelled) {
                    slideTranslationX = 0f
                    switched = false
                    invalidate()
                }
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })

        switched = false
        slideAnimator = animator
        animator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * 0.62f).toInt().coerceAtLeast((w * 0.45f).toInt())
        setMeasuredDimension(w, h)
    }

    // ============================================================
    //  ОТРИСОВКА
    // ============================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // ------------------------------------------------------------
        //  1. ФОН ВСЕГО КАЛЕНДАРЯ — на весь вид (0,0 → w,h)
        // ------------------------------------------------------------
        val corner = h * 0.10f
        bgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, corner, corner, bgPaint)

        // ------------------------------------------------------------
        //  2. МАЛЕНЬКАЯ ПОДЛОЖКА КНОПКИ — в правом верхнем углу
        //     Ширина w/8, высота h/10. Рисуется ПОВЕРХ фона,
        //     но ДО canvas.translate(), чтобы не уезжать при свайпе.
        // ------------------------------------------------------------
        if (!resetHitRect.isEmpty) {
            resetBgRect.set(resetHitRect)
            val r = (resetHitRect.height()) * 0.35f
            resetBgPaint.color = if (resetPressed) {
                Color.argb(130, 0, 0, 0)
            } else {
                Color.argb(80, 0, 0, 0)
            }
            canvas.drawRoundRect(resetBgRect, r, r, resetBgPaint)
        }

        // ------------------------------------------------------------
        //  3. СЕТКА ДНЕЙ (сдвигается при анимации свайпа)
        // ------------------------------------------------------------
        canvas.save()
        canvas.translate(slideTranslationX, 0f)

        val realNow = Calendar.getInstance(locale)
        val todayYear = realNow.get(Calendar.YEAR)
        val todayMonth = realNow.get(Calendar.MONTH)
        val todayDay = realNow.get(Calendar.DAY_OF_MONTH)

        val shown = Calendar.getInstance(locale).apply {
            clear()
            set(todayYear, todayMonth, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val year = shown.get(Calendar.YEAR)
        val month = shown.get(Calendar.MONTH)
        val isCurrentMonth = (monthOffset == 0)

        val df = SimpleDateFormat("LLLL yyyy", locale)
        val monthTitle = df.format(shown.time).replaceFirstChar { it.uppercaseChar() }

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

        // Первое число отображаемого месяца
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

            val highlight = isCurrentMonth && day == todayDay
            if (highlight) {
                val r = min(colW, rowH) * 0.60f
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

        canvas.restore()
    }
}