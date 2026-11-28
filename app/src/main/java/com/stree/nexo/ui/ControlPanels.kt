package com.stree.nexo.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stree.nexo.QuickClipActivity
import com.stree.nexo.R
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.SilenceResult
import com.stree.nexo.panel.SoundControl
import com.stree.nexo.ui.theme.visualSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The same controls are embedded in carousel pages and opened by activator gestures. */
@Composable
internal fun ControlPanelPage(
    mode: String,
    onDismiss: (() -> Unit)? = null
) {
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
        "display_audio", "brightness", "volume" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BrightnessSlider(revision = revision, iconBrush = iconBrush)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
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
            var showTimerPicker by remember { mutableStateOf(false) }
            var customMinutes by remember { mutableIntStateOf(45) }
            var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

            val silentUntil = remember(revision, nowMillis) { Prefs.silentUntilMillis(context) }
            LaunchedEffect(silentUntil) {
                if (silentUntil <= 0L) return@LaunchedEffect
                while (true) {
                    val now = System.currentTimeMillis()
                    nowMillis = now
                    if (now >= silentUntil) {
                        SoundControl.restoreIfExpired(context)
                        revision++
                        break
                    }
                    delay(15_000L)
                }
            }

            val remainingMinutes = if (silentUntil > nowMillis) {
                ((silentUntil - nowMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            } else 0
            val timerActive = remainingMinutes > 0

            val ringerMode = remember(revision) { audio.ringerMode }
            val ringVol = remember(revision) { audio.getStreamVolume(AudioManager.STREAM_RING) }
            val notifVol = remember(revision) { audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) }
            val isSilent = ringerMode == AudioManager.RINGER_MODE_SILENT ||
                (ringerMode == AudioManager.RINGER_MODE_NORMAL && ringVol == 0 && notifVol == 0)
            val isVibrate = ringerMode == AudioManager.RINGER_MODE_VIBRATE
            val isNormal = ringerMode == AudioManager.RINGER_MODE_NORMAL && !isSilent

            val activeBg = themeSpec.primary.copy(alpha = 0.22f)
            val activeBorder = themeSpec.primary.copy(alpha = 0.68f)
            val idleBg = themeSpec.surfaceVariant.copy(alpha = 0.42f)
            val idleBorder = themeSpec.outlineVariant.copy(alpha = 0.30f)

            fun requestSilencePermission() {
                context.controlMessage("Activa Nexo aquí para poder poner el celular en silencio (no restringe notificaciones)")
                context.openControlPermission(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }

            fun applyTimedSilence(minutes: Int) {
                Haptics.perform(context)
                scope.launch {
                    when (SoundControl.scheduleTimedSilence(context, minutes)) {
                        SilenceResult.SILENCED -> {
                            nowMillis = System.currentTimeMillis()
                            showTimerPicker = false
                            revision++
                            context.controlMessage("En silencio por ${formatDuration(minutes)}")
                        }
                        SilenceResult.PERMISSION_REQUIRED -> requestSilencePermission()
                        SilenceResult.FAILED -> context.controlMessage("El dispositivo no permitió activar silencio")
                    }
                }
            }

            AnimatedContent(
                targetState = showTimerPicker,
                transitionSpec = {
                    fadeIn(tween(150, easing = PanelMotion.StandardEasing)) togetherWith
                        fadeOut(tween(120, easing = PanelMotion.StandardEasing))
                },
                label = "soundOrTimer"
            ) { pickingTimer ->
                if (!pickingTimer) {
                    fun openQuickClipTab(tabMode: String) {
                        Haptics.perform(context)
                        runCatching {
                            context.startActivity(
                                Intent(context, QuickClipActivity::class.java)
                                    .putExtra("mode", tabMode)
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                                    )
                            )
                        }.onFailure {
                            context.controlMessage("No se pudo abrir la tarjeta rápida")
                        }
                        onDismiss?.invoke()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Fila 1: Silencio, Vibrar y Sonido normal (solo iconos)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ControlIconTile(
                                icon = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Silencio",
                                active = isSilent && !timerActive,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                onLongClick = {
                                    Haptics.perform(context, strong = true)
                                    showTimerPicker = true
                                },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Haptics.perform(context)
                                scope.launch {
                                    SoundControl.cancelTimedSilence(context)
                                    when (SoundControl.silenceWithoutRestrictingNotifications(context)) {
                                        SilenceResult.SILENCED -> revision++
                                        SilenceResult.PERMISSION_REQUIRED -> requestSilencePermission()
                                        SilenceResult.FAILED -> context.controlMessage("El dispositivo no permitió activar silencio")
                                    }
                                }
                            }

                            ControlIconTile(
                                icon = Icons.Filled.Vibration,
                                contentDescription = "Vibrar",
                                active = isVibrate,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Haptics.perform(context)
                                if (!SoundControl.enableVibrate(context)) {
                                    context.controlMessage("No se pudo activar la vibración")
                                }
                                revision++
                            }

                            ControlIconTile(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Sonido normal",
                                active = isNormal,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Haptics.perform(context)
                                if (!SoundControl.restoreNormalSound(context)) {
                                    context.controlMessage("El dispositivo no permitió restaurar el sonido")
                                }
                                revision++
                            }
                        }

                        // Fila 2: Silencio con temporizador y Asistente de voz (solo iconos)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ControlIconTile(
                                icon = Icons.Filled.Timer,
                                contentDescription = "Silenciar por tiempo",
                                active = timerActive,
                                badge = if (timerActive) formatCompactDuration(remainingMinutes) else null,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Haptics.perform(context)
                                showTimerPicker = true
                            }

                            ControlIconTile(
                                icon = Icons.Filled.Alarm,
                                contentDescription = "Alarmas",
                                active = false,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Haptics.perform(context)
                                val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val opened = runCatching { context.startActivity(showAlarms); true }.getOrDefault(false)
                                if (!opened) {
                                    val setAlarm = Intent(AlarmClock.ACTION_SET_ALARM)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (runCatching { context.startActivity(setAlarm) }.isFailure) {
                                        context.controlMessage("No se encontró una app de reloj o alarmas")
                                    }
                                }
                            }
                        }

                        // Fila 3: Capturas recientes y Portapapeles (QuickClip)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ControlIconTile(
                                icon = Icons.Filled.Image,
                                contentDescription = "Capturas",
                                active = false,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                openQuickClipTab("screenshot")
                            }

                            ControlIconTile(
                                icon = Icons.Filled.ContentPaste,
                                contentDescription = "Portapapeles",
                                active = false,
                                iconBrush = iconBrush,
                                activeBg = activeBg,
                                activeBorder = activeBorder,
                                idleBg = idleBg,
                                idleBorder = idleBorder,
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                openQuickClipTab("clipboard")
                            }
                        }
                    }
                } else {
                    // Selector rápido de duración: 15 min, 30 min, 1 hora o personalizado
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).gradientTint(iconBrush),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "¿Por cuánto?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (timerActive) {
                                TextButton(
                                    onClick = {
                                        Haptics.perform(context)
                                        SoundControl.restoreNormalSound(context)
                                        showTimerPicker = false
                                        revision++
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Normal", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            IconButton(
                                onClick = { showTimerPicker = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cerrar",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Opciones rápidas: 15 min, 30 min, 1 hora
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15 to "15 m", 30 to "30 m", 60 to "1 h").forEach { (mins, label) ->
                                Surface(
                                    onClick = { applyTimedSilence(mins) },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = idleBg,
                                    border = BorderStroke(0.9.dp, activeBorder.copy(alpha = 0.45f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = themeSpec.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Duración personalizada con botones - / + y confirmar
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = idleBg,
                            border = BorderStroke(0.8.dp, idleBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        Haptics.perform(context)
                                        val step = if (customMinutes > 60) 15 else 5
                                        customMinutes = (customMinutes - step).coerceAtLeast(5)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Menos tiempo", Modifier.size(18.dp))
                                }
                                Text(
                                    text = formatDuration(customMinutes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = themeSpec.onSurface
                                )
                                IconButton(
                                    onClick = {
                                        Haptics.perform(context)
                                        val step = if (customMinutes >= 60) 15 else 5
                                        customMinutes = (customMinutes + step).coerceAtMost(480)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Más tiempo", Modifier.size(18.dp))
                                }
                                Surface(
                                    onClick = { applyTimedSilence(customMinutes) },
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    color = activeBg,
                                    border = BorderStroke(1.dp, activeBorder)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Activar silencio",
                                            modifier = Modifier.size(18.dp).gradientTint(iconBrush),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    badge: String? = null,
    iconSizeDp: Int = 24,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = if (active) activeBg else idleBg,
        border = BorderStroke(if (active) 1.3.dp else 0.8.dp, if (active) activeBorder else idleBorder),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSizeDp.dp).gradientTint(iconBrush),
                tint = Color.White
            )
            if (badge != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ControlPainterTile(
    painter: Painter,
    contentDescription: String,
    iconBrush: Brush,
    bg: Color,
    border: Color,
    modifier: Modifier = Modifier,
    iconSizeDp: Int = 27,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = bg,
        border = BorderStroke(0.9.dp, border),
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSizeDp.dp).gradientTint(iconBrush),
                tint = Color.White
            )
        }
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> if (minutes == 60) "1 hora" else "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun formatCompactDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h${minutes % 60}"
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

private fun Context.controlMessage(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

private fun Context.openControlPermission(action: String, data: Uri? = null) {
    runCatching { startActivity(Intent(action, data)) }
        .onFailure { controlMessage("No se pudo abrir este permiso") }
}
