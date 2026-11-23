package com.stree.nexo.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import org.json.JSONObject

data class WidgetBinding(val id: Int, val width: Int = 280, val height: Int = 220)

object WidgetStore {
    private fun prefs(context: Context) = context.getSharedPreferences("nexo_widgets", Context.MODE_PRIVATE)
    private var sharedHost: AppWidgetHost? = null
    private var listeners = 0

    @Synchronized
    fun host(context: Context): AppWidgetHost =
        sharedHost ?: NexoAppWidgetHost(context.applicationContext, 0x4E45584F)
            .also { sharedHost = it }

    @Synchronized
    fun startListening(context: Context) {
        if (listeners == 0) runCatching { host(context).startListening() }
        listeners++
    }

    @Synchronized
    fun stopListening(context: Context) {
        if (listeners <= 0) return
        listeners--
        if (listeners == 0) runCatching { host(context).stopListening() }
    }

    private const val PANEL_PREFIX = "panel-widget:"
    private const val SPOTLIGHT_PREFIX = "spotlight-widget:"

    fun isEmbeddedPanelWidget(item: String): Boolean =
        item.startsWith(PANEL_PREFIX) || item.startsWith(SPOTLIGHT_PREFIX)

    fun panelItems(context: Context): List<String> = prefs(context).all.keys
        .filter { it.startsWith(PANEL_PREFIX) }.sorted()

    fun spotlightItems(context: Context): List<String> = prefs(context).all.keys
        .filter { it.startsWith(SPOTLIGHT_PREFIX) }.sorted()

    fun get(context: Context, item: String): WidgetBinding? = runCatching {
        val json = JSONObject(prefs(context).getString(item, null) ?: return null)
        WidgetBinding(json.getInt("id"), json.optInt("width", 280), json.optInt("height", 220))
    }.getOrNull()

    fun put(context: Context, item: String, binding: WidgetBinding) {
        prefs(context).edit().putString(
            item,
            JSONObject()
                .put("id", binding.id)
                .put("width", binding.width)
                .put("height", binding.height)
                .toString()
        ).apply()
    }

    fun remove(context: Context, item: String) {
        get(context, item)?.let { runCatching { host(context).deleteAppWidgetId(it.id) } }
        prefs(context).edit().remove(item).apply()
    }

    fun buildHostOptions(widthDp: Int, heightDp: Int): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    }

    fun applyWidgetSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        val options = buildHostOptions(widthDp, heightDp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(options, listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
        }
    }
}
