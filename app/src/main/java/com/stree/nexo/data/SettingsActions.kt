package com.stree.nexo.data

import android.content.Intent
import android.provider.Settings

object SettingsActions {

    private data class Spec(
        val label: String,
        val action: String,
        val keywords: String
    )

    private val specs = listOf(
        Spec("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi inalambrica red redes"),
        Spec("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "bluetooth dispositivos"),
        Spec("Datos móviles", Settings.ACTION_DATA_USAGE_SETTINGS, "datos moviles internet red"),
        Spec("Pantalla", Settings.ACTION_DISPLAY_SETTINGS, "pantalla brillo modo oscuro"),
        Spec("Sonido", Settings.ACTION_SOUND_SETTINGS, "sonido volumen timbre"),
        Spec("Batería", Settings.ACTION_BATTERY_SAVER_SETTINGS, "bateria ahorro energia"),
        Spec("Almacenamiento", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "almacenamiento memoria espacio"),
        Spec("Aplicaciones", Settings.ACTION_APPLICATION_SETTINGS, "aplicaciones apps instalar desinstalar"),
        Spec("Ubicación", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "ubicacion gps localizacion"),
        Spec("Idioma", Settings.ACTION_LOCALE_SETTINGS, "idioma region texto"),
        Spec("Fecha y hora", Settings.ACTION_DATE_SETTINGS, "fecha hora reloj"),
        Spec("Accesibilidad", Settings.ACTION_ACCESSIBILITY_SETTINGS, "accesibilidad"),
        Spec("Seguridad", Settings.ACTION_SECURITY_SETTINGS, "seguridad privacidad"),
        Spec("Opciones de desarrollador", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "desarrollador depuracion usb"),
        Spec("VPN", Settings.ACTION_VPN_SETTINGS, "vpn red privada"),
        Spec("NFC", Settings.ACTION_NFC_SETTINGS, "nfc pagos"),
        Spec("Pantalla de inicio", Settings.ACTION_HOME_SETTINGS, "inicio launcher escritorio"),
        Spec("Ajustes", Settings.ACTION_SETTINGS, "ajustes configuracion sistema")
    )

    fun items(): List<SearchItem> = specs.map { spec ->
        SearchItem(
            id = "setting:${spec.action}",
            title = spec.label,
            subtitle = "Ajuste del sistema",
            type = ItemType.SETTING,
            keywords = spec.keywords,
            intent = Intent(spec.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
