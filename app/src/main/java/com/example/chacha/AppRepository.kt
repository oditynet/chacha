package com.example.chacha

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class AppRepository(private val context: Context) {
    fun findDefaultApps(): List<AppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent("android.media.action.IMAGE_CAPTURE"),
            Intent(Intent.ACTION_VIEW).setType("vnd.android-dir/mms-sms"),
            Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://"))
        )
        val seen = mutableSetOf<String>()
        for (i in intents) {
            val ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) continue
            val ai = pm.getApplicationInfo(pkg, 0)
            result += AppInfo(
                packageName = pkg,
                className = ri.activityInfo.name,
                label = pm.getApplicationLabel(ai).toString(),
                icon = pm.getApplicationIcon(ai)
            )
            if (result.size == 4) break
        }
        return result
    }
    suspend fun loadAll(hidden: Set<String> = emptySet()): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (hidden.contains(pkg)) return@mapNotNull null
                AppInfo(
                    packageName = pkg,
                    className = ri.activityInfo.name,
                    label = ri.loadLabel(pm).toString(),
                    icon = ri.loadIcon(pm)
                )
            }.sortedBy { it.label.lowercase() }
        }

    fun loadDockApps(): List<AppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()
        val dial = Intent(Intent.ACTION_DIAL)
        val ri = pm.resolveActivity(dial, PackageManager.MATCH_DEFAULT_ONLY) ?: return result
        val appInfo = pm.getApplicationInfo(ri.activityInfo.packageName, 0)
        result += AppInfo(
            packageName = ri.activityInfo.packageName,
            className = ri.activityInfo.name,
            label = pm.getApplicationLabel(appInfo).toString(),
            icon = pm.getApplicationIcon(appInfo)
        )
        return result
    }

    fun launch(app: AppInfo) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.packageName, app.className)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
}