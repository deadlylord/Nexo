package com.stree.nexo.media

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs

internal data class LyricsTrack(val title: String, val artist: String, val durationMs: Long)

internal data class SongLyrics(val timed: List<TimedLyric>, val plain: List<String>)

internal data class TimedLyric(val positionMs: Long, val text: String)

private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
private val offsetTag = Regex("""(?im)^\[offset:([+-]?\d+)\]""")
private val bracketSuffixRegex = Regex(
    """\s*[\(\[](?:feat\.?|ft\.?|featuring|with|official\s*(?:music\s*)?(?:video|audio|lyric(?:s)?|visualizer)?|lyrics?|remaster(?:ed)?(?:\s*\d{4})?|radio\s*edit|deluxe(?:\s*edition)?|bonus\s*track|explicit|clean|live(?:\s+at\b.*)?|acoustic|version|edit)[^\)\]]*[\)\]]""",
    RegexOption.IGNORE_CASE
)
private val dashSuffixRegex = Regex(
    """\s*[-–—]\s*(?:remaster(?:ed)?(?:\s*\d{4})?|radio\s*edit|single\s*version|album\s*version|live(?:\s+at\b.*)?|acoustic|explicit|clean|bonus\s*track|feat\.?.*|ft\.?.*)$""",
    RegexOption.IGNORE_CASE
)
private val multiSpaceRegex = Regex("""\s{2,}""")

private fun cleanTrackTitle(raw: String): String {
    var cleaned = raw.trim()
    cleaned = bracketSuffixRegex.replace(cleaned, "")
    cleaned = dashSuffixRegex.replace(cleaned, "")
    return multiSpaceRegex.replace(cleaned, " ").trim()
}

private fun cleanArtistName(raw: String): String {
    val trimmed = raw.trim()
    val primary = trimmed.split(",", "&", " feat.", " ft.", ignoreCase = true)
        .firstOrNull()?.trim().orEmpty()
    return primary.ifBlank { trimmed }
}

internal suspend fun fetchLyrics(track: LyricsTrack): SongLyrics? = withContext(Dispatchers.IO) {
    val exact = queryLrclib(track.title.trim(), track.artist.trim(), track.durationMs)
    if (exact != null) return@withContext exact

    val cleanedTitle = cleanTrackTitle(track.title)
    val cleanedArtist = cleanArtistName(track.artist)
    if (cleanedTitle.isNotBlank() &&
        (!cleanedTitle.equals(track.title.trim(), ignoreCase = true) ||
            !cleanedArtist.equals(track.artist.trim(), ignoreCase = true))
    ) {
        return@withContext queryLrclib(cleanedTitle, cleanedArtist, track.durationMs)
    }
    null
}

private fun queryLrclib(title: String, artist: String, durationMs: Long): SongLyrics? {
    val seconds = (durationMs + 500L) / 1_000L
    val endpoint = Uri.parse("https://lrclib.net/api/search")
        .buildUpon()
        .appendQueryParameter("track_name", title)
        .appendQueryParameter("artist_name", artist)
        .build()
    val connection = URL(endpoint.toString()).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Nexo (https://github.com/deadlylord/Nexo)")
        return when (connection.responseCode) {
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
                val candidates = (0 until results.length()).asSequence()
                    .mapNotNull(results::optJSONObject)
                    .filter { hasUsableLyrics(it) }
                    .toList()
                if (candidates.isEmpty()) return null

                val exactMatches = candidates.filter {
                    it.optString("trackName").trim().equals(title, ignoreCase = true) &&
                        it.optString("artistName").trim().equals(artist, ignoreCase = true)
                }
                val strictMatch = exactMatches
                    .filter { seconds <= 0 || abs(it.optDouble("duration") - seconds) <= 3.0 }
                    .sortedWith(
                        compareByDescending<JSONObject> { hasSyncedLyrics(it) }
                            .thenBy { if (seconds > 0) abs(it.optDouble("duration") - seconds) else 0.0 }
                    )
                    .firstOrNull()

                val relaxedExact = strictMatch ?: exactMatches
                    .sortedWith(
                        compareByDescending<JSONObject> { hasSyncedLyrics(it) }
                            .thenBy { if (seconds > 0) abs(it.optDouble("duration") - seconds) else 0.0 }
                    )
                    .firstOrNull()

                val cleanQueryTitle = cleanTrackTitle(title)
                val cleanQueryArtist = cleanArtistName(artist)
                val fuzzyMatch = relaxedExact ?: candidates
                    .filter { obj ->
                        val candTitle = cleanTrackTitle(obj.optString("trackName"))
                        val candArtist = cleanArtistName(obj.optString("artistName"))
                        val titleOk = candTitle.equals(cleanQueryTitle, ignoreCase = true) ||
                            candTitle.contains(cleanQueryTitle, ignoreCase = true) ||
                            cleanQueryTitle.contains(candTitle, ignoreCase = true)
                        val artistOk = candArtist.equals(cleanQueryArtist, ignoreCase = true) ||
                            obj.optString("artistName").contains(cleanQueryArtist, ignoreCase = true) ||
                            cleanQueryArtist.contains(candArtist, ignoreCase = true)
                        titleOk && artistOk
                    }
                    .sortedWith(
                        compareByDescending<JSONObject> { hasSyncedLyrics(it) }
                            .thenBy { if (seconds > 0) abs(it.optDouble("duration") - seconds) else 0.0 }
                    )
                    .firstOrNull()

                fuzzyMatch?.let(::parseSongLyrics)
            }
            else -> throw IOException("Error de LRCLIB")
        }
    } finally {
        connection.disconnect()
    }
}

private fun hasSyncedLyrics(obj: JSONObject): Boolean =
    !obj.isNull("syncedLyrics") && obj.optString("syncedLyrics").isNotBlank()

private fun hasUsableLyrics(obj: JSONObject): Boolean =
    hasSyncedLyrics(obj) || (!obj.isNull("plainLyrics") && obj.optString("plainLyrics").isNotBlank())

private fun parseSongLyrics(it: JSONObject): SongLyrics? {
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
    return SongLyrics(timed, plain).takeIf { lyrics ->
        lyrics.timed.any { line -> line.text.isNotBlank() } || lyrics.plain.isNotEmpty()
    }
}

