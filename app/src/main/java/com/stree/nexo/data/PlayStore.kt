package com.stree.nexo.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Búsqueda en Play Store con URI oficial `market://`. Si el teléfono no tiene la
 * tienda instalada se recurre a la tienda web, sin pedir datos adicionales.
 */
object PlayStore {
    fun homeItem(context: Context): SearchItem = SearchItem(
        id = "store:", title = "Play Store", subtitle = "Tienda de aplicaciones",
        type = ItemType.CUSTOM,
        icon = runCatching { context.packageManager.getApplicationIcon("com.android.vending") }.getOrNull(),
        intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps"))
    )

    fun open(context: Context, query: String): Boolean {
        val text = query.trim()
        if (text.isBlank()) {
            val launched = runCatching {
                val launcher = context.packageManager.getLaunchIntentForPackage("com.android.vending")
                    ?: return@runCatching false
                context.startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        val market = Intent(Intent.ACTION_VIEW,
            if (text.isBlank()) Uri.parse("market://apps") else marketUri(text))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(market); true }.getOrElse {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    if (text.isBlank()) Uri.parse("https://play.google.com/store/apps") else webUri(text))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
    }

    fun item(query: String): SearchItem {
        val text = query.trim()
        return SearchItem(
            id = "store:$text",
            title = "Buscar \"$text\" en Play Store",
            subtitle = "Play Store · instalar o abrir la ficha",
            type = ItemType.CUSTOM,
            keywords = "play store tienda instalar descargar apk",
            intent = Intent(Intent.ACTION_VIEW, marketUri(text))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Consulta guardada en el id de la fila de tienda. */
    fun queryOf(item: SearchItem): String? =
        if (item.id.startsWith("store:")) item.id.removePrefix("store:") else null

    private fun marketUri(query: String) =
        Uri.parse("market://search?q=${Uri.encode(query)}&c=apps")

    private fun webUri(query: String) =
        Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps")
}
