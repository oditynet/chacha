package com.example.chacha

import android.content.Context

class WorkspaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)

    fun getWorkspaceKeys(): Set<String> =
        prefs.getStringSet("workspace_keys", emptySet()) ?: emptySet()

    fun addWorkspace(key: String) {
        val s = getWorkspaceKeys().toMutableSet().apply { add(key) }
        prefs.edit().putStringSet("workspace_keys", s).apply()
    }

    fun removeWorkspace(key: String) {
        val s = getWorkspaceKeys().toMutableSet().apply { remove(key) }
        prefs.edit().putStringSet("workspace_keys", s).apply()
    }

    fun getDock(): MutableList<String?> {
        val raw = prefs.getString("dock", null) ?: return MutableList(4) { null }
        val parts = raw.split("|", limit = 4).toMutableList()
        while (parts.size < 4) parts.add("")
        return parts.map { if (it.isEmpty()) null else it }.toMutableList()
    }

    fun setDock(slots: List<String?>) {
        val raw = slots.take(4).joinToString("|") { it ?: "" }
        prefs.edit().putString("dock", raw).apply()
    }

    fun insertDock(index: Int, key: String) {
        val slots = getDock()
        // Если это приложение уже есть в доке — освобождаем его старое место
        val existingIdx = slots.indexOf(key)
        if (existingIdx >= 0) slots[existingIdx] = null
        // Ставим в целевой слот, не трогая остальные
        slots[index] = key
        setDock(slots)
    }

    fun removeFromDock(key: String) {
        val slots = getDock()
        val idx = slots.indexOf(key)
        if (idx >= 0) {
            slots[idx] = null
            setDock(slots)
        }
    }

    fun ensureDefaultDock(defaults: List<String>) {
        val current = getDock()
        if (current.any { it != null }) return
        val slots = MutableList<String?>(4) { null }
        defaults.take(4).forEachIndexed { i, key -> slots[i] = key }
        setDock(slots)
    }
}