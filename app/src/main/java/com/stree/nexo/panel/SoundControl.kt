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

enum class SilenceResult {
    SILENCED,
    PERMISSION_REQUIRED,
    FAILED
}

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

    suspend fun silenceWithoutRestrictingNotifications(context: Context): SilenceResult {
        val audio = context.getSystemService(AudioManager::class.java) ?: return SilenceResult.FAILED
        // Android exige este permiso a nivel de sistema para activar RINGER_MODE_SILENT
        // (incluso sin usar No molestar). Sin él, Android bloquea el modo silencio.
        if (!soundPolicyAllowed(context)) return SilenceResult.PERMISSION_REQUIRED

        saveActiveVolumes(context, audio)
        val nm = context.getSystemService(NotificationManager::class.java)
        val dndWasOff = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

        val applied = runCatching {
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        }.getOrDefault(false)
        if (!applied) return SilenceResult.FAILED

        // En Android 9+ (y Samsung One UI) RINGER_MODE_SILENT ya no activa No molestar,
        // pero verificamos por seguridad en ROMs antiguas que No molestar siga desactivado.
        if (dndWasOff) {
            delay(100)
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                    runCatching { audio.ringerMode = AudioManager.RINGER_MODE_SILENT }
                }
            }
        }

        return if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            SilenceResult.SILENCED
        } else {
            SilenceResult.FAILED
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

    suspend fun scheduleTimedSilence(context: Context, durationMinutes: Int): SilenceResult {
        val minutes = durationMinutes.coerceIn(1, 24 * 60)
        val result = silenceWithoutRestrictingNotifications(context)
        if (result != SilenceResult.SILENCED) return result

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
        return SilenceResult.SILENCED
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
