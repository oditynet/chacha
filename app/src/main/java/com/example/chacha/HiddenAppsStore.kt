package com.example.chacha

import android.content.Context

class HiddenAppsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)

    fun get(): Set<String> =
        prefs.getStringSet("pkgs", emptySet()) ?: emptySet()

    fun add(pkg: String) {
        val s = get().toMutableSet().apply { add(pkg) }
        prefs.edit().putStringSet("pkgs", s).apply()
    }

    fun remove(pkg: String) {
        val s = get().toMutableSet().apply { remove(pkg) }
        prefs.edit().putStringSet("pkgs", s).apply()
    }

    fun toggle(pkg: String, hidden: Boolean) {
        if (hidden) add(pkg) else remove(pkg)
    }
}