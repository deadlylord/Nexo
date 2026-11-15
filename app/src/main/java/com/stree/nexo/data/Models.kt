package com.stree.nexo.data

import android.content.Intent
import android.graphics.drawable.Drawable

enum class ItemType {
    APP,
    SHORTCUT,
    SETTING,
    CONTACT,
    CUSTOM
}

enum class PanelSide {
    START,
    END
}

enum class PanelPage(val label: String) {
    MAIN("Principal"), SETTINGS("Ajustes rápidos"), APPS("Aplicaciones"), CONTACTS("Contactos"), WIDGETS("Widgets")
}

enum class PanelStyle(val label: String) {
    NEXO("Nexo"),
    MINT("Menta"),
    OCEAN("Océano"),
    AMBER("Ámbar"),
    MONO("Monocromo"),
    GLASS("Cristal")
}

enum class ActivatorStyle(val label: String) {
    LINE("Línea"), PILL("Píldora"), CIRCLE("Círculo"), ICON("Icono")
}

data class SearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ItemType,
    val keywords: String = "",
    val icon: Drawable? = null,
    val intent: Intent? = null,
    val packageName: String? = null,
    val shortcutId: String? = null,
    val phoneNumber: String? = null,
    val photoUri: String? = null
)

data class ResultSection(
    val title: String,
    val items: List<SearchItem>
)

data class SearchUiState(
    val query: String = "",
    val sections: List<ResultSection> = emptyList(),
    val allItems: List<SearchItem> = emptyList(),
    val loading: Boolean = true,
    val contactsPermission: Boolean = false,
    val shortcutsBlocked: Boolean = false,
    val pinnedIds: Set<String> = emptySet(),
    val pinnedOrder: List<String> = emptyList(),
    val pinnedSlots: Map<String, Int> = emptyMap(),
    val error: String? = null
)

data class CustomShortcut(
    val id: String,
    val label: String,
    val target: String
)
