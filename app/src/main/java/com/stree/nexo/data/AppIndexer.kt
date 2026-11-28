package com.stree.nexo.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process

data class ShortcutLoadResult(
    val items: List<SearchItem>,
    val blocked: Boolean
)

class AppIndexer(private val context: Context) {

    fun loadApps(): List<SearchItem> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val seenPackages = HashSet<String>()

        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                if (!seenPackages.add(packageName)) return@mapNotNull null

                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@mapNotNull null

                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                val label = resolveInfo.loadLabel(packageManager).toString()
                val defaultIcon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                val resolvedIcon = IconPackManager.resolveIcon(
                    context = context,
                    packageName = packageName,
                    activityName = resolveInfo.activityInfo.name,
                    fallback = defaultIcon
                )
                SearchItem(
                    id = "app:$packageName",
                    title = label,
                    subtitle = "Aplicación",
                    type = ItemType.APP,
                    keywords = "$label $packageName",
                    icon = resolvedIcon,
                    intent = launchIntent,
                    packageName = packageName
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    fun loadShortcuts(apps: List<SearchItem>): ShortcutLoadResult {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return ShortcutLoadResult(emptyList(), blocked = true)

        if (!launcherApps.hasShortcutHostPermission()) {
            return ShortcutLoadResult(emptyList(), blocked = true)
        }

        val user = Process.myUserHandle()
        val density = context.resources.displayMetrics.densityDpi
        val items = mutableListOf<SearchItem>()

        for (app in apps) {
            val packageName = app.packageName ?: continue

            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }

            val shortcuts = runCatching {
                launcherApps.getShortcuts(query, user)
            }.getOrElse {
                if (it is SecurityException) {
                    return ShortcutLoadResult(items, blocked = true)
                }
                emptyList()
            } ?: emptyList()

            for (shortcut in shortcuts) {
                val label = (shortcut.shortLabel ?: shortcut.longLabel)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (label.isBlank()) continue

                val icon = runCatching {
                    launcherApps.getShortcutIconDrawable(shortcut, density)
                }.getOrNull()

                items += SearchItem(
                    id = "shortcut:$packageName:${shortcut.id}",
                    title = label,
                    subtitle = app.title,
                    type = ItemType.SHORTCUT,
                    keywords = "$label ${app.title}",
                    icon = icon ?: app.icon,
                    packageName = packageName,
                    shortcutId = shortcut.id
                )
            }
        }

        return ShortcutLoadResult(items, blocked = false)
    }
}
