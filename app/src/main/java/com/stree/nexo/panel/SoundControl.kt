package com.stree.nexo.panel

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import com.stree.nexo.data.Prefs
import kotlinx.coroutines.delay

/**
 * Control de sonido y temporizador de silencio sin activar No molestar ni
 * restringir la llegada de notificaciones.
 */
object SoundControl {
    private const val TIMER_REQUEST_CODE = 4201

    fun soundPolicyAllowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun saveActiveVolumes(context: Context, audio: AudioManager) {
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

    suspend fun silenceWithoutRestrictingNotifications(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        saveActiveVolumes(context, audio)
        val nm = context.getSystemService(NotificationManager::class.java)
        val dndWasOff = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

        fun muteStreamsOnly() {
            runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
            listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM).forEach { stream ->
                runCatching { audio.setStreamVolume(stream, 0, 0) }
            }
        }

        if (soundPolicyAllowed(context)) {
            val applied = runCatching {
                audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                true
            }.getOrDefault(false)

            if (!applied) {
                muteStreamsOnly()
                return
            }

            // Si el fabricante activa No molestar al cambiar a RINGER_MODE_SILENT,
            // lo revertimos de inmediato para no restringir notificaciones.
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
            muteStreamsOnly()
        }
    }

    fun enableVibrate(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        cancelTimedSilence(context)
        saveActiveVolumes(context, audio)
        return runCatching {
            audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            true
        }.getOrDefault(false)
    }

    fun restoreNormalSound(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        cancelTimedSilence(context)
        val restoredMode = runCatching {
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            true
        }.getOrDefault(false)
        if (!restoredMode) return false

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
        return true
    }

    suspend fun scheduleTimedSilence(context: Context, durationMinutes: Int): Long {
        val minutes = durationMinutes.coerceIn(1, 24 * 60)
        silenceWithoutRestrictingNotifications(context)
        val untilMillis = System.currentTimeMillis() + minutes * 60_000L
        Prefs.setSilentUntilMillis(context, untilMillis)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = timerPendingIntent(context)
        if (alarmManager != null) {
            runCatching {
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, untilMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, untilMillis, pendingIntent)
                }
            }
        }
        EdgePanelService.syncSilentTimer(context)
        return untilMillis
    }

    fun cancelTimedSilence(context: Context) {
        if (Prefs.silentUntilMillis(context) != 0L) {
            Prefs.setSilentUntilMillis(context, 0L)
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        runCatching { alarmManager?.cancel(timerPendingIntent(context)) }
        EdgePanelService.syncSilentTimer(context)
    }

    fun restoreIfExpired(context: Context): Boolean {
        val until = Prefs.silentUntilMillis(context)
        if (until <= 0L) return false
        if (System.currentTimeMillis() + 1_500L < until) return false
        return restoreNormalSound(context)
    }

    private fun timerPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TIMER_REQUEST_CODE,
            Intent(context, SilentTimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
