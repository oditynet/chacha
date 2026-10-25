package com.example.chacha

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    /** Приложения, которые НЕ показывать ни в каком режиме */
    fun hiddenApps(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
    fun setHiddenApps(pkgs: Set<String>) = prefs.edit().putStringSet("hidden", pkgs).apply()

    /** Приложения, которые показывать только в РЕАЛЬНОМ режиме (не в фейковом) */
    fun realOnlyApps(): Set<String> = prefs.getStringSet("real_only", emptySet()) ?: emptySet()
    fun setRealOnlyApps(pkgs: Set<String>) = prefs.edit().putStringSet("real_only", pkgs).apply()
}