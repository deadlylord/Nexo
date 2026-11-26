package com.stree.nexo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE = "nexo_prefs"

    var themeRevision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isPanelEnabled(context: Context): Boolean =
        prefs(context).getBoolean("panel_enabled", false)

    fun setPanelEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("panel_enabled", enabled).apply()
    }

    fun panelSide(context: Context): PanelSide =
        if (prefs(context).getString("panel_side", "end") == "start") {
            PanelSide.START
        } else {
            PanelSide.END
        }

    fun setPanelSide(context: Context, side: PanelSide) {
        prefs(context).edit()
            .putString("panel_side", if (side == PanelSide.START) "start" else "end")
            .apply()
    }

    fun panelStyle(context: Context): PanelStyle = runCatching {
        PanelStyle.valueOf(prefs(context).getString("panel_style", PanelStyle.NEXO.name).orEmpty())
    }.getOrDefault(PanelStyle.NEXO)

    fun setPanelStyle(context: Context, style: PanelStyle) {
        prefs(context).edit().putString("panel_style", style.name).apply()
        themeRevision++
    }

    fun appDynamicColor(context: Context): Boolean =
        prefs(context).getBoolean("app_dynamic_color", false)

    fun setAppDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("app_dynamic_color", enabled).apply()
        themeRevision++
    }

    fun panelPageOrder(context: Context): List<PanelPage> {
        val saved = runCatching {
            val array = JSONArray(prefs(context).getString("panel_page_order", "[]"))
            (0 until array.length()).mapNotNull { index ->
                runCatching { PanelPage.valueOf(array.getString(index)) }.getOrNull()
            }.distinct()
        }.getOrDefault(emptyList())
        return saved + PanelPage.entries.filterNot { it in saved }
    }

    fun setPanelPageOrder(context: Context, pages: List<PanelPage>) {
        val order = pages.distinct() + PanelPage.entries.filterNot { it in pages }
        prefs(context).edit()
            .putString("panel_page_order", JSONArray(order.map { it.name }).toString()).apply()
    }

    fun hiddenPanelPages(context: Context): Set<String> =
        prefs(context).getStringSet("hidden_panel_pages", emptySet())?.toSet().orEmpty()

    fun visiblePanelPages(context: Context): List<PanelPage> =
        panelPageOrder(context).filterNot { it.name in hiddenPanelPages(context) }
            .ifEmpty { listOf(PanelPage.MAIN) }

    fun setPanelPageVisible(context: Context, page: PanelPage, visible: Boolean) {
        val hidden = hiddenPanelPages(context).toMutableSet()
        if (visible) hidden.remove(page.name) else {
            if (visiblePanelPages(context).size <= 1) return
            hidden.add(page.name)
        }
        prefs(context).edit().putStringSet("hidden_panel_pages", hidden).apply()
    }

    fun animatePanelIcons(context: Context): Boolean = prefs(context).getBoolean("animate_panel_icons", true)

    fun quickDrawerExpanded(context: Context): Boolean = prefs(context).getBoolean("quick_drawer_expanded", false)

    fun setQuickDrawerExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean("quick_drawer_expanded", expanded).apply()
    }

    fun setAnimatePanelIcons(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("animate_panel_icons", enabled).apply()
    }

    fun panelWidth(context: Context): Float =
        prefs(context).getFloat("panel_width", 0.58f).coerceIn(0.45f, 0.90f)

    fun setPanelWidth(context: Context, value: Float) {
        prefs(context).edit().putFloat("panel_width", value.coerceIn(0.45f, 0.90f)).apply()
        themeRevision++
    }

    fun panelInfiniteScroll(context: Context): Boolean =
        prefs(context).getBoolean("panel_infinite_scroll", true)

    fun setPanelInfiniteScroll(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("panel_infinite_scroll", enabled).apply()
    }

    fun panelHeight(context: Context): Float =
        prefs(context).getFloat("panel_height", 0.52f).coerceIn(0.30f, 0.85f)

    fun setPanelHeight(context: Context, value: Float) {
        prefs(context).edit().putFloat("panel_height", value.coerceIn(0.30f, 0.85f)).apply()
        themeRevision++
    }

    fun lastImeHeightDp(context: Context): Int =
        prefs(context).getInt("last_ime_height_dp", 0).coerceIn(0, 520)

    fun setLastImeHeightDp(context: Context, heightDp: Int) {
        prefs(context).edit().putInt("last_ime_height_dp", heightDp.coerceIn(0, 520)).apply()
    }

    fun savedStreamVolume(context: Context, stream: Int): Int =
        prefs(context).getInt("saved_stream_vol_$stream", -1)

    fun setSavedStreamVolume(context: Context, stream: Int, volume: Int) {
        prefs(context).edit().putInt("saved_stream_vol_$stream", volume).apply()
    }

    fun silentUntilMillis(context: Context): Long =
        prefs(context).getLong("silent_until_millis", 0L).coerceAtLeast(0L)

    fun setSilentUntilMillis(context: Context, untilMillis: Long) {
        prefs(context).edit().putLong("silent_until_millis", untilMillis.coerceAtLeast(0L)).apply()
    }

    fun panelIconScale(context: Context): Float =
        prefs(context).getFloat("panel_icon_scale", 1f).coerceIn(0.75f, 1.35f)

    fun panelLightBackground(context: Context): Boolean =
        prefs(context).getBoolean("panel_light_background", false)

    fun setPanelLightBackground(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("panel_light_background", enabled).apply()
        themeRevision++
    }

    fun activatorStyle(context: Context): ActivatorStyle = runCatching {
        ActivatorStyle.valueOf(prefs(context).getString("activator_style", ActivatorStyle.PILL.name).orEmpty())
    }.getOrDefault(ActivatorStyle.PILL)

    fun setActivatorStyle(context: Context, style: ActivatorStyle) {
        prefs(context).edit().putString("activator_style", style.name).apply()
    }

    fun activatorColor(context: Context): Int = prefs(context).getInt("activator_color", 0xFF8B80FF.toInt())
    fun setActivatorColor(context: Context, color: Int) {
        prefs(context).edit().putInt("activator_color", color).apply()
    }
    fun activatorOpacity(context: Context): Float = prefs(context).getFloat("activator_opacity", 0.92f).coerceIn(0.25f, 1f)
    fun setActivatorOpacity(context: Context, opacity: Float) {
        prefs(context).edit().putFloat("activator_opacity", opacity.coerceIn(0.25f, 1f)).apply()
    }

    fun activatorSyncTheme(context: Context): Boolean =
        prefs(context).getBoolean("activator_sync_theme", true)

    fun setActivatorSyncTheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("activator_sync_theme", enabled).apply()
        themeRevision++
    }

    fun edgePulseOnCopy(context: Context): Boolean =
        prefs(context).getBoolean("edge_pulse_copy", true)

    fun setEdgePulseOnCopy(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("edge_pulse_copy", enabled).apply()
    }

    fun edgePulseOnScreenshot(context: Context): Boolean =
        prefs(context).getBoolean("edge_pulse_screenshot", true)

    fun setEdgePulseOnScreenshot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("edge_pulse_screenshot", enabled).apply()
    }

    fun edgePulseOnNotification(context: Context): Boolean =
        prefs(context).getBoolean("edge_pulse_notification", true)

    fun setEdgePulseOnNotification(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("edge_pulse_notification", enabled).apply()
    }

    fun edgeNotificationDoubleTapPreview(context: Context): Boolean =
        prefs(context).getBoolean("edge_notification_double_tap_preview", true)

    fun setEdgeNotificationDoubleTapPreview(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("edge_notification_double_tap_preview", enabled).apply()
    }

    fun dynamicIslandEnabled(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_enabled", true)

    fun setDynamicIslandEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_enabled", enabled).apply()
    }

    fun dynamicIslandIdlePill(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_idle_pill", true)

    fun setDynamicIslandIdlePill(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_idle_pill", enabled).apply()
    }

    fun dynamicIslandHideBehindCamera(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_hide_behind_camera", true)

    fun setDynamicIslandHideBehindCamera(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_hide_behind_camera", enabled).apply()
    }

    fun dynamicIslandTopOffset(context: Context): Int =
        prefs(context).getInt("dynamic_island_top_offset", 8).coerceIn(-6, 64)

    fun setDynamicIslandTopOffset(context: Context, offsetDp: Int) {
        prefs(context).edit().putInt("dynamic_island_top_offset", offsetDp.coerceIn(-6, 64)).apply()
    }

    fun dynamicIslandHorizontalOffset(context: Context): Int =
        prefs(context).getInt("dynamic_island_x_offset", 0).coerceIn(-120, 120)

    fun setDynamicIslandHorizontalOffset(context: Context, offsetDp: Int) {
        prefs(context).edit().putInt("dynamic_island_x_offset", offsetDp.coerceIn(-120, 120)).apply()
    }

    fun dynamicIslandIdleWidth(context: Context): Int =
        prefs(context).getInt("dynamic_island_idle_width", 26).coerceIn(16, 180)

    fun setDynamicIslandIdleWidth(context: Context, widthDp: Int) {
        prefs(context).edit().putInt("dynamic_island_idle_width", widthDp.coerceIn(16, 180)).apply()
    }

    fun dynamicIslandIdleHeight(context: Context): Int =
        prefs(context).getInt("dynamic_island_idle_height", 24).coerceIn(16, 48)

    fun setDynamicIslandIdleHeight(context: Context, heightDp: Int) {
        prefs(context).edit().putInt("dynamic_island_idle_height", heightDp.coerceIn(16, 48)).apply()
    }

    fun dynamicIslandCompactHeight(context: Context): Int =
        prefs(context).getInt("dynamic_island_compact_height", 36).coerceIn(26, 52)

    fun setDynamicIslandCompactHeight(context: Context, heightDp: Int) {
        prefs(context).edit().putInt("dynamic_island_compact_height", heightDp.coerceIn(26, 52)).apply()
    }

    fun dynamicIslandExpandedWidth(context: Context): Int =
        prefs(context).getInt("dynamic_island_expanded_width", 336).coerceIn(250, 390)

    fun setDynamicIslandExpandedWidth(context: Context, widthDp: Int) {
        prefs(context).edit().putInt("dynamic_island_expanded_width", widthDp.coerceIn(250, 390)).apply()
    }

    fun dynamicIslandExpandedHeight(context: Context): Int =
        prefs(context).getInt("dynamic_island_expanded_height", 138).coerceIn(112, 210)

    fun setDynamicIslandExpandedHeight(context: Context, heightDp: Int) {
        prefs(context).edit().putInt("dynamic_island_expanded_height", heightDp.coerceIn(112, 210)).apply()
    }

    fun dynamicIslandCameraGap(context: Context): Int =
        prefs(context).getInt("dynamic_island_camera_gap", 32).coerceIn(0, 72)

    fun setDynamicIslandCameraGap(context: Context, gapDp: Int) {
        prefs(context).edit().putInt("dynamic_island_camera_gap", gapDp.coerceIn(0, 72)).apply()
    }

    fun dynamicIslandHideLandscape(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_hide_landscape", true)

    fun setDynamicIslandHideLandscape(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_hide_landscape", enabled).apply()
    }

    fun dynamicIslandMedia(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_media", true)

    fun dynamicIslandHideOnLock(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_hide_on_lock", false)

    fun setDynamicIslandHideOnLock(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_hide_on_lock", enabled).apply()
    }

    fun dynamicIslandCompactFullscreen(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_compact_fullscreen", false)

    fun setDynamicIslandCompactFullscreen(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_compact_fullscreen", enabled).apply()
    }

    fun dynamicIslandOpacity(context: Context): Int =
        prefs(context).getInt("dynamic_island_opacity", 100).coerceIn(65, 100)

    fun setDynamicIslandOpacity(context: Context, percent: Int) {
        prefs(context).edit().putInt("dynamic_island_opacity", percent.coerceIn(65, 100)).apply()
    }

    fun dynamicIslandTextScale(context: Context): Int =
        prefs(context).getInt("dynamic_island_text_scale", 100).coerceIn(90, 115)

    fun setDynamicIslandTextScale(context: Context, percent: Int) {
        prefs(context).edit().putInt("dynamic_island_text_scale", percent.coerceIn(90, 115)).apply()
    }

    fun dynamicIslandMediaKeepPaused(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_media_keep_paused_v2", false)

    fun setDynamicIslandMediaKeepPaused(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_media_keep_paused_v2", enabled).apply()
    }

    fun setDynamicIslandMedia(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_media", enabled).apply()
    }

    fun dynamicIslandNotifications(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_notifications", true)

    fun setDynamicIslandNotifications(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_notifications", enabled).apply()
    }

    fun islandNotificationDurationMs(context: Context): Int =
        prefs(context).getInt("island_notification_duration_ms", 4_800).coerceIn(1_000, 10_000)

    fun setIslandNotificationDurationMs(context: Context, value: Int) {
        prefs(context).edit().putInt("island_notification_duration_ms", value.coerceIn(1_000, 10_000)).apply()
    }

    fun islandNotificationPill(context: Context): Boolean =
        prefs(context).getBoolean("island_notification_pill", false)

    fun setIslandNotificationPill(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("island_notification_pill", enabled).apply()
    }

    fun islandHideSilentNotifications(context: Context): Boolean =
        prefs(context).getBoolean("island_hide_silent_notifications", false)

    fun setIslandHideSilentNotifications(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("island_hide_silent_notifications", enabled).apply()
    }

    fun islandBlockedNotificationApps(context: Context): Set<String> =
        prefs(context).getStringSet("island_blocked_notification_apps", emptySet()).orEmpty()

    fun setIslandBlockedNotificationApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet("island_blocked_notification_apps", packages.toSet()).apply()
    }

    fun islandNotificationHistoryEnabled(context: Context): Boolean =
        prefs(context).getBoolean("island_notification_history", false)

    fun setIslandNotificationHistoryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("island_notification_history", enabled).apply()
    }

    fun acceptsIslandNotification(context: Context, pulse: NotificationPulse): Boolean =
        dynamicIslandNotifications(context) &&
            pulse.packageName !in islandBlockedNotificationApps(context) &&
            !(pulse.silent && islandHideSilentNotifications(context))

    fun mainSpotlightMode(context: Context): Boolean =
        prefs(context).getBoolean("main_spotlight_mode", true)

    fun setMainSpotlightMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("main_spotlight_mode", enabled).apply()
    }

    fun edgeGestureAction(context: Context, gesture: EdgeGesture): EdgeGestureAction = runCatching {
        val raw = prefs(context).getString("edge_gesture_${gesture.name}", gesture.defaultAction.name).orEmpty()
        EdgeGestureAction.valueOf(raw)
    }.getOrDefault(gesture.defaultAction)

    fun setEdgeGestureAction(context: Context, gesture: EdgeGesture, action: EdgeGestureAction) {
        prefs(context).edit().putString("edge_gesture_${gesture.name}", action.name).apply()
    }

    fun edgeGestureApp(context: Context, gesture: EdgeGesture): String? =
        prefs(context).getString("edge_gesture_app_${gesture.name}", null)?.takeIf { it.isNotBlank() }

    fun setEdgeGestureApp(context: Context, gesture: EdgeGesture, packageName: String?) {
        prefs(context).edit().putString("edge_gesture_app_${gesture.name}", packageName).apply()
    }

    fun setPanelIconScale(context: Context, value: Float) {
        prefs(context).edit().putFloat("panel_icon_scale", value.coerceIn(0.75f, 1.35f)).apply()
    }

    fun panelColumns(context: Context): Int =
        prefs(context).getInt("panel_columns", 5).coerceIn(3, 6)

    fun setPanelColumns(context: Context, value: Int) {
        prefs(context).edit().putInt("panel_columns", value.coerceIn(3, 6)).apply()
    }

    fun panelItemSpacing(context: Context): Int =
        prefs(context).getInt("panel_item_spacing", 0).coerceIn(0, 12)

    fun setPanelItemSpacing(context: Context, value: Int) {
        prefs(context).edit().putInt("panel_item_spacing", value.coerceIn(0, 12)).apply()
    }

    fun panelShowLabels(context: Context): Boolean =
        prefs(context).getBoolean("panel_show_labels", false)

    fun setPanelShowLabels(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("panel_show_labels", value).apply()
    }

    fun panelYFraction(context: Context): Float =
        prefs(context).getFloat("panel_y", 0f)

    fun setPanelYFraction(context: Context, value: Float) {
        prefs(context).edit().putFloat("panel_y", value.coerceIn(-1f, 1f)).apply()
    }

    fun contactsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("contacts_enabled", false)

    fun activationWidth(context: Context): Int =
        prefs(context).getInt("activation_width", 24).coerceIn(12, 48)

    fun guardBackGesture(context: Context): Boolean =
        prefs(context).getBoolean("guard_back_gesture", true)

    fun setGuardBackGesture(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("guard_back_gesture", enabled).apply()
    }

    fun activationHeight(context: Context): Int =
        prefs(context).getInt("activation_height", 196).coerceIn(48, 200)

    fun activationInset(context: Context): Int =
        prefs(context).getInt("activation_inset", 24).coerceIn(0, 64)

    fun indicatorWidth(context: Context): Int =
        prefs(context).getInt("indicator_width", 6).coerceIn(3, 24)

    fun indicatorHeight(context: Context): Int =
        prefs(context).getInt("indicator_height", 64).coerceIn(16, 120)

    fun showActivationArea(context: Context): Boolean =
        prefs(context).getBoolean("show_activation_area", false)

    fun setIndicator(context: Context, width: Int, height: Int, showArea: Boolean) {
        prefs(context).edit()
            .putInt("indicator_width", width.coerceIn(3, 24))
            .putInt("indicator_height", height.coerceIn(16, 120))
            .putBoolean("show_activation_area", showArea)
            .apply()
    }

    fun setActivationInset(context: Context, value: Int) {
        prefs(context).edit().putInt("activation_inset", value.coerceIn(0, 64)).apply()
    }

    fun setActivationSize(context: Context, width: Int, height: Int) {
        prefs(context).edit()
            .putInt("activation_width", width.coerceIn(12, 48))
            .putInt("activation_height", height.coerceIn(48, 200))
            .apply()
    }

    fun setContactsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("contacts_enabled", enabled).apply()
    }

    fun hapticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("haptics_enabled", true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun pinnedIds(context: Context): Set<String> =
        prefs(context).getStringSet("pinned_ids", emptySet())?.toSet() ?: emptySet()

    fun pinnedSlots(context: Context): Map<String, Int> {
        val order = pinnedOrder(context)
        val saved = runCatching { JSONObject(prefs(context).getString("pinned_slots", "{}").orEmpty()) }
            .getOrDefault(JSONObject())
        val slots = linkedMapOf<String, Int>()
        order.forEach { id ->
            val position = saved.optInt(id, -1)
            if (position in 0..199 && position !in slots.values) slots[id] = position
        }
        order.filterNot { it in slots }.forEach { id ->
            slots[id] = (0..maxOf(199, order.size)).first { it !in slots.values }
        }
        return slots
    }

    fun setPinnedSlot(context: Context, id: String, slot: Int) {
        if (id !in pinnedIds(context)) return
        val slots = pinnedSlots(context).toMutableMap()
        val position = slot.coerceIn(0, 199)
        val old = slots[id] ?: position
        slots.entries.firstOrNull { it.value == position && it.key != id }?.key?.let { slots[it] = old }
        slots[id] = position
        prefs(context).edit()
            .putString("pinned_slots", JSONObject(slots as Map<*, *>).toString())
            .putString("pinned_order", JSONArray(slots.entries.sortedBy { it.value }.map { it.key }).toString())
            .apply()
    }

    fun pinnedOrder(context: Context): List<String> {
        val pinned = pinnedIds(context)
        val saved = runCatching {
            val array = JSONArray(prefs(context).getString("pinned_order", "[]"))
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
        return saved.filter { it in pinned }.distinct() + pinned.filterNot { it in saved }
    }

    fun setPinnedOrder(context: Context, ids: List<String>) {
        val pinned = pinnedIds(context)
        val ordered = ids.filter { it in pinned }.distinct() + pinned.filterNot { it in ids }
        prefs(context).edit().putString("pinned_order", JSONArray(ordered).toString()).apply()
    }

    fun contactWhatsAppLink(context: Context, id: String): String? =
        prefs(context).getString("whatsapp_link:$id", null)

    fun setContactWhatsAppLink(context: Context, id: String, link: String) {
        prefs(context).edit().putString("whatsapp_link:$id", link).apply()
    }

    fun setPinnedIds(context: Context, ids: Set<String>) {
        val oldOrder = pinnedOrder(context)
        val newOrder = oldOrder.filter { it in ids } + ids.filterNot { it in oldOrder }
        val slots = pinnedSlots(context).filterKeys { it in ids }.toMutableMap()
        prefs(context).edit()
            .putStringSet("pinned_ids", ids.toSet())
            .putString("pinned_order", JSONArray(newOrder).toString())
            .putString("pinned_slots", JSONObject(slots as Map<*, *>).toString())
            .apply()
    }

    fun autoOpenOnUnlock(context: Context): Boolean =
        prefs(context).getBoolean("auto_open_on_unlock", false)

    fun setAutoOpenOnUnlock(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("auto_open_on_unlock", enabled).apply()
    }

    fun autoOpenOnUnlockSpotlight(context: Context): Boolean =
        prefs(context).getBoolean("auto_open_on_unlock_spotlight", false)

    fun setAutoOpenOnUnlockSpotlight(context: Context, spotlight: Boolean) {
        prefs(context).edit().putBoolean("auto_open_on_unlock_spotlight", spotlight).apply()
    }

    fun floatingBubbleEnabled(context: Context): Boolean =
        prefs(context).getBoolean("floating_bubble_enabled", true)

    fun setFloatingBubbleEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("floating_bubble_enabled", enabled).apply()
    }

    fun spotlightAutoOpen(context: Context): Boolean =
        prefs(context).getBoolean("spotlight_auto_open", true)

    fun setSpotlightAutoOpen(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("spotlight_auto_open", enabled).apply()
    }

    fun dynamicIslandShowPlaylists(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_island_show_playlists", true)

    fun setDynamicIslandShowPlaylists(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("dynamic_island_show_playlists", enabled).apply()
    }

    fun pinnedPlaylists(context: Context): List<PinnedPlaylist> = runCatching {
        val raw = prefs(context).getString("pinned_playlists", "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val title = obj.optString("title").trim()
                val url = obj.optString("url").trim()
                val artPath = obj.optString("artPath").trim().takeIf { it.isNotEmpty() }
                if (id.isNotEmpty() && url.isNotEmpty()) {
                    add(PinnedPlaylist(id = id, title = title.ifBlank { "Playlist" }, url = url, artPath = artPath))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun savePinnedPlaylists(context: Context, playlists: List<PinnedPlaylist>) {
        val array = JSONArray()
        playlists.take(16).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    if (!item.artPath.isNullOrBlank()) put("artPath", item.artPath)
                }
            )
        }
        prefs(context).edit().putString("pinned_playlists", array.toString()).apply()
    }

    fun addPinnedPlaylist(context: Context, playlist: PinnedPlaylist): List<PinnedPlaylist> {
        val current = pinnedPlaylists(context).filterNot { it.id == playlist.id || it.url.equals(playlist.url, ignoreCase = true) }
        val updated = (current + playlist).takeLast(16)
        savePinnedPlaylists(context, updated)
        return updated
    }

    fun removePinnedPlaylist(context: Context, id: String): List<PinnedPlaylist> {
        val updated = pinnedPlaylists(context).filterNot { it.id == id }
        savePinnedPlaylists(context, updated)
        return updated
    }
}

data class PinnedPlaylist(
    val id: String,
    val title: String,
    val url: String,
    val artPath: String? = null
)

