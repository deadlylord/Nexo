package com.stree.nexo.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WidgetBinding(
    val id: Int,
    val width: Int = 280,
    val height: Int = 220,
    val edgeToEdge: Boolean = false
)

data class WidgetTab(
    val id: String,
    val name: String,
    val icon: String = ""
)

object WidgetStore {
    private fun prefs(context: Context) = context.getSharedPreferences("nexo_widgets", Context.MODE_PRIVATE)
    private fun metaPrefs(context: Context) = context.getSharedPreferences("nexo_widget_meta", Context.MODE_PRIVATE)
    private var sharedHost: AppWidgetHost? = null
    private var listeners = 0

    private const val KEY_SPOTLIGHT_TABS = "spotlight_tabs"
    private const val KEY_SELECTED_TAB = "spotlight_selected_tab"
    private const val KEY_TAB_ITEMS_PREFIX = "spotlight_tab_items_"
    private const val KEY_PANEL_ORDER = "panel_widget_order"
    const val DEFAULT_TAB_ID = "default"

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

    fun panelItems(context: Context): List<String> {
        val existing = prefs(context).all.keys.filter { it.startsWith(PANEL_PREFIX) }.sorted()
        val existingSet = existing.toSet()
        val storedOrder = readStringList(metaPrefs(context).getString(KEY_PANEL_ORDER, null))
            .filter { it in existingSet }
            .distinct()
        val remaining = existing.filterNot { it in storedOrder }
        return storedOrder + remaining
    }

    fun movePanelWidget(context: Context, itemId: String, delta: Int) {
        val list = panelItems(context).toMutableList()
        val index = list.indexOf(itemId)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        metaPrefs(context).edit().putString(KEY_PANEL_ORDER, writeStringList(list)).apply()
    }

    fun spotlightItems(context: Context): List<String> = prefs(context).all.keys
        .filter { it.startsWith(SPOTLIGHT_PREFIX) }.sorted()

    fun spotlightTabs(context: Context): List<WidgetTab> {
        val raw = metaPrefs(context).getString(KEY_SPOTLIGHT_TABS, null)
        val parsed = runCatching {
            val arr = JSONArray(raw ?: return@runCatching emptyList())
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                WidgetTab(id = id, name = name, icon = obj.optString("icon", "").trim())
            }
        }.getOrDefault(emptyList())

        if (parsed.isNotEmpty()) return parsed

        val defaultTab = WidgetTab(id = DEFAULT_TAB_ID, name = "General", icon = "")
        saveSpotlightTabs(context, listOf(defaultTab))
        return listOf(defaultTab)
    }

    fun selectedSpotlightTabId(context: Context): String {
        val tabs = spotlightTabs(context)
        val saved = metaPrefs(context).getString(KEY_SELECTED_TAB, null)
        return tabs.firstOrNull { it.id == saved }?.id ?: tabs.first().id
    }

    fun setSelectedSpotlightTabId(context: Context, tabId: String) {
        metaPrefs(context).edit().putString(KEY_SELECTED_TAB, tabId).apply()
    }

    fun spotlightItemsForTab(context: Context, tabId: String): List<String> {
        val tabs = spotlightTabs(context)
        val allExisting = spotlightItems(context)
        val existingSet = allExisting.toSet()

        // Construimos la asignación real de todos los tabs para migrar huérfanos a la primera pestaña
        val assigned = mutableSetOf<String>()
        val perTab = linkedMapOf<String, MutableList<String>>()
        for (tab in tabs) {
            val ids = readStringList(metaPrefs(context).getString(tabItemsKey(tab.id), null))
                .filter { it in existingSet && assigned.add(it) }
                .toMutableList()
            perTab[tab.id] = ids
        }

        val unassigned = allExisting.filterNot { it in assigned }
        val firstTabId = tabs.first().id
        if (unassigned.isNotEmpty()) {
            val firstList = perTab.getOrPut(firstTabId) { mutableListOf() }
            firstList.addAll(unassigned)
            metaPrefs(context).edit()
                .putString(tabItemsKey(firstTabId), writeStringList(firstList))
                .apply()
        }

        return perTab[tabId]?.toList() ?: emptyList()
    }

    fun addSpotlightTab(context: Context, name: String, icon: String = ""): WidgetTab {
        val cleanName = name.trim().ifEmpty { "Pestaña" }
        val tab = WidgetTab(
            id = "tab_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            name = cleanName,
            icon = icon.trim()
        )
        val updated = spotlightTabs(context) + tab
        saveSpotlightTabs(context, updated)
        return tab
    }

    fun updateSpotlightTab(context: Context, tabId: String, name: String, icon: String = "") {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val updated = spotlightTabs(context).map {
            if (it.id == tabId) it.copy(name = cleanName, icon = icon.trim()) else it
        }
        saveSpotlightTabs(context, updated)
    }

    fun moveSpotlightTab(context: Context, tabId: String, delta: Int) {
        val tabs = spotlightTabs(context).toMutableList()
        val index = tabs.indexOfFirst { it.id == tabId }
        val target = index + delta
        if (index < 0 || target !in tabs.indices) return
        val moved = tabs.removeAt(index)
        tabs.add(target, moved)
        saveSpotlightTabs(context, tabs)
    }

    fun deleteSpotlightTab(context: Context, tabId: String): Boolean {
        val tabs = spotlightTabs(context)
        if (tabs.size <= 1) return false
        val remaining = tabs.filterNot { it.id == tabId }
        if (remaining.size == tabs.size) return false

        val orphanWidgets = spotlightItemsForTab(context, tabId)
        val fallbackTabId = remaining.first().id
        val fallbackItems = spotlightItemsForTab(context, fallbackTabId).toMutableList()
        for (id in orphanWidgets) {
            if (id !in fallbackItems) fallbackItems.add(id)
        }

        saveSpotlightTabs(context, remaining)
        metaPrefs(context).edit()
            .putString(tabItemsKey(fallbackTabId), writeStringList(fallbackItems))
            .remove(tabItemsKey(tabId))
            .apply()

        if (selectedSpotlightTabId(context) == tabId) {
            setSelectedSpotlightTabId(context, fallbackTabId)
        }
        return true
    }

    fun assignSpotlightWidgetToTab(context: Context, itemId: String, tabId: String) {
        val tabs = spotlightTabs(context)
        val targetId = tabs.firstOrNull { it.id == tabId }?.id ?: tabs.first().id
        val editor = metaPrefs(context).edit()
        for (tab in tabs) {
            val list = readStringList(metaPrefs(context).getString(tabItemsKey(tab.id), null))
                .filterNot { it == itemId }
                .toMutableList()
            if (tab.id == targetId) {
                list.add(itemId)
            }
            editor.putString(tabItemsKey(tab.id), writeStringList(list))
        }
        editor.apply()
    }

    fun moveSpotlightWidgetInTab(context: Context, tabId: String, itemId: String, delta: Int) {
        val list = spotlightItemsForTab(context, tabId).toMutableList()
        val index = list.indexOf(itemId)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        metaPrefs(context).edit().putString(tabItemsKey(tabId), writeStringList(list)).apply()
    }

    private fun saveSpotlightTabs(context: Context, tabs: List<WidgetTab>) {
        val arr = JSONArray()
        for (tab in tabs) {
            arr.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("name", tab.name)
                    .put("icon", tab.icon)
            )
        }
        metaPrefs(context).edit().putString(KEY_SPOTLIGHT_TABS, arr.toString()).apply()
    }

    private fun tabItemsKey(tabId: String) = "$KEY_TAB_ITEMS_PREFIX$tabId"

    private fun readStringList(raw: String?): List<String> = runCatching {
        val arr = JSONArray(raw ?: return@runCatching emptyList())
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotEmpty() } }
    }.getOrDefault(emptyList())

    private fun writeStringList(items: List<String>): String =
        JSONArray().apply { items.forEach { put(it) } }.toString()

    fun get(context: Context, item: String): WidgetBinding? = runCatching {
        val json = JSONObject(prefs(context).getString(item, null) ?: return null)
        WidgetBinding(
            id = json.getInt("id"),
            width = json.optInt("width", 280),
            height = json.optInt("height", 220),
            edgeToEdge = json.optBoolean("edgeToEdge", false)
        )
    }.getOrNull()

    fun put(context: Context, item: String, binding: WidgetBinding) {
        prefs(context).edit().putString(
            item,
            JSONObject()
                .put("id", binding.id)
                .put("width", binding.width)
                .put("height", binding.height)
                .put("edgeToEdge", binding.edgeToEdge)
                .toString()
        ).apply()
    }

    fun remove(context: Context, item: String) {
        get(context, item)?.let { runCatching { host(context).deleteAppWidgetId(it.id) } }
        prefs(context).edit().remove(item).apply()
        if (item.startsWith(SPOTLIGHT_PREFIX)) {
            val editor = metaPrefs(context).edit()
            for (tab in spotlightTabs(context)) {
                val list = readStringList(metaPrefs(context).getString(tabItemsKey(tab.id), null))
                    .filterNot { it == item }
                editor.putString(tabItemsKey(tab.id), writeStringList(list))
            }
            editor.apply()
        } else if (item.startsWith(PANEL_PREFIX)) {
            val list = readStringList(metaPrefs(context).getString(KEY_PANEL_ORDER, null))
                .filterNot { it == item }
            metaPrefs(context).edit().putString(KEY_PANEL_ORDER, writeStringList(list)).apply()
        }
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
