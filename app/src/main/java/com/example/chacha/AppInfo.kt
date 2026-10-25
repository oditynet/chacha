package com.example.chacha

import android.graphics.drawable.Drawable
import java.io.Serializable

data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable?
) : Serializable {
    val key: String get() = "$packageName/$className"
}