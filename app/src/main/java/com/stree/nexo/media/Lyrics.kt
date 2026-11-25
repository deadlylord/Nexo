package com.stree.nexo.media

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class LyricsTrack(val title: String, val artist: String, val durationMs: Long)

internal data class SongLyrics(val timed: List<TimedLyric>, val plain: List<String>)

internal data class TimedLyric(val positionMs: Long, val text: String)

private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
private val offsetTag = Regex("""(?im)^\[offset:([+-]?\d+)\]""")

internal suspend fun fetchLyrics(track: LyricsTrack): SongLyrics? = withContext(Dispatchers.IO) {
    val seconds = (track.durationMs + 500L) / 1_000L
    val endpoint = Uri.parse("https://lrclib.net/api/search")
        .buildUpon()
        .appendQueryParameter("track_name", track.title)
        .appendQueryParameter("artist_name", track.artist)
        .build()
    val connection = URL(endpoint.toString()).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Nexo (https://github.com/deadlylord/Nexo)")
        when (connection.responseCode) {
            404 -> null
            200 -> {
                val response = connection.inputStream.bufferedReader().use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count == -1) break
                        if (result.length + count > 512_000) throw IOException("Respuesta demasiado grande")
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
                val results = JSONArray(response)
                val song = (0 until results.length()).asSequence()
                    .mapNotNull(results::optJSONObject)
                    .filter {
                        it.optString("trackName").trim().equals(track.title, ignoreCase = true) &&
                            it.optString("artistName").trim().equals(track.artist, ignoreCase = true) &&
                            (seconds <= 0 || kotlin.math.abs(it.optDouble("duration") - seconds) <= 3)
                    }
                    .minByOrNull {
                        if (seconds > 0) kotlin.math.abs(it.optDouble("duration") - seconds) else 0.0
                    }
                song?.let {
                    val lrc = if (it.isNull("syncedLyrics")) "" else it.optString("syncedLyrics")
                    val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull()
                        ?.coerceIn(-60_000L, 60_000L) ?: 0L
                    val timed = lrc.lineSequence().flatMap { line ->
                        val marks = timestamp.findAll(line).toList()
                        val lyric = line.substring((marks.lastOrNull()?.range?.last ?: -1) + 1).trim()
                        marks.asSequence().map { mark ->
                            val (minutes, secs, fraction) = mark.destructured
                            val time = minutes.toLong() * 60_000L + secs.toLong() * 1_000L +
                                fraction.padEnd(3, '0').toLong()
                            TimedLyric((time - offset).coerceAtLeast(0L), lyric)
                        }
                    }.sortedBy(TimedLyric::positionMs).toList()
                    val plain = if (it.isNull("plainLyrics")) emptyList() else {
                        it.optString("plainLyrics").lineSequence().map(String::trim)
                            .filter(String::isNotEmpty).toList()
                    }
                    SongLyrics(timed, plain).takeIf { lyrics ->
                        lyrics.timed.any { line -> line.text.isNotBlank() } || lyrics.plain.isNotEmpty()
                    }
                }
            }
            else -> throw IOException("Error de LRCLIB")
        }
    } finally {
        connection.disconnect()
    }
}
