package com.stree.nexo.widget

import android.appwidget.AppWidgetHost
import android.content.Context
import org.json.JSONObject

data class WidgetBinding(val id: Int, val width: Int = 280, val height: Int = 220)

object WidgetStore {
    private fun prefs(context: Context) = context.getSharedPreferences("nexo_widgets", Context.MODE_PRIVATE)
    private var sharedHost: AppWidgetHost? = null
    private var listeners = 0
    @Synchronized fun host(context: Context): AppWidgetHost = sharedHost ?: AppWidgetHost(context.applicationContext, 0x4E45584F)
        .also { sharedHost = it }

    @Synchronized fun startListening(context: Context) {
        if (listeners == 0) host(context).startListening()
        listeners++
    }

    @Synchronized fun stopListening(context: Context) {
        if (listeners <= 0) return
        listeners--
        if (listeners == 0) runCatching { host(context).stopListening() }
    }

    fun panelItems(context: Context): List<String> = prefs(context).all.keys
        .filter { it.startsWith("panel-widget:") }.sorted()

    fun get(context: Context, item: String): WidgetBinding? = runCatching {
        val json = JSONObject(prefs(context).getString(item, null) ?: return null)
        WidgetBinding(json.getInt("id"), json.optInt("width", 280), json.optInt("height", 220))
    }.getOrNull()

    fun put(context: Context, item: String, binding: WidgetBinding) {
        prefs(context).edit().putString(item, JSONObject().put("id", binding.id)
            .put("width", binding.width).put("height", binding.height).toString()).apply()
    }

    fun remove(context: Context, item: String) {
        get(context, item)?.let { runCatching { host(context).deleteAppWidgetId(it.id) } }
        prefs(context).edit().remove(item).apply()
    }
}
