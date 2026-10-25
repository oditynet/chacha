package com.example.chacha

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class HiddenAppsActivity : AppCompatActivity() {

    private lateinit var repo: AppRepository
    private lateinit var hiddenStore: HiddenAppsStore
    private lateinit var adapter: AppDrawerAdapter

    private var hiddenApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_apps)

        // Отступ под статус-бар
        val root = findViewById<View>(R.id.hiddenAppsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBars.top + view.paddingTop,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        repo = AppRepository(this)
        hiddenStore = HiddenAppsStore(this)

        val rv = findViewById<RecyclerView>(R.id.hiddenAppsList)
        rv.layoutManager = GridLayoutManager(this, 4)
        adapter = AppDrawerAdapter(
            apps = mutableListOf(),
            selfPackage = "",
            onClick = { app -> repo.launch(app) },
            onSelfLongClick = { /* не используется */ }
        )
        rv.adapter = adapter

        loadHiddenApps()
    }

    private fun loadHiddenApps() {
        lifecycleScope.launch {
            val all = repo.loadAll(emptySet())
            val hiddenPkgs = hiddenStore.get()
            hiddenApps = all.filter { hiddenPkgs.contains(it.packageName) }
            adapter.update(hiddenApps)

            adapter.setOnItemLongClickListener { app ->
                AlertDialog.Builder(this@HiddenAppsActivity)
                    .setTitle(app.label)
                    .setMessage("Показать в общем списке приложений?")
                    .setPositiveButton("Показать") { _, _ ->
                        hiddenStore.remove(app.packageName)
                        loadHiddenApps()
                        Toast.makeText(
                            this@HiddenAppsActivity,
                            "«${app.label}» снова видно",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
    }
}