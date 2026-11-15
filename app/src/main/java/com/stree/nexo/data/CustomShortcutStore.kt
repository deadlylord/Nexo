package com.stree.nexo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CustomShortcutStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexo_custom", Context.MODE_PRIVATE)

    fun all(): List<CustomShortcut> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val id = obj.optString("id")
                    val label = obj.optString("label")
                    val target = obj.optString("target")
                    if (id.isNotBlank() && label.isNotBlank() && target.isNotBlank()) {
                        add(CustomShortcut(id, label, target))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(label: String, target: String): CustomShortcut {
        val shortcut = CustomShortcut(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            target = normalizeTarget(target)
        )
        save(all() + shortcut)
        return shortcut
    }

    fun delete(id: String) {
        save(all().filterNot { it.id == id })
    }

    private fun save(shortcuts: List<CustomShortcut>) {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            array.put(
                JSONObject().apply {
                    put("id", shortcut.id)
                    put("label", shortcut.label)
                    put("target", shortcut.target)
                }
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun normalizeTarget(target: String): String {
        val trimmed = target.trim()
        return if ("://" in trimmed || trimmed.startsWith("tel:") ||
            trimmed.startsWith("mailto:") || trimmed.startsWith("sms:")
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
