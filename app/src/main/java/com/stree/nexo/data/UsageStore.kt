package com.stree.nexo.data

import android.content.Context
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Process
import java.util.Calendar

class UsageStore(private val context: Context) {
    companion object { private val importLock = Any() }
    private val prefs = context.getSharedPreferences("nexo_usage", Context.MODE_PRIVATE)
    @Volatile private var externalEnabled = hasUsageAccess()

    fun record(id: String) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val editor = prefs.edit()
        if (id.startsWith("app:") && externalEnabled) {
            // Actualizamos al instante para que Recientes/Frecuentes cambien de inmediato;
            // importDeviceUsage() ignora eventos <= previous (ventana de 2 s), evitando duplicados.
            editor
                .putInt("external_count_$id", prefs.getInt("external_count_$id", 0) + 1)
                .putLong("external_time_$id", now)
                .putInt("external_hour_${hour}_$id", prefs.getInt("external_hour_${hour}_$id", 0) + 1)
        } else {
            val count = prefs.getInt("count_$id", 0) + 1
            editor
                .putInt("count_$id", count)
                .putLong("time_$id", now)
                .putInt("hour_${hour}_$id", prefs.getInt("hour_${hour}_$id", 0) + 1)
        }
        editor.apply()
    }

    fun count(id: String): Int = if (externalEnabled && id.startsWith("app:")) {
        prefs.getInt("external_count_$id", 0)
    } else prefs.getInt("count_$id", 0)

    fun lastUsed(id: String): Long = maxOf(prefs.getLong("time_$id", 0L),
        if (externalEnabled) prefs.getLong("external_time_$id", 0L) else 0L)

    fun hasUsageAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    /**
     * Importa eventos del sistema. Cada 6 h reconstruye los contadores sobre una ventana
     * móvil de 7 días para que Frecuentes evolucione con el uso real y no quede congelado;
     * entre reconstrucciones importa incrementalmente desde la última marca.
     */
    fun importDeviceUsage() {
        synchronized(importLock) {
            externalEnabled = hasUsageAccess()
            if (!externalEnabled) return
            val now = System.currentTimeMillis()
            val windowMs = 7L * 24 * 60 * 60 * 1000
            val lastRebuild = prefs.getLong("usage_last_rebuild", 0L)
            val fullRebuild = now - lastRebuild > 6L * 60 * 60 * 1000
            val since = if (fullRebuild) {
                now - windowMs
            } else {
                prefs.getLong("usage_watermark", now - windowMs).coerceAtLeast(now - windowMs)
            }
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(since, now) ?: return
            val event = UsageEvents.Event()
            val counts = mutableMapOf<String, Int>()
            val hours = mutableMapOf<String, Int>()
            val times = mutableMapOf<String, Long>()
            var lastPackage: String? = null
            var lastTime = 0L
            val calendar = Calendar.getInstance()
            val foreground = if (android.os.Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else 1
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != foreground || event.timeStamp < since) continue
                val pkg = event.packageName ?: continue
                if (pkg == context.packageName) continue
                val id = "app:$pkg"
                val previous = times[id] ?: if (fullRebuild) 0L else prefs.getLong("external_time_$id", 0L)
                if (event.timeStamp <= previous || (pkg == lastPackage && event.timeStamp - lastTime < 2000)) continue
                lastPackage = pkg; lastTime = event.timeStamp
                times[id] = event.timeStamp
                counts[id] = (counts[id] ?: if (fullRebuild) 0 else prefs.getInt("external_count_$id", 0)) + 1
                calendar.timeInMillis = event.timeStamp
                val key = "external_hour_${calendar.get(Calendar.HOUR_OF_DAY)}_$id"
                hours[key] = (hours[key] ?: if (fullRebuild) 0 else prefs.getInt(key, 0)) + 1
            }
            val editor = prefs.edit().putLong("usage_watermark", now)
            if (fullRebuild) {
                prefs.all.keys.forEach { key ->
                    if (key.startsWith("external_count_") || key.startsWith("external_hour_")) {
                        editor.remove(key)
                    }
                }
                editor.putLong("usage_last_rebuild", now)
            }
            counts.forEach { (id, value) -> editor.putInt("external_count_$id", value) }
            times.forEach { (id, value) ->
                val existing = prefs.getLong("external_time_$id", 0L)
                editor.putLong("external_time_$id", maxOf(existing, value))
            }
            hours.forEach { (key, value) -> editor.putInt(key, value) }
            editor.apply()
        }
    }

    fun forCurrentHour(ids: Collection<String>, limit: Int): List<String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val external = externalEnabled
        fun hourCount(id: String, h: Int): Int = if (external && id.startsWith("app:")) {
            prefs.getInt("external_hour_${h}_$id", 0)
        } else prefs.getInt("hour_${h}_$id", 0)
        fun score(id: String) = hourCount(id, hour) * 3 +
            hourCount(id, (hour + 23) % 24) + hourCount(id, (hour + 1) % 24)
        return ids.filter { score(it) > 0 }.sortedWith(compareByDescending<String> { score(it) }
            .thenByDescending { lastUsed(it) }).take(limit)
    }

    fun recent(ids: Collection<String>, limit: Int): List<String> =
        ids.asSequence()
            .filter { lastUsed(it) > 0L }
            .sortedByDescending { lastUsed(it) }
            .take(limit)
            .toList()

    fun frequent(ids: Collection<String>, limit: Int): List<String> =
        ids.asSequence()
            .filter { count(it) > 0 }
            .sortedWith(
                compareByDescending<String> { count(it) }
                    .thenByDescending { lastUsed(it) }
            )
            .take(limit)
            .toList()
}
