package com.stree.nexo.ui

import android.content.Intent
import android.media.session.PlaybackState
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stree.nexo.media.MediaControlService

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MiniPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playback by MediaControlService.playback.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    fun authorized() = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    fun supports(action: Long) = playback?.actions?.let { it and action != 0L } == true
    fun toggle() {
        val current = playback ?: return
        runCatching {
            if (current.playing) current.controller.transportControls.pause()
            else current.controller.transportControls.play()
        }.onFailure { Toast.makeText(context, "La aplicación no permitió controlar la reproducción", Toast.LENGTH_SHORT).show() }
    }
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(playback?.title ?: "Música", modifier = Modifier.weight(1f).clickable { expanded = true }.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Box(
        Modifier.size(48.dp).clip(CircleShape).combinedClickable(
            role = Role.Button,
            onClickLabel = if (playback?.playing == true) "Pausar música" else "Mini reproductor",
            onLongClickLabel = "Mostrar canción y controles",
            onClick = {
                val action = if (playback?.playing == true) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                if (authorized() && playback != null && (supports(action) || supports(PlaybackState.ACTION_PLAY_PAUSE))) toggle()
                else expanded = true
            },
            onLongClick = { expanded = true }
        ), contentAlignment = Alignment.Center
    ) {
        Icon(
            if (playback == null) Icons.Filled.MusicNote else if (playback?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playback?.playing == true) "Pausar · ${playback?.title}" else "Mini reproductor",
            tint = MaterialTheme.colorScheme.primary
        )
    }
        IconButton(enabled = authorized() && supports(PlaybackState.ACTION_SKIP_TO_NEXT), onClick = {
            runCatching { playback?.controller?.transportControls?.skipToNext() }
                .onFailure { Toast.makeText(context, "No se pudo pasar a la siguiente canción", Toast.LENGTH_SHORT).show() }
        }) { Icon(Icons.Filled.SkipNext, "Siguiente canción") }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false }, title = { Text("Mini reproductor") },
        text = {
            Column {
                if (!authorized()) Text("Para detectar y controlar la música de otras apps, Android exige acceso a notificaciones. Nexo usa las sesiones multimedia; no guarda el contenido de tus notificaciones.")
                else if (playback == null) Text("Inicia música en una aplicación compatible. Sus controles aparecerán aquí.")
                else {
                    Text(playback!!.title, style = MaterialTheme.typography.titleMedium)
                    if (playback!!.artist.isNotBlank()) Text(playback!!.artist)
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(enabled = supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS), onClick = {
                            runCatching { playback?.controller?.transportControls?.skipToPrevious() }
                        }) { Icon(Icons.Filled.SkipPrevious, "Anterior") }
                        IconButton(enabled = supports(PlaybackState.ACTION_PLAY_PAUSE) || supports(
                            if (playback?.playing == true) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                        ), onClick = { toggle() }) {
                            Icon(if (playback?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir o pausar")
                        }
                        IconButton(enabled = supports(PlaybackState.ACTION_SKIP_TO_NEXT), onClick = {
                            runCatching { playback?.controller?.transportControls?.skipToNext() }
                        }) { Icon(Icons.Filled.SkipNext, "Siguiente") }
                    }
                }
            }
        },
        confirmButton = {
            if (!authorized()) TextButton(onClick = {
                expanded = false
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Abre Ajustes → Acceso a notificaciones y autoriza Nexo", Toast.LENGTH_LONG).show() }
            }) { Text("Configurar acceso") }
            else TextButton(onClick = { expanded = false }) { Text("Cerrar") }
        },
        dismissButton = { if (!authorized()) TextButton(onClick = { expanded = false }) { Text("Ahora no") } }
    )
}
