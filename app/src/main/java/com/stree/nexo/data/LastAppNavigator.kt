package com.stree.nexo.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class PreviousAppTarget(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val launchIntent: Intent
)

object LastAppNavigator {
    /** Busca la app anterior junto con su icono y nombre para mostrar vista previa. */
    fun previousAppInfo(context: Context): PreviousAppTarget? {
        if (!UsageStore(context).hasUsageAccess()) return null
        val now = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(now - 7L * 24 * 60 * 60 * 1000, now) ?: return null
        val event = UsageEvents.Event()
        val latest = mutableMapOf<String, Long>()
        var current: String? = null
        val resumed = if (android.os.Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else 1
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != resumed || event.packageName == context.packageName) continue
            val pkg = event.packageName ?: continue
            latest[pkg] = event.timeStamp
            current = pkg
        }
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        val pm = context.packageManager
        return latest.entries.sortedByDescending { it.value }.firstNotNullOfOrNull { (pkg, _) ->
            if (pkg == current || pkg == home || pkg == context.packageName || pkg == "com.android.systemui") null
            else {
                val intent = pm.getLaunchIntentForPackage(pkg)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    ?: return@firstNotNullOfOrNull null
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() } ?: pkg
                val icon = info?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
                PreviousAppTarget(pkg, label, icon, intent)
            }
        }
    }

    /** Busca el paquete anterior al actual; no simula Atrás ni usa Accesibilidad. */
    fun previousApp(context: Context): Intent? = previousAppInfo(context)?.launchIntent
}
