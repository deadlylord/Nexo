package com.stree.nexo.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val actions: Long,
    val controller: MediaController,
    val packageName: String = controller.packageName.orEmpty()
)

enum class OpenMusicResult {
    OPENED,
    PERMISSION_REQUIRED,
    NO_ACTIVE_APP
}

/** Uses media-session metadata only; notification bodies are not read or stored. */
class MediaControlService : NotificationListenerService() {
    companion object {
        private val mutablePlayback = MutableStateFlow<NowPlaying?>(null)
        val playback = mutablePlayback.asStateFlow()

        fun isAuthorized(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        private fun selectController(list: List<MediaController>): MediaController? =
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_BUFFERING }
                ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
                ?: list.firstOrNull()

        fun activeController(context: Context): MediaController? {
            playback.value?.controller?.let { return it }
            if (!isAuthorized(context)) return null
            val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
            val component = ComponentName(context, MediaControlService::class.java)
            val active = runCatching { manager.getActiveSessions(component).toList() }.getOrDefault(emptyList())
            return selectController(active)
        }

        /** Abre la aplicación de música que tiene la sesión activa en reproducción. */
        fun openPlayingApp(context: Context): OpenMusicResult {
            val controller = activeController(context)
            if (controller != null) {
                val pkg = controller.packageName?.takeIf { it.isNotBlank() }
                val launchIntent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                if (launchIntent != null) {
                    val launched = runCatching {
                        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        true
                    }.getOrDefault(false)
                    if (launched) return OpenMusicResult.OPENED
                }
                val sessionOpened = runCatching {
                    val pending = controller.sessionActivity ?: return@runCatching false
                    pending.send()
                    true
                }.getOrDefault(false)
                if (sessionOpened) return OpenMusicResult.OPENED
            }

            if (!isAuthorized(context)) {
                return OpenMusicResult.PERMISSION_REQUIRED
            }

            val fallbackOpened = runCatching {
                val musicIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(musicIntent)
                true
            }.getOrDefault(false)

            return if (fallbackOpened) OpenMusicResult.OPENED else OpenMusicResult.NO_ACTIVE_APP
        }
    }

    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, MediaControlService::class.java) }
    private var controllers = emptyList<MediaController>()
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = reload()
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { reload() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        runCatching { manager.addOnActiveSessionsChangedListener(sessions, component) }
        reload()
    }

    private fun reload() {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = runCatching { manager.getActiveSessions(component).toList() }.getOrDefault(emptyList())
        controllers.forEach { runCatching { it.registerCallback(callback) } }
        publish()
    }

    private fun publish() {
        val selected = selectController(controllers)
        mutablePlayback.value = selected?.let { controller ->
            val metadata = controller.metadata
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Reproducción multimedia",
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                actions = controller.playbackState?.actions ?: 0L,
                controller = controller,
                packageName = controller.packageName.orEmpty()
            )
        }
    }

    private fun release() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        mutablePlayback.value = null
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val notif = sbn.notification ?: return
        val flags = notif.flags
        val title = notif.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty().lowercase()
        val channel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notif.channelId.orEmpty().lowercase()
        } else ""

        // Detectar capturas de pantalla del sistema (Samsung, AOSP, MIUI, etc.)
        if ("screenshot" in pkg.lowercase() || "screenshot" in channel || "captura" in title || "screenshot" in title) {
            com.stree.nexo.panel.EdgePanelService.notifyScreenshotDetected(applicationContext)
            return
        }

        // Ignorar servicios en curso, resúmenes de grupo o avisos del sistema silenciosos
        val isOngoing = (flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isOngoing || pkg == "android" || pkg == "com.android.systemui") return

        val accent = notif.color.takeIf { it != 0 }
        val pulse = com.stree.nexo.data.ClipboardHistoryStore.recordNotification(
            context = applicationContext,
            packageName = pkg,
            accentColor = accent,
            contentIntent = notif.contentIntent
        )
        com.stree.nexo.panel.EdgePanelService.notifyNotificationPulse(applicationContext, pulse)
    }

    override fun onListenerDisconnected() {
        release()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }
}
