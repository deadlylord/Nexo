package com.stree.nexo.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

/** The same controls are embedded in carousel pages and opened by activator gestures. */
@Composable
internal fun ControlPanelPage(mode: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val title = when (mode) {
        "brightness" -> "Brillo"
        "volume" -> "Volumen"
        "lastapp" -> "Volver a la última app"
        else -> "Sonido y silencio"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        when (mode) {
            "brightness" -> PanelSection("Pantalla", Modifier.fillMaxWidth()) { BrightnessSlider(revision) }
            "volume" -> {
                PanelSection("Multimedia", Modifier.fillMaxWidth()) { VolumeSlider("Multimedia", AudioManager.STREAM_MUSIC, revision) }
                PanelSection("Tono y notificaciones", Modifier.fillMaxWidth()) { VolumeSlider("Tono", AudioManager.STREAM_RING, revision) }
                PanelSection("Alarmas", Modifier.fillMaxWidth()) { VolumeSlider("Alarmas", AudioManager.STREAM_ALARM, revision) }
            }
            "lastapp" -> {
                Text("Para identificar la app anterior, Nexo necesita acceso al uso. Jala el activador hacia dentro y mantén el dedo quieto hasta la segunda vibración.")
                TextButton(onClick = { context.openControlPermission(Settings.ACTION_USAGE_ACCESS_SETTINGS) }) { Text("Autorizar acceso al uso") }
            }
            else -> PanelSection("Modo de sonido", Modifier.fillMaxWidth()) {
                val audio = context.getSystemService(AudioManager::class.java)
                val currentMode = remember(revision) { audio.ringerMode }
                listOf(true to "Silencio", false to "Normal").forEach { (silent, label) ->
                    OutlinedButton(onClick = {
                        if (!context.soundPolicyAllowed()) {
                            context.controlMessage("Autoriza el acceso a No molestar para cambiar el modo de sonido")
                        } else runCatching {
                            audio.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                            if (!silent && audio.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                                audio.setStreamVolume(AudioManager.STREAM_RING, (audio.getStreamMaxVolume(AudioManager.STREAM_RING) / 2).coerceAtLeast(1), 0)
                            }
                            revision++
                        }.onFailure { context.controlMessage("El dispositivo no permitió cambiar el modo de sonido") }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(label + if (currentMode == (if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL)) " · Activo" else "")
                    }
                }
                OutlinedButton(onClick = {
                    val voice = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/voice")).setPackage("com.openai.chatgpt")
                    val opened = runCatching { context.startActivity(voice); true }.getOrDefault(false)
                    if (!opened) {
                        val app = context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
                        if (app == null || runCatching { context.startActivity(app) }.isFailure) context.controlMessage("Instala ChatGPT para usar este acceso")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("ChatGPT · Voz / Live") }
                Text("Live depende de la versión de ChatGPT; Nexo no activa el micrófono.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if ((mode == "actions" || mode == "volume") && !context.soundPolicyAllowed()) {
            TextButton(onClick = { context.openControlPermission(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }) {
                Text("Permitir cambios de silencio / No molestar")
            }
        }
    }
}

@Composable
private fun BrightnessSlider(revision: Int) {
    val context = LocalContext.current
    val allowed = remember(revision) { Settings.System.canWrite(context) }
    var value by remember(revision) { mutableFloatStateOf(
        (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(.02f, 1f)
    ) }
    Text("${(value * 100).roundToInt()} %")
    Slider(value, onValueChange = {
        value = it
        context.findActivity()?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = it } }
    }, enabled = allowed, valueRange = .02f..1f, onValueChangeFinished = {
        runCatching {
            check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
            check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255).roundToInt()))
        }.onFailure { context.controlMessage("No se pudo cambiar el brillo del sistema") }
    })
    Text("Brillo del sistema en modo manual.", style = MaterialTheme.typography.bodySmall)
    if (!allowed) TextButton(onClick = {
        context.openControlPermission(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    }) { Text("Permitir modificar ajustes") }
}

@Composable
private fun VolumeSlider(label: String, stream: Int, revision: Int) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maximum = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    val minimum = if (android.os.Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0
    var value by remember(revision) { mutableFloatStateOf(audio.getStreamVolume(stream).toFloat()) }
    Text("$label · ${value.roundToInt()} / $maximum", style = MaterialTheme.typography.bodySmall)
    if (maximum > minimum) Slider(value.coerceIn(minimum.toFloat(), maximum.toFloat()),
        onValueChange = { value = it }, valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = (maximum - minimum - 1).coerceAtLeast(0), onValueChangeFinished = {
            runCatching { audio.setStreamVolume(stream, value.roundToInt(), 0) }
                .onFailure { context.controlMessage("Android requiere acceso a No molestar para ese nivel") }
            value = audio.getStreamVolume(stream).toFloat()
        })
}

private fun Context.soundPolicyAllowed() = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
private fun Context.controlMessage(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
private fun Context.openControlPermission(action: String, data: Uri? = null) {
    runCatching { startActivity(Intent(action, data)) }.onFailure { controlMessage("No se pudo abrir este permiso") }
}
