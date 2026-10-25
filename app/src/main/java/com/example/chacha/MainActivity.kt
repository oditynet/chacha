package com.example.chacha

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var repo: AppRepository
    private lateinit var workspaceStore: WorkspaceStore
    private lateinit var hiddenStore: HiddenAppsStore
    private lateinit var pinStore: PinStore
    private lateinit var wallpaperStore: WallpaperStore

    private var allApps: List<AppInfo> = emptyList()
    private var drawerFullList: List<AppInfo> = emptyList()
    private lateinit var workspaceAdapter: WorkspaceAdapter
    private lateinit var drawerAdapter: AppDrawerAdapter

    private lateinit var gestureDetector: GestureDetector
    private lateinit var searchEdit: EditText
    private var drawerOpen = false

    private var wallpaperObserver: ContentObserver? = null

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            val preview = Intent(this, WallpaperPreviewActivity::class.java).apply {
                putExtra(WallpaperPreviewActivity.EXTRA_URI, uri.toString())
            }
            previewLauncher.launch(preview)
        }

    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == WallpaperPreviewActivity.RESULT_OK_SAVED) {
                applyWallpaper()
                Toast.makeText(this, "Обои установлены", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)
        workspaceStore = WorkspaceStore(this)
        hiddenStore = HiddenAppsStore(this)
        pinStore = PinStore(this)
        wallpaperStore = WallpaperStore(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerOpen) closeDrawer() else finish()
            }
        })

        setupGestureDetector()
        applyInsets()
        setupWorkspace()
        setupDrawer()
        setupDock()
        setupDrawerDock()
        applyWallpaper()

        findViewById<View>(R.id.calendar)?.setOnTouchListener { _, _ -> false }

        if (!pinStore.hasPin()) {
            showSetupPin()
        } else {
            loadApps()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::wallpaperStore.isInitialized) applyWallpaper()
        registerWallpaperObserver()
    }

    override fun onPause() {
        unregisterWallpaperObserver()
        super.onPause()
    }

    private fun registerWallpaperObserver() {
        if (wallpaperObserver != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                runOnUiThread { applyWallpaper() }
            }
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, obs)
        wallpaperObserver = obs
    }

    private fun unregisterWallpaperObserver() {
        wallpaperObserver?.let { contentResolver.unregisterContentObserver(it) }
        wallpaperObserver = null
    }

    private fun loadApps() {
        lifecycleScope.launch {
            allApps = repo.loadAll(hiddenStore.get())
            val defaults = repo.findDefaultApps().map { it.key }
            workspaceStore.ensureDefaultDock(defaults)
            drawerFullList = allApps
            drawerAdapter.update(allApps)
            refreshWorkspace()
            refreshDock()
            refreshDrawerDock()
        }
    }

    private fun applyWallpaper() {
        val root = findViewById<View>(R.id.root) ?: return
        val bmp = wallpaperStore.loadBitmap()
        if (bmp != null) {
            root.background = BitmapDrawable(resources, bmp)
            root.invalidate()
            return
        }
        try {
            val wm = WallpaperManager.getInstance(this)
            val sys: Drawable? = wm.drawable
            if (sys != null) {
                root.background = sys
                root.invalidate()
                return
            }
        } catch (_: Exception) {}
        root.setBackgroundColor(0xFF1A1A2E.toInt())
    }

    private fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        pickPhotoLauncher.launch(Intent.createChooser(intent, "Выбрать фото"))
    }

    private fun grabCurrentSystemWallpaper() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val wm = WallpaperManager.getInstance(this@MainActivity)
                    val drawable: Drawable? = wm.drawable
                    val bmp: Bitmap? = drawableToBitmap(drawable)
                    if (bmp != null) wallpaperStore.saveFromBitmap(bmp) else false
                } catch (_: Exception) {
                    false
                }
            }
            if (ok) {
                applyWallpaper()
                Toast.makeText(this@MainActivity, "Обои установлены", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Не удалось прочитать обои", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return null
        return try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun resetWallpaper() {
        wallpaperStore.clear()
        applyWallpaper()
        Toast.makeText(this, "Обои сброшены", Toast.LENGTH_SHORT).show()
    }

    private fun showSetupPin() {
        PinPadDialog(this, "Придумайте PIN (4 цифры)", verify = null) { first ->
            PinPadDialog(this, "Повторите PIN", verify = null) { second ->
                if (first == second) {
                    pinStore.setPin(first)
                    loadApps()
                } else {
                    Toast.makeText(this, "PIN не совпал", Toast.LENGTH_SHORT).show()
                    showSetupPin()
                }
            }.show()
        }.show()
    }

    private fun openSettingsWithPin() {
        if (pinStore.hasPin()) {
            PinPadDialog(this, "Введите PIN", verify = { pinStore.checkPin(it) }) {
                showLauncherMenu()
            }.show()
        } else {
            showLauncherMenu()
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffY) <= abs(diffX)) return false

                if (diffY < -300f && !drawerOpen) {
                    openDrawer()
                    return true
                }
                if (diffY > 300f && drawerOpen) {
                    val rv = findViewById<RecyclerView>(R.id.appDrawer)
                    if (!rv.canScrollVertically(-1)) {
                        closeDrawer()
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        val navSpacer = findViewById<View>(R.id.navBarSpacer)
        val dock = findViewById<View>(R.id.dock)
        val workspace = findViewById<RecyclerView>(R.id.workspace)
        val drawerTopBlock = findViewById<View>(R.id.drawerTopBlock)
        val drawerList = findViewById<RecyclerView>(R.id.appDrawer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = maxOf(statusBars.top, cutout.top)
            val density = resources.displayMetrics.density

            navSpacer.layoutParams.height = navBars.bottom
            navSpacer.requestLayout()

            val dockLp = dock.layoutParams as? ViewGroup.MarginLayoutParams
            if (dockLp != null) {
                dockLp.bottomMargin = 0
                dock.layoutParams = dockLp
                dock.requestLayout()
            }

            workspace.setPadding(
                workspace.paddingLeft,
                topInset + (40 * density).toInt(),
                workspace.paddingRight,
                workspace.paddingBottom
            )

            val topLp = drawerTopBlock.layoutParams
            if (topLp is ViewGroup.MarginLayoutParams) {
                topLp.topMargin = topInset
                drawerTopBlock.layoutParams = topLp
                drawerTopBlock.requestLayout()
            }

            // Список приложений прижат вплотную к блоку поиска
            drawerTopBlock.post {
                drawerList.setPadding(
                    drawerList.paddingLeft,
                    0,
                    drawerList.paddingRight,
                    drawerList.paddingBottom
                )
            }

            insets
        }
    }

    private fun setupWorkspace() {
        val rv = findViewById<RecyclerView>(R.id.workspace)
        rv.layoutManager = GridLayoutManager(this, 4)
        workspaceAdapter = WorkspaceAdapter(
            mutableListOf(),
            onClick = { app -> repo.launch(app) },
            onLongClick = { app -> showRemoveButton(app) }
        )
        rv.adapter = workspaceAdapter

        rv.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) {
                val app = event.localState as? AppInfo ?: return@setOnDragListener false
                workspaceStore.addWorkspace(app.key)
                workspaceAdapter.add(app)
                true
            } else true
        }
    }

    private fun refreshWorkspace() {
        val keys = workspaceStore.getWorkspaceKeys()
        workspaceAdapter.setItems(allApps.filter { keys.contains(it.key) })
    }

    private fun setupDrawer() {
        val rv = findViewById<RecyclerView>(R.id.appDrawer)
        rv.layoutManager = GridLayoutManager(this, 4)
        drawerAdapter = AppDrawerAdapter(
            apps = mutableListOf(),
            selfPackage = packageName,
            onClick = { app ->
                repo.launch(app)
                closeDrawer()
            },
            onSelfLongClick = { openSettingsWithPin() },
            onAppLongClick = { app -> showAppOptions(app) }
        )
        rv.adapter = drawerAdapter
        rv.post { rv.translationY = rv.height.toFloat() }

        val container = findViewById<View>(R.id.drawerContainer)
        container.visibility = View.GONE
        container.alpha = 1f

        searchEdit = findViewById(R.id.drawerSearch)
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDrawer(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterDrawer(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) drawerFullList
        else drawerFullList.filter { it.label.lowercase().contains(q) }
        drawerAdapter.update(filtered)
    }

    private fun openDrawer() {
        val container = findViewById<View>(R.id.drawerContainer)
        val rv = findViewById<RecyclerView>(R.id.appDrawer)
        drawerOpen = true
        container.visibility = View.VISIBLE
        container.alpha = 1f
        rv.translationY = rv.height.toFloat()
        rv.animate().translationY(0f).setDuration(220).start()
        refreshDrawerDock()
    }

    private fun closeDrawer() {
        val container = findViewById<View>(R.id.drawerContainer)
        val rv = findViewById<RecyclerView>(R.id.appDrawer)
        drawerOpen = false
        if (::searchEdit.isInitialized) searchEdit.setText("")
        rv.animate().translationY(rv.height.toFloat()).setDuration(220)
            .withEndAction { container.visibility = View.GONE }
            .start()
    }

    private fun setupDock() {
        val dock = findViewById<LinearLayout>(R.id.dock)
        for (index in 0 until 4) {
            val slot = layoutInflater.inflate(R.layout.item_dock, dock, false) as FrameLayout
            slot.tag = index

            val lp = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            slot.layoutParams = lp

            slot.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DROP -> {
                        val app = event.localState as? AppInfo ?: return@setOnDragListener false
                        workspaceStore.insertDock(index, app.key)
                        refreshDock()
                        refreshDrawerDock()
                        true
                    }
                    else -> true
                }
            }
            dock.addView(slot)
        }
        refreshDock()
    }

    private fun refreshDock() {
        val dock = findViewById<LinearLayout>(R.id.dock)
        val slots = workspaceStore.getDock()

        for (i in 0 until dock.childCount) {
            val slot = dock.getChildAt(i) as FrameLayout
            val icon = slot.findViewById<ImageView>(R.id.icon)
            val label = slot.findViewById<TextView>(R.id.label)

            val key = slots.getOrNull(i)
            val app = allApps.firstOrNull { it.key == key }

            if (app != null) {
                icon.setImageDrawable(app.icon)
                label.text = app.label
                icon.visibility = View.VISIBLE
                label.visibility = View.VISIBLE

                slot.setOnClickListener { repo.launch(app) }
                slot.setOnLongClickListener {
                    workspaceStore.removeFromDock(app.key)
                    refreshDock()
                    refreshDrawerDock()
                    true
                }
            } else {
                icon.setImageDrawable(null)
                label.text = ""
                icon.visibility = View.INVISIBLE
                label.visibility = View.INVISIBLE
                slot.setOnClickListener(null)
                slot.setOnLongClickListener(null)
            }
        }
        refreshDrawerDock()
    }

    private fun setupDrawerDock() {
        val dock = findViewById<LinearLayout>(R.id.drawerDock)
        for (index in 0 until 4) {
            val slot = layoutInflater.inflate(R.layout.item_dock, dock, false) as FrameLayout
            slot.tag = index

            val lp = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            slot.layoutParams = lp

            slot.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DROP -> {
                        val app = event.localState as? AppInfo ?: return@setOnDragListener false
                        workspaceStore.insertDock(index, app.key)
                        refreshDock()
                        refreshDrawerDock()
                        true
                    }
                    else -> true
                }
            }
            dock.addView(slot)
        }
        refreshDrawerDock()
    }

    private fun refreshDrawerDock() {
        val dock = findViewById<LinearLayout>(R.id.drawerDock) ?: return
        val slots = workspaceStore.getDock()

        for (i in 0 until dock.childCount) {
            val slot = dock.getChildAt(i) as FrameLayout
            val icon = slot.findViewById<ImageView>(R.id.icon)
            val label = slot.findViewById<TextView>(R.id.label)

            val key = slots.getOrNull(i)
            val app = allApps.firstOrNull { it.key == key }

            if (app != null) {
                icon.setImageDrawable(app.icon)
                label.text = app.label
                icon.visibility = View.VISIBLE
                label.visibility = View.VISIBLE

                slot.setOnClickListener { repo.launch(app) }
                slot.setOnLongClickListener {
                    workspaceStore.removeFromDock(app.key)
                    refreshDock()
                    refreshDrawerDock()
                    true
                }
            } else {
                icon.setImageDrawable(null)
                label.text = ""
                icon.visibility = View.INVISIBLE
                label.visibility = View.INVISIBLE
                slot.setOnClickListener(null)
                slot.setOnLongClickListener(null)
            }
        }
    }

    private fun showAppOptions(app: AppInfo) {
        val onWorkspace = workspaceStore.getWorkspaceKeys().contains(app.key)
        val options = mutableListOf<String>()
        if (onWorkspace) options.add("Убрать с рабочего стола")
        else options.add("Добавить на рабочий стол")
        options.add("В док 1")
        options.add("В док 2")
        options.add("В док 3")
        options.add("В док 4")

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        if (onWorkspace) {
                            workspaceStore.removeWorkspace(app.key)
                            refreshWorkspace()
                            Toast.makeText(this, "Убрано с рабочего стола", Toast.LENGTH_SHORT).show()
                        } else {
                            workspaceStore.addWorkspace(app.key)
                            refreshWorkspace()
                            Toast.makeText(this, "Добавлено на рабочий стол", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        workspaceStore.insertDock(0, app.key)
                        refreshDock(); refreshDrawerDock()
                        Toast.makeText(this, "В док 1", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        workspaceStore.insertDock(1, app.key)
                        refreshDock(); refreshDrawerDock()
                        Toast.makeText(this, "В док 2", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        workspaceStore.insertDock(2, app.key)
                        refreshDock(); refreshDrawerDock()
                        Toast.makeText(this, "В док 3", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        workspaceStore.insertDock(3, app.key)
                        refreshDock(); refreshDrawerDock()
                        Toast.makeText(this, "В док 4", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRemoveButton(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setMessage("Убрать с рабочего стола?")
            .setPositiveButton("Убрать") { _, _ ->
                workspaceAdapter.remove(app)
                workspaceStore.removeWorkspace(app.key)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showLauncherMenu() {
        val options = arrayOf(
            "Скрытые приложения (запуск)",
            "Управление скрытыми",
            "Сменить PIN",
            "Обои: мои фото",
            "Взять текущие системные обои",
            "Сбросить обои"
        )
        AlertDialog.Builder(this)
            .setTitle("Настройки лаунчера")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, HiddenAppsActivity::class.java))
                    1 -> showHiddenAppsDialog()
                    2 -> showSetupPin()
                    3 -> pickFromGallery()
                    4 -> grabCurrentSystemWallpaper()
                    5 -> resetWallpaper()
                }
            }
            .show()
    }

    private fun showHiddenAppsDialog() {
        lifecycleScope.launch {
            val installed = repo.loadAll(emptySet())
            val hidden = hiddenStore.get()

            val unique = installed.distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            val pkgs = unique.map { it.packageName }.toTypedArray()
            val labels = unique.map { it.label }.toTypedArray()
            val checked = BooleanArray(pkgs.size) { hidden.contains(pkgs[it]) }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Скрытые приложения")
                .setMultiChoiceItems(labels, checked) { _, i, isChecked ->
                    hiddenStore.toggle(pkgs[i], isChecked)
                }
                .setPositiveButton("Применить") { _, _ -> loadApps() }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}