package com.example.chacha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class WallpaperPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val RESULT_OK_SAVED = 2001
        const val RESULT_CANCELLED = 2002
    }

    private enum class Mode { CENTER, FIT_WIDTH, CROP }

    private lateinit var previewImage: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var statusText: TextView
    private lateinit var btnCenter: Button
    private lateinit var btnFitWidth: Button
    private lateinit var btnCrop: Button
    private lateinit var previewContainer: FrameLayout

    private var originalBitmap: Bitmap? = null
    private var sourceBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var uri: Uri? = null
    private var mode: Mode = Mode.CENTER

    private var rotationDeg: Int = 0
    private var mirrored: Boolean = false

    // Размеры экрана (реальные, куда попадёт обои)
    private var screenW: Int = 0
    private var screenH: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, sb.top, 0, nb.bottom)
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }
        val btnCancel = Button(this).apply {
            text = "Отмена"
            setOnClickListener {
                setResult(RESULT_CANCELLED)
                finish()
            }
        }
        val btnApply = Button(this).apply {
            text = "Применить"
            setOnClickListener { applyWallpaper() }
        }
        topBar.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(btnApply, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(topBar)

        previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        previewImage = ImageView(this).apply {
            // FIT_XY — чтобы превью показывало ровно то, что будет на экране
            scaleType = ImageView.ScaleType.FIT_XY
            setBackgroundColor(0xFF111111.toInt())
        }
        previewContainer.addView(previewImage, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        cropOverlay = CropOverlayView(this).apply {
            visibility = View.GONE
        }
        previewContainer.addView(cropOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(previewContainer)

        statusText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(16, 4, 16, 4)
        }
        root.addView(statusText)

        val transformBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)
        }
        val btnRotateLeft = Button(this).apply {
            text = "⟲ 90°"
            setOnClickListener {
                rotationDeg = (rotationDeg + 270) % 360
                rebuildTransformed()
            }
        }
        val btnRotateRight = Button(this).apply {
            text = "⟳ 90°"
            setOnClickListener {
                rotationDeg = (rotationDeg + 90) % 360
                rebuildTransformed()
            }
        }
        val btnMirror = Button(this).apply {
            text = "⇋ Зеркало"
            setOnClickListener {
                mirrored = !mirrored
                rebuildTransformed()
            }
        }
        transformBar.addView(btnRotateLeft, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        transformBar.addView(btnRotateRight, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        transformBar.addView(btnMirror, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(transformBar)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 16)
        }
        btnCenter = Button(this).apply {
            text = "По центру"
            setOnClickListener { setMode(Mode.CENTER) }
        }
        btnFitWidth = Button(this).apply {
            text = "Во всю ширину"
            setOnClickListener { setMode(Mode.FIT_WIDTH) }
        }
        btnCrop = Button(this).apply {
            text = "Выбрать область"
            setOnClickListener { setMode(Mode.CROP) }
        }
        bottomBar.addView(btnCenter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(btnFitWidth, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(btnCrop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bottomBar)

        val rawUri = intent.getStringExtra(EXTRA_URI)
        if (rawUri == null) {
            Toast.makeText(this, "Нет фото", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        uri = Uri.parse(rawUri)
        loadBitmap(uri!!)
    }

    private fun loadBitmap(u: Uri) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(u)?.use { input ->
                        BitmapFactory.decodeStream(input, null, opts)
                    }
                    val maxSide = 2048
                    var scale = 1
                    while (opts.outWidth / scale > maxSide || opts.outHeight / scale > maxSide) {
                        scale *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = scale }
                    contentResolver.openInputStream(u)?.use { input ->
                        BitmapFactory.decodeStream(input, null, decodeOpts)
                    }
                } catch (e: Exception) {
                    Log.e("WallpaperPreview", "decode failed", e)
                    null
                }
            }
            if (bmp == null) {
                Toast.makeText(this@WallpaperPreviewActivity, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            originalBitmap = bmp
            rotationDeg = 0
            mirrored = false
            rebuildTransformed()
            setMode(Mode.CENTER)
        }
    }

    /** Применяет поворот/зеркало и обновляет предпросмотр */
    private fun rebuildTransformed() {
        val src = originalBitmap ?: return
        val matrix = Matrix()

        // Зеркало по горизонтали (лево ↔ право) относительно центра
        if (mirrored) {
            matrix.postScale(-1f, 1f, src.width / 2f, src.height / 2f)
        }
        // Поворот относительно центра
        if (rotationDeg != 0) {
            matrix.postRotate(rotationDeg.toFloat(), src.width / 2f, src.height / 2f)
        }

        val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        sourceBitmap = transformed
        updatePreview()
    }

    /** Генерирует превью-битмап в реальных пропорциях экрана по текущему режиму */
    private fun updatePreview() {
        val src = sourceBitmap ?: return
        if (screenW <= 0 || screenH <= 0) return

        // Размер превью-контейнера для отрисовки (пропорционален экрану)
        val vw = screenW
        val vh = screenH

        val bmp = when (mode) {
            Mode.CENTER -> renderCenter(src, vw, vh)
            Mode.FIT_WIDTH -> renderFitWidth(src, vw, vh)
            Mode.CROP -> renderCropPreview(src, vw, vh)
        }
        previewBitmap = bmp
        previewImage.setImageBitmap(bmp)

        if (mode == Mode.CROP) cropOverlay.resetToCenter()
        statusText.text = when (mode) {
            Mode.CENTER -> "По центру (пропорции сохранены, поля по бокам)"
            Mode.FIT_WIDTH -> "Во всю ширину (верх и низ обрезаны, пропорции сохранены)"
            Mode.CROP -> "Выберите область рамкой"
        }
    }

    private fun renderCenter(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF000000.toInt())
        val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), null)
        return out
    }

    /** Во всю ширину: ширина = экран, высота обрезается сверху и снизу (центр) */
    private fun renderFitWidth(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF000000.toInt())
        val scale = w.toFloat() / src.width
        val dh = src.height * scale
        // Центрируем по вертикали: верх/низ обрезаются симметрично
        val top = (h - dh) / 2f
        val dst = RectF(0f, top, w.toFloat(), top + dh)
        canvas.drawBitmap(src, null, dst, null)
        return out
    }

    private fun renderCropPreview(src: Bitmap, w: Int, h: Int): Bitmap {
        // Предпросмотр для режима CROP: показываем исходник FIT_CENTER (рамка на overlay)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF111111.toInt())
        val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), null)
        return out
    }

    private fun setMode(m: Mode) {
        mode = m
        btnCenter.isSelected = m == Mode.CENTER
        btnFitWidth.isSelected = m == Mode.FIT_WIDTH
        btnCrop.isSelected = m == Mode.CROP
        cropOverlay.visibility = if (m == Mode.CROP) View.VISIBLE else View.GONE
        if (m == Mode.CROP) cropOverlay.resetToCenter()
        updatePreview()
    }

    private fun applyWallpaper() {
        val src = sourceBitmap ?: return
        if (screenW <= 0 || screenH <= 0) return

        val result: Bitmap = when (mode) {
            Mode.CENTER -> renderCenter(src, screenW, screenH)
            Mode.FIT_WIDTH -> renderFitWidth(src, screenW, screenH)
            Mode.CROP -> cropFromOverlay(src, screenW, screenH)
        }

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                WallpaperStore(this@WallpaperPreviewActivity).saveFromBitmap(result)
            }
            if (ok) {
                setResult(RESULT_OK_SAVED)
                finish()
            } else {
                Toast.makeText(this@WallpaperPreviewActivity, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Кроп по рамке. Координаты рамки берём в пикселях превью-контейнера,
     * но так как превью-битмап имеет размеры screenW×screenH, а ImageView FIT_XY
     * растянут на previewContainer — нужно масштабировать координаты рамки
     * из размера контейнера в размер screenW×screenH.
     */
    private fun cropFromOverlay(src: Bitmap, w: Int, h: Int): Bitmap {
        // Размеры, в которых реально нарисовано превью (screenW×screenH),
        // и размеры контейнера, в котором лежит overlay (vw×vh)
        val vw = previewContainer.width.toFloat().coerceAtLeast(1f)
        val vh = previewContainer.height.toFloat().coerceAtLeast(1f)

        // Рамка overlay в координатах контейнера
        val r = cropOverlay.getCropRect()

        // Переводим в координаты превью-битмапа (screenW×screenH)
        val pxLeft = r.left * w / vw
        val pxTop = r.top * h / vh
        val pxRight = r.right * w / vw
        val pxBottom = r.bottom * h / vh

        // Превью-битмап для CROP был отрисован как FIT_CENTER исходника в screenW×screenH
        val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f

        // Переводим пиксели превью в пиксели src
        val relLeft = (pxLeft - left) / scale
        val relTop = (pxTop - top) / scale
        val relRight = (pxRight - left) / scale
        val relBottom = (pxBottom - top) / scale

        val cx = relLeft.coerceIn(0f, src.width.toFloat())
        val cy = relTop.coerceIn(0f, src.height.toFloat())
        val cw = (relRight - relLeft).coerceAtLeast(1f).coerceAtMost(src.width - cx)
        val ch = (relBottom - relTop).coerceAtLeast(1f).coerceAtMost(src.height - cy)

        val crop = Bitmap.createBitmap(src, cx.toInt(), cy.toInt(), cw.toInt(), ch.toInt())

        // Растягиваем кроп на весь экран с сохранением пропорций (CENTER_CROP)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF000000.toInt())
        val s = max(w.toFloat() / crop.width, h.toFloat() / crop.height)
        val dw2 = crop.width * s
        val dh2 = crop.height * s
        val l2 = (w - dw2) / 2f
        val t2 = (h - dh2) / 2f
        canvas.drawBitmap(crop, null, RectF(l2, t2, l2 + dw2, t2 + dh2), null)
        return out
    }
}