package com.stree.nexo.data

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ClipEntry(
    val text: String,
    val timestamp: Long
)

data class ScreenshotEntry(
    val uri: Uri,
    val displayName: String,
    val timestamp: Long
)

data class NotificationPulse(
    val packageName: String,
    val appIcon: Bitmap?,
    val accentColor: Int?,
    val contentIntent: PendingIntent?,
    val timestamp: Long
)

/**
 * Estado ligero en memoria del portapapeles reciente, la última captura
 * detectada y la última notificación para la barra viva y la mini-tarjeta.
 */
object ClipboardHistoryStore {
    private const val MAX_ITEMS = 12

    private val _clips = MutableStateFlow<List<ClipEntry>>(emptyList())
    val clips = _clips.asStateFlow()

    private val _latestScreenshot = MutableStateFlow<ScreenshotEntry?>(null)
    val latestScreenshot = _latestScreenshot.asStateFlow()

    @Volatile
    var latestNotification: NotificationPulse? = null
        private set

    fun recordClip(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val current = _clips.value
        if (current.firstOrNull()?.text == clean) return false
        val updated = (listOf(ClipEntry(clean, System.currentTimeMillis())) +
            current.filterNot { it.text == clean }).take(MAX_ITEMS)
        _clips.value = updated
        return true
    }

    fun readSystemClipboard(context: Context): String? {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount <= 0) return null
        val text = runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        recordClip(text)
        return text
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { cm.setPrimaryClip(ClipData.newPlainText("Nexo", text)) }
        recordClip(text)
    }

    fun recordScreenshot(entry: ScreenshotEntry) {
        _latestScreenshot.value = entry
    }

    fun clearScreenshot(uri: Uri) {
        if (_latestScreenshot.value?.uri == uri) {
            _latestScreenshot.value = null
        }
    }

    fun queryLatestScreenshot(context: Context): ScreenshotEntry? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sort
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val pathIdx = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                var checked = 0
                while (cursor.moveToNext() && checked < 25) {
                    checked++
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val relPath = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else ""
                    val combined = "$relPath/$name".lowercase(Locale.ROOT)
                    if ("screenshot" in combined || "captura" in combined || "screen_shot" in combined) {
                        val id = cursor.getLong(idIdx)
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val addedMs = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else System.currentTimeMillis()
                        val entry = ScreenshotEntry(uri, name.ifBlank { "Captura" }, addedMs)
                        _latestScreenshot.value = entry
                        return@use entry
                    }
                }
                null
            }
        }.getOrNull()
    }

    fun recordNotification(
        context: Context,
        packageName: String,
        accentColor: Int?,
        contentIntent: PendingIntent?
    ): NotificationPulse {
        val iconBitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(72, 72)
        }.getOrNull()
        val pulse = NotificationPulse(
            packageName = packageName,
            appIcon = iconBitmap,
            accentColor = accentColor,
            contentIntent = contentIntent,
            timestamp = System.currentTimeMillis()
        )
        latestNotification = pulse
        return pulse
    }

    fun hasScreenshotPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            perm
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
