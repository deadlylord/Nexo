package com.stree.nexo.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val actions: Long,
    val controller: MediaController
)

/** Uses media-session metadata only; notification bodies are not read or stored. */
class MediaControlService : NotificationListenerService() {
    companion object {
        private val mutablePlayback = MutableStateFlow<NowPlaying?>(null)
        val playback = mutablePlayback.asStateFlow()
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
        val selected = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: controllers.firstOrNull()
        mutablePlayback.value = selected?.let { controller ->
            val metadata = controller.metadata
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Reproducción multimedia",
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                actions = controller.playbackState?.actions ?: 0L,
                controller = controller
            )
        }
    }

    private fun release() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        mutablePlayback.value = null
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
