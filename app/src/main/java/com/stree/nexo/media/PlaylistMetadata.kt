package com.stree.nexo.media

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.stree.nexo.data.PinnedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

object PlaylistMetadata {

    private const val TIMEOUT_MS = 5_000
    private const val MAX_HTML_BYTES = 64 * 1024
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_ART_PX = 160

    private val artCache = LruCache<String, Bitmap>(24)

    private val ogTitleRegex1 = Regex(
        """<meta[^>]+(?:property|name)\s*=\s*["']og:title["'][^>]+content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val ogTitleRegex2 = Regex(
        """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']og:title["']""",
        RegexOption.IGNORE_CASE
    )
    private val ogImageRegex1 = Regex(
        """<meta[^>]+(?:property|name)\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val ogImageRegex2 = Regex(
        """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']og:image["']""",
        RegexOption.IGNORE_CASE
    )
    private val htmlTitleRegex = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("spotify:", ignoreCase = true)) {
            val parts = trimmed.split(":")
            if (parts.size >= 3) {
                val type = parts[1].lowercase()
                val id = parts[2].substringBefore("?")
                if (type in setOf("playlist", "album", "artist", "track", "show") && id.isNotBlank()) {
                    return "https://open.spotify.com/$type/$id"
                }
            }
        }
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    suspend fun fetchPlaylistMetadata(
        context: Context,
        rawUrl: String,
        fallbackTitle: String? = null
    ): PinnedPlaylist = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(rawUrl)
        val id = UUID.randomUUID().toString().take(12)
        var fetchedTitle: String? = null
        var imageUrl: String? = null

        val isSpotify = normalizedUrl.contains("open.spotify.com/", ignoreCase = true)
        if (isSpotify) {
            runCatching {
                val encoded = URLEncoder.encode(normalizedUrl, "UTF-8")
                val body = httpGetText("https://open.spotify.com/oembed?url=$encoded", MAX_HTML_BYTES)
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    fetchedTitle = json.optString("title").trim().takeIf { it.isNotEmpty() }
                    imageUrl = json.optString("thumbnail_url").trim().takeIf { it.isNotEmpty() }
                }
            }
        }

        if (fetchedTitle == null || imageUrl == null) {
            runCatching {
                val html = httpGetText(normalizedUrl, MAX_HTML_BYTES)
                if (!html.isNullOrBlank()) {
                    if (fetchedTitle == null) {
                        val rawTitle = ogTitleRegex1.find(html)?.groupValues?.getOrNull(1)
                            ?: ogTitleRegex2.find(html)?.groupValues?.getOrNull(1)
                            ?: htmlTitleRegex.find(html)?.groupValues?.getOrNull(1)
                        fetchedTitle = rawTitle?.let(::decodeHtmlEntities)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    if (imageUrl == null) {
                        val rawImg = ogImageRegex1.find(html)?.groupValues?.getOrNull(1)
                            ?: ogImageRegex2.find(html)?.groupValues?.getOrNull(1)
                        imageUrl = rawImg?.let(::decodeHtmlEntities)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }
            }
        }

        val artPath = imageUrl?.let { imgUrl ->
            runCatching { downloadAndSaveArt(context, id, imgUrl) }.getOrNull()
        }

        val finalTitle = fallbackTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: fetchedTitle
            ?: Uri.parse(normalizedUrl).host?.removePrefix("www.")?.ifBlank { "Playlist" }
            ?: "Playlist"

        PinnedPlaylist(
            id = id,
            title = finalTitle.take(40),
            url = normalizedUrl,
            artPath = artPath
        )
    }

    fun getCachedPlaylistArt(artPath: String?): Bitmap? {
        if (artPath.isNullOrBlank()) return null
        return artCache.get(artPath)?.takeIf { !it.isRecycled }
    }

    fun loadPlaylistArt(artPath: String?): Bitmap? {
        if (artPath.isNullOrBlank()) return null
        artCache.get(artPath)?.takeIf { !it.isRecycled }?.let { return it }
        val file = File(artPath)
        if (!file.exists()) return null
        return runCatching {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) artCache.put(artPath, bmp)
            bmp
        }.getOrNull()
    }

    suspend fun preloadPlaylistArt(artPaths: List<String?>) = withContext(Dispatchers.IO) {
        artPaths.forEach { path ->
            if (!path.isNullOrBlank() && artCache.get(path) == null) {
                loadPlaylistArt(path)
            }
        }
    }

    fun deletePlaylistArt(artPath: String?) {
        if (artPath.isNullOrBlank()) return
        artCache.remove(artPath)
        runCatching { File(artPath).delete() }
    }

    fun openPlaylist(context: Context, url: String): Boolean {
        val normalized = normalizeUrl(url)
        if (normalized.isBlank()) return false
        val uri = Uri.parse(normalized)
        val isSpotify = normalized.contains("open.spotify.com/", ignoreCase = true)

        if (isSpotify) {
            val spotifyIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.spotify.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startActivityCompat(context, spotifyIntent)) return true
        }

        val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivityCompat(context, genericIntent)
    }

    private fun startActivityCompat(context: Context, intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sent = runCatching {
                @Suppress("DEPRECATION")
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    ).toBundle()
                context.startActivity(intent, opts)
                true
            }.getOrDefault(false)
            if (sent) return true
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun httpGetText(urlString: String, maxBytes: Int): String? {
        val conn = (URL(urlString).openConnection() as? HttpURLConnection) ?: return null
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Nexo/1.0")
            conn.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    total += read
                }
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadAndSaveArt(context: Context, id: String, imageUrl: String): String? {
        val conn = (URL(imageUrl).openConnection() as? HttpURLConnection) ?: return null
        val bytes = try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Nexo/1.0")
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (total < MAX_IMAGE_BYTES) {
                    val read = input.read(buf, 0, minOf(buf.size, MAX_IMAGE_BYTES - total))
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    total += read
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_ART_PX * 2 || bounds.outHeight / sample > MAX_ART_PX * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val scaled = if (decoded.width > MAX_ART_PX || decoded.height > MAX_ART_PX) {
            val scale = minOf(MAX_ART_PX.toFloat() / decoded.width, MAX_ART_PX.toFloat() / decoded.height)
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val dir = File(context.filesDir, "playlist_art").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        artCache.put(file.absolutePath, scaled)
        return file.absolutePath
    }

    private fun decodeHtmlEntities(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
