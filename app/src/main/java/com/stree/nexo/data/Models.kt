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

enum class PanelStyle(val label: String, val subtitle: String) {
    NEXO("Nexo", "Índigo cósmico y destellos iridiscentes"),
    GLASS("Cristal", "Vidrio esmerilado oscuro con bisel especular"),
    MINT("Aurora", "Esmeralda nocturno y turquesa boreal"),
    OCEAN("Océano", "Abismo zafiro con reflejos celestes"),
    AMBER("Ámbar", "Obsidiana cálida con bronce y oro"),
    NEON("Cyberpunk", "Violeta eléctrico y láser cian"),
    SUNSET("Atardecer", "Crepúsculo carmesí, coral y violeta"),
    ROSE("Cuarzo", "Ciruela ahumado y rosa cuarzo"),
    MONO("OLED Puro", "Negro absoluto y titanio de alto contraste")
}

enum class ActivatorStyle(val label: String) {
    LINE("Línea"), PILL("Píldora"), CIRCLE("Círculo"), ICON("Icono")
}

enum class EdgeGesture(val label: String, val subtitle: String, val defaultAction: EdgeGestureAction) {
    SINGLE_TAP("Toque simple", "Tocar la barra una vez", EdgeGestureAction.OPEN_PANEL),
    DOUBLE_TAP("Doble toque", "Tocar dos veces rápido", EdgeGestureAction.OPEN_CONTROLS),
    SWIPE_UP("Deslizar arriba", "Arrastrar hacia arriba", EdgeGestureAction.OPEN_BRIGHTNESS),
    SWIPE_DOWN("Deslizar abajo", "Arrastrar hacia abajo", EdgeGestureAction.OPEN_VOLUME),
    SWIPE_IN("Jalar hacia dentro", "Deslizar rápido desde el borde", EdgeGestureAction.SYSTEM_BACK),
    PULL_HOLD("Jalar y sostener", "Jalar hacia dentro y mantener", EdgeGestureAction.LAST_APP)
}

enum class EdgeGestureAction(val label: String, val description: String) {
    OPEN_PANEL("Panel principal", "Abre tus favoritos y buscador"),
    OPEN_SPOTLIGHT("Búsqueda Spotlight", "Abre la búsqueda rápida estilo iPhone"),
    OPEN_CONTROLS("Controles rápidos", "Silencio, temporizador y asistente"),
    OPEN_BRIGHTNESS("Brillo", "Control rápido de brillo"),
    OPEN_VOLUME("Volumen", "Control rápido de audio"),
    TOGGLE_SILENT("Alternar silencio", "Pasa entre silencio y sonido normal"),
    OPEN_CLIPBOARD("Portapapeles y captura", "Abre la tarjeta de texto copiado y última captura"),
    LAST_APP("Última app", "Vuelve a la app anterior con vista previa del icono"),
    SYSTEM_BACK("Atrás (sistema)", "Vuelve atrás en la app actual"),
    SYSTEM_HOME("Inicio", "Vuelve a la pantalla principal"),
    SYSTEM_RECENTS("Apps recientes", "Abre la vista de multitarea"),
    TAKE_SCREENSHOT("Tomar captura", "Guarda una captura de pantalla"),
    TOGGLE_FLASHLIGHT("Linterna", "Enciende o apaga el flash"),
    VOICE_ASSISTANT("Asistente de voz", "Abre ChatGPT Voz / Live"),
    MINIMIZE_TO_BUBBLE("Minimizar a burbuja", "Convierte la app actual en una burbuja flotante"),
    LAUNCH_APP("Abrir una app…", "Lanza la aplicación que elijas"),
    NONE("Desactivado", "No hace nada con este gesto")
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
    val frequentApps: List<SearchItem> = emptyList(),
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
