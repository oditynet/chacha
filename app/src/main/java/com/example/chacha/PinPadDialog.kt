package com.example.chacha

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class PinPadDialog(
    context: Context,
    private val title: String,
    private val verify: ((String) -> Boolean)? = null,
    private val onResult: (String) -> Unit
) : Dialog(context) {

    private val entered = StringBuilder()
    private lateinit var dotsView: TextView
    private lateinit var grid: GridLayout
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#1A1A2E")))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        root.addView(titleView)

        dotsView = TextView(context).apply {
            text = "○ ○ ○ ○"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        root.addView(dotsView)

        message = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#FF6B6B"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(message)

        grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 3
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val gridLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(grid, gridLp)

        val deleteBtn = Button(context).apply {
            text = "⌫ Удалить"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#44446E"))
            setPadding(32, 16, 32, 16)
            setOnClickListener { onDelete() }
        }
        val deleteLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 24
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(deleteBtn, deleteLp)

        setContentView(root)
        buildPad()
    }

    override fun onStart() {
        super.onStart()
        val dm = context.resources.displayMetrics
        val width = (dm.widthPixels * 0.9f).toInt()
        val maxWidth = (420 * dm.density).toInt()
        val finalWidth = width.coerceAtMost(maxWidth)
        window?.setLayout(finalWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER)
    }

    private fun shuffledDigits(): List<Int> {
        val base = mutableListOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val rnd = java.util.Random()
        do {
            base.shuffle(rnd)
        } while (base == listOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        return base
    }

    private fun buildPad() {
        grid.removeAllViews()
        val digits = shuffledDigits()

        val density = context.resources.displayMetrics.density
        val buttonSize = (72 * density).toInt()

        for (d in digits) {
            val btn = Button(context).apply {
                text = d.toString()
                textSize = 22f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2E2E4E"))
                val lp = GridLayout.LayoutParams()
                lp.width = buttonSize
                lp.height = buttonSize
                lp.setMargins(8, 8, 8, 8)
                layoutParams = lp
                setOnClickListener { onDigit(d) }
            }
            grid.addView(btn)
        }
    }

    private fun onDigit(d: Int) {
        if (entered.length >= 4) return
        entered.append(d)
        updateDots()

        if (entered.length == 4) {
            val pin = entered.toString()
            if (verify == null) {
                dismiss()
                onResult(pin)
            } else {
                if (verify(pin)) {
                    dismiss()
                    onResult(pin)
                } else {
                    message.text = "Неверный PIN"
                    entered.clear()
                    updateDots()
                    buildPad()
                }
            }
        }
    }

    private fun onDelete() {
        if (entered.isNotEmpty()) {
            entered.deleteCharAt(entered.length - 1)
            updateDots()
            message.text = ""
        }
    }

    private fun updateDots() {
        val filled = entered.length
        dotsView.text = (1..4).joinToString(" ") { if (it <= filled) "●" else "○" }
    }
}