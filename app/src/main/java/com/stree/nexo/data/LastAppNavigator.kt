package com.stree.nexo.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent

object LastAppNavigator {
    /** Busca el paquete anterior al actual; no simula Atrás ni usa Accesibilidad. */
    fun previousApp(context: Context): Intent? {
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
        return latest.entries.sortedByDescending { it.value }.firstNotNullOfOrNull { (pkg, _) ->
            if (pkg == current || pkg == home || pkg == context.packageName || pkg == "com.android.systemui") null
            else context.packageManager.getLaunchIntentForPackage(pkg)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
}
