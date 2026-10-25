package com.example.chacha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

class WallpaperStore(private val context: Context) {

    private fun file(): File = File(context.filesDir, "launcher_wallpaper.png")

    fun hasWallpaper(): Boolean = file().exists() && file().length() > 0

    fun saveFromUri(uri: Uri): Boolean {
        return try {
            file().delete()
            context.contentResolver.openInputStream(uri)?.use { input ->
                file().outputStream().use { output -> input.copyTo(output) }
            }
            val ok = file().exists() && file().length() > 0
            Log.d("WallpaperStore", "saveFromUri ok=$ok size=${file().length()}")
            ok
        } catch (e: Exception) {
            Log.e("WallpaperStore", "saveFromUri failed", e)
            false
        }
    }

    fun saveFromBitmap(bitmap: Bitmap): Boolean {
        return try {
            file().delete()
            file().outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val ok = file().exists() && file().length() > 0
            Log.d("WallpaperStore", "saveFromBitmap ok=$ok size=${file().length()}")
            ok
        } catch (e: Exception) {
            Log.e("WallpaperStore", "saveFromBitmap failed", e)
            false
        }
    }

    /** Читает файл через BitmapFactory — надёжнее, чем Drawable.createFromPath */
    fun loadBitmap(): Bitmap? {
        val f = file()
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            Log.d("WallpaperStore", "loadBitmap result=$bmp")
            bmp
        } catch (e: Exception) {
            Log.e("WallpaperStore", "loadBitmap failed", e)
            null
        }
    }

    fun getFile(): File? = if (hasWallpaper()) file() else null

    fun clear() {
        file().delete()
    }
}