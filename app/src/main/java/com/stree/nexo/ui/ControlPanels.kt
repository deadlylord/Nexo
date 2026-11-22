package com.stree.nexo.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.theme.visualSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The same controls are embedded in carousel pages and opened by activator gestures. */
@Composable
internal fun ControlPanelPage(mode: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val themeRevision = Prefs.themeRevision
    val themeSpec = remember(themeRevision) {
        Prefs.panelStyle(context).visualSpec(light = Prefs.panelLightBackground(context))
    }
    val iconBrush = remember(themeSpec) { themeSpec.actionIconBrush }

    when (mode) {
        "brightness" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                BrightnessSlider(revision = revision, iconBrush = iconBrush)
            }
        }

        "volume" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                VolumeSlider(
                    icon = Icons.Filled.MusicNote,
                    contentDescription = "Multimedia",
                    stream = AudioManager.STREAM_MUSIC,
                    revision = revision,
                    iconBrush = iconBrush
                )
                VolumeSlider(
                    icon = Icons.Filled.Notifications,
                    contentDescription = "Tono y notificaciones",
                    stream = AudioManager.STREAM_RING,
                    revision = revision,
                    iconBrush = iconBrush
                )
                VolumeSlider(
                    icon = Icons.Filled.Alarm,
                    contentDescription = "Alarmas",
                    stream = AudioManager.STREAM_ALARM,
                    revision = revision,
                    iconBrush = iconBrush
                )
            }
        }

        "lastapp" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
            ) {
                Text("Volver a la última app", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Para identificar la app anterior, Nexo necesita acceso al uso. Jala el activador hacia dentro y mantén el dedo quieto hasta la segunda vibración.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { context.openControlPermission(Settings.ACTION_USAGE_ACCESS_SETTINGS) }) {
                    Text("Autorizar acceso al uso")
                }
            }
        }

        else -> {
            val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
            val ringerMode = remember(revision) { audio.ringerMode }
            val ringVol = remember(revision) { audio.getStreamVolume(AudioManager.STREAM_RING) }
            val notifVol = remember(revision) { audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) }
            val isSilent = ringerMode == AudioManager.RINGER_MODE_SILENT ||
                (ringerMode == AudioManager.RINGER_MODE_NORMAL && ringVol == 0 && notifVol == 0)
            val isVibrate = ringerMode == AudioManager.RINGER_MODE_VIBRATE
            val isNormal = ringerMode == AudioManager.RINGER_MODE_NORMAL && !isSilent

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Solo iconos para silencio, vibración y sonido normal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlIconTile(
                        icon = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Silencio",
                        active = isSilent,
                        iconBrush = iconBrush,
                        activeBg = themeSpec.primary.copy(alpha = 0.22f),
                        activeBorder = themeSpec.primary.copy(alpha = 0.65f),
                        idleBg = themeSpec.surfaceVariant.copy(alpha = 0.42f),
                        idleBorder = themeSpec.outlineVariant.copy(alpha = 0.28f),
                        modifier = Modifier.weight(1f).height(62.dp)
                    ) {
                        Haptics.perform(context)
                        scope.launch {
                            silenceWithoutRestrictingNotifications(context, audio)
                            revision++
                        }
                    }

                    ControlIconTile(
                        icon = Icons.Filled.Vibration,
                        contentDescription = "Vibrar",
                        active = isVibrate,
                        iconBrush = iconBrush,
                        activeBg = themeSpec.primary.copy(alpha = 0.22f),
                        activeBorder = themeSpec.primary.copy(alpha = 0.65f),
                        idleBg = themeSpec.surfaceVariant.copy(alpha = 0.42f),
                        idleBorder = themeSpec.outlineVariant.copy(alpha = 0.28f),
                        modifier = Modifier.weight(1f).height(62.dp)
                    ) {
                        Haptics.perform(context)
                        saveActiveVolumes(context, audio)
                        runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
                            .onFailure { context.controlMessage("No se pudo activar la vibración") }
                        revision++
                    }

                    ControlIconTile(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Sonido normal",
                        active = isNormal,
                        iconBrush = iconBrush,
                        activeBg = themeSpec.primary.copy(alpha = 0.22f),
                        activeBorder = themeSpec.primary.copy(alpha = 0.65f),
                        idleBg = themeSpec.surfaceVariant.copy(alpha = 0.42f),
                        idleBorder = themeSpec.outlineVariant.copy(alpha = 0.28f),
                        modifier = Modifier.weight(1f).height(62.dp)
                    ) {
                        Haptics.perform(context)
                        restoreNormalSound(context, audio)
                        revision++
                    }
                }

                // Icono del asistente de voz (ChatGPT Voz / Live), sin texto
                ControlIconTile(
                    icon = Icons.Filled.GraphicEq,
                    contentDescription = "Asistente de voz",
                    active = false,
                    iconBrush = iconBrush,
                    activeBg = themeSpec.primary.copy(alpha = 0.22f),
                    activeBorder = themeSpec.primary.copy(alpha = 0.65f),
                    idleBg = themeSpec.surfaceVariant.copy(alpha = 0.42f),
                    idleBorder = themeSpec.outlineVariant.copy(alpha = 0.34f),
                    iconSizeDp = 28,
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Haptics.perform(context)
                    val voice = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/voice"))
                        .setPackage("com.openai.chatgpt")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val opened = runCatching { context.startActivity(voice); true }.getOrDefault(false)
                    if (!opened) {
                        val app = context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (app == null || runCatching { context.startActivity(app) }.isFailure) {
                            context.controlMessage("Instala ChatGPT para usar el asistente")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlIconTile(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    iconBrush: Brush,
    activeBg: Color,
    activeBorder: Color,
    idleBg: Color,
    idleBorder: Color,
    modifier: Modifier = Modifier,
    iconSizeDp: Int = 24,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (active) activeBg else idleBg,
        border = BorderStroke(if (active) 1.3.dp else 0.8.dp, if (active) activeBorder else idleBorder),
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSizeDp.dp).gradientTint(iconBrush),
                tint = Color.White
            )
        }
    }
}

/**
 * Silencia el tono y las notificaciones del celular sin activar No molestar
 * ni restringir la llegada de notificaciones.
 */
private suspend fun silenceWithoutRestrictingNotifications(context: Context, audio: AudioManager) {
    saveActiveVolumes(context, audio)
    val nm = context.getSystemService(NotificationManager::class.java)
    val dndWasOff = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

    fun muteStreamsOnly() {
        runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
        listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM).forEach { stream ->
            runCatching { audio.setStreamVolume(stream, 0, 0) }
        }
    }

    if (context.soundPolicyAllowed()) {
        val applied = runCatching {
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        }.getOrDefault(false)

        if (!applied) {
            muteStreamsOnly()
            return
        }

        // Algunos sistemas activan No molestar automáticamente al pasar a
        // RINGER_MODE_SILENT. Si eso ocurre, lo revertimos de inmediato para no
        // restringir notificaciones y silenciamos solo los canales de sonido.
        if (dndWasOff) {
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                muteStreamsOnly()
                return
            }
            delay(150)
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                muteStreamsOnly()
            }
        }
    } else {
        // Sin permiso de No molestar silenciamos directo sin tocar restricciones
        // de notificaciones ni pedir cambiar la política de No molestar.
        muteStreamsOnly()
    }
}

private fun saveActiveVolumes(context: Context, audio: AudioManager) {
    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    listOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    ).forEach { stream ->
        val current = runCatching { audio.getStreamVolume(stream) }.getOrDefault(0)
        if (current > 0) Prefs.setSavedStreamVolume(context, stream, current)
    }
}

private fun restoreNormalSound(context: Context, audio: AudioManager) {
    runCatching {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }.onFailure {
        // Si Android bloquea salir de silencio estricto sin permiso, avisamos.
        context.controlMessage("El dispositivo no permitió restaurar el sonido")
        return
    }
    listOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    ).forEach { stream ->
        runCatching {
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            val current = audio.getStreamVolume(stream)
            if (current == 0) {
                val saved = Prefs.savedStreamVolume(context, stream)
                val target = if (saved > 0) saved.coerceAtMost(max) else (max / 2).coerceAtLeast(1)
                audio.setStreamVolume(stream, target, 0)
            }
        }
    }
}

@Composable
private fun BrightnessSlider(revision: Int, iconBrush: Brush) {
    val context = LocalContext.current
    val allowed = remember(revision) { Settings.System.canWrite(context) }
    var value by remember(revision) {
        mutableFloatStateOf(
            (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                .coerceIn(.02f, 1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Filled.Brightness6,
            contentDescription = "Brillo",
            modifier = Modifier.size(22.dp).gradientTint(iconBrush),
            tint = Color.White
        )
        Text(
            text = "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
    }
    Spacer(Modifier.height(6.dp))
    Slider(
        value = value,
        onValueChange = {
            value = it
            context.findActivity()?.window?.let { window ->
                window.attributes = window.attributes.apply { screenBrightness = it }
            }
        },
        enabled = allowed,
        valueRange = .02f..1f,
        onValueChangeFinished = {
            runCatching {
                check(
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                )
                check(
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        (value * 255).roundToInt()
                    )
                )
            }.onFailure { context.controlMessage("No se pudo cambiar el brillo del sistema") }
        }
    )
    if (!allowed) {
        TextButton(
            onClick = {
                context.openControlPermission(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Permitir brillo")
        }
    }
}

@Composable
private fun VolumeSlider(
    icon: ImageVector,
    contentDescription: String,
    stream: Int,
    revision: Int,
    iconBrush: Brush
) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maximum = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    val minimum = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0
    var value by remember(revision) { mutableFloatStateOf(audio.getStreamVolume(stream).toFloat()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp).gradientTint(iconBrush),
            tint = Color.White
        )
        if (maximum > minimum) {
            Slider(
                value = value.coerceIn(minimum.toFloat(), maximum.toFloat()),
                onValueChange = { value = it },
                valueRange = minimum.toFloat()..maximum.toFloat(),
                steps = (maximum - minimum - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
                onValueChangeFinished = {
                    runCatching { audio.setStreamVolume(stream, value.roundToInt(), 0) }
                        .onFailure { context.controlMessage("No se pudo ajustar este volumen") }
                    value = audio.getStreamVolume(stream).toFloat()
                }
            )
        }
    }
}

private fun Context.soundPolicyAllowed() =
    getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

private fun Context.controlMessage(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

private fun Context.openControlPermission(action: String, data: Uri? = null) {
    runCatching { startActivity(Intent(action, data)) }
        .onFailure { controlMessage("No se pudo abrir este permiso") }
}
