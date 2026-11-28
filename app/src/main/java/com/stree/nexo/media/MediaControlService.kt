package com.stree.nexo.media

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Process
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueTrack(
    val queueId: Long,
    val title: String,
    val art: android.graphics.Bitmap? = null
)

data class IslandShortcutItem(
    val id: String,
    val label: String,
    val packageName: String,
    val shortcutId: String,
    val icon: android.graphics.Bitmap? = null
)

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val actions: Long,
    val controller: MediaController,
    val packageName: String = controller.packageName.orEmpty(),
    val albumArt: android.graphics.Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val positionUpdateTimeMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val queue: List<QueueTrack> = emptyList(),
    val isShuffleActive: Boolean = false
)

enum class OpenMusicResult {
    OPENED,
    PERMISSION_REQUIRED,
    NO_ACTIVE_APP
}

/** Uses media-session metadata and ephemeral live notification headers for Dynamic Island. */
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

        fun skipToQueueItem(queueId: Long): Boolean {
            val controller = playback.value?.controller ?: return false
            return runCatching {
                controller.transportControls.skipToQueueItem(queueId)
                true
            }.getOrDefault(false)
        }

        fun toggleShuffle(): Boolean {
            val controller = playback.value?.controller ?: return false
            val actions = controller.playbackState?.customActions ?: return false
            val shuffleAction = actions.firstOrNull { action ->
                val id = action.action.orEmpty()
                val name = action.name?.toString().orEmpty()
                id.contains("SHUFFLE", ignoreCase = true) ||
                    name.contains("shuffle", ignoreCase = true) ||
                    name.contains("aleatorio", ignoreCase = true) ||
                    id.contains("MIX", ignoreCase = true)
            } ?: return false
            return runCatching {
                controller.transportControls.sendCustomAction(shuffleAction, null)
                true
            }.getOrDefault(false)
        }

        fun playArtistRadio(
            context: Context,
            artist: String?,
            title: String?,
            packageName: String?
        ): Boolean {
            val cleanArtist = artist?.trim()?.takeIf { it.isNotEmpty() }
            val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            val query = cleanArtist ?: cleanTitle ?: run {
                return openPlayingApp(context) == OpenMusicResult.OPENED
            }

            fun buildSearchIntent(targetPkg: String?): Intent =
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(
                        MediaStore.EXTRA_MEDIA_FOCUS,
                        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
                    )
                    if (cleanArtist != null) {
                        putExtra(MediaStore.EXTRA_MEDIA_ARTIST, cleanArtist)
                    }
                    putExtra(SearchManager.QUERY, query)
                    if (!targetPkg.isNullOrBlank()) setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            val pkg = packageName?.takeIf { it.isNotBlank() }
            if (pkg != null) {
                val targeted = runCatching {
                    context.startActivity(buildSearchIntent(pkg))
                    true
                }.getOrDefault(false)
                if (targeted) return true
            }

            val generic = runCatching {
                context.startActivity(buildSearchIntent(null))
                true
            }.getOrDefault(false)
            if (generic) return true

            return openPlayingApp(context) == OpenMusicResult.OPENED
        }

        fun loadPlayerShortcuts(context: Context, packageName: String): List<IslandShortcutItem> {
            if (packageName.isBlank()) return emptyList()
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val user = Process.myUserHandle()
            val density = context.resources.displayMetrics.densityDpi
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val shortcuts = runCatching { launcherApps.getShortcuts(query, user) }
                .getOrNull().orEmpty()
            return shortcuts.mapNotNull { sc ->
                val label = (sc.shortLabel ?: sc.longLabel)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val iconBmp = runCatching {
                    launcherApps.getShortcutIconDrawable(sc, density)
                        ?.toBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
                }.getOrNull()
                IslandShortcutItem(
                    id = "sc_${packageName}_${sc.id}",
                    label = label,
                    packageName = packageName,
                    shortcutId = sc.id,
                    icon = iconBmp
                )
            }.take(8)
        }

        fun launchPlayerShortcut(context: Context, packageName: String, shortcutId: String): Boolean {
            if (packageName.isBlank() || shortcutId.isBlank()) return false
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            if (!launcherApps.hasShortcutHostPermission()) return false
            return runCatching {
                launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
                true
            }.getOrDefault(false)
        }
    }

    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, MediaControlService::class.java) }
    private var controllers = emptyList<MediaController>()
    private var lastNotifSignature: String = ""
    private var lastNotifTimestampMs: Long = 0L
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = publish()
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
            val pb = controller.playbackState
            val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val scaledArt = rawArt?.let { bmp ->
                runCatching {
                    if (bmp.width > 128 || bmp.height > 128) {
                        android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                    } else bmp
                }.getOrNull()
            }
            val queueItems = runCatching {
                controller.queue.orEmpty()
                    .take(8)
                    .mapNotNull { item ->
                        val desc = item?.description ?: return@mapNotNull null
                        val title = desc.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        val iconBmp = desc.iconBitmap?.let { bmp ->
                            runCatching {
                                if (bmp.width > 128 || bmp.height > 128) {
                                    android.graphics.Bitmap.createScaledBitmap(bmp, 120, 120, true)
                                } else bmp
                            }.getOrNull()
                        }
                        QueueTrack(queueId = item.queueId, title = title, art = iconBmp)
                    }
            }.getOrDefault(emptyList())
            val shuffleActive = detectShuffleActive(pb)
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Reproducción multimedia",
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                playing = pb?.state == PlaybackState.STATE_PLAYING,
                actions = pb?.actions ?: 0L,
                controller = controller,
                packageName = controller.packageName.orEmpty(),
                albumArt = scaledArt,
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
                positionMs = pb?.position?.coerceAtLeast(0L) ?: 0L,
                positionUpdateTimeMs = pb?.lastPositionUpdateTime ?: android.os.SystemClock.elapsedRealtime(),
                playbackSpeed = pb?.playbackSpeed ?: 1f,
                queue = queueItems,
                isShuffleActive = shuffleActive
            )
        }
    }

    private fun detectShuffleActive(pb: PlaybackState?): Boolean {
        if (pb == null) return false
        val extras = pb.extras
        if (extras != null) {
            runCatching {
                for (key in extras.keySet()) {
                    if (key.contains("shuffle", ignoreCase = true)) {
                        @Suppress("DEPRECATION")
                        val v = extras.get(key)
                        when (v) {
                            is Boolean -> if (v) return true
                            is Int -> if (v > 0) return true
                            is Long -> if (v > 0L) return true
                        }
                    }
                }
            }
        }
        val actions = pb.customActions ?: return false
        return actions.any { action ->
            val id = action.action.orEmpty().lowercase()
            val name = action.name?.toString().orEmpty().lowercase()
            val isShuffle = "shuffle" in id || "shuffle" in name || "aleatorio" in name
            if (!isShuffle) false
            else {
                ("on" in id && "off" !in id) ||
                    ("enabled" in id && "disabled" !in id) ||
                    "disable_shuffle" in id ||
                    "turn_off_shuffle" in id ||
                    "shuffle_active" in id
            }
        }
    }

    private fun release() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        mutablePlayback.value = null
    }

    override fun onNotificationPosted(
        sbn: android.service.notification.StatusBarNotification?,
        rankingMap: RankingMap?
    ) {
        if (sbn == null) return
        val ranking = Ranking()
        val silent = rankingMap?.getRanking(sbn.key, ranking) == true &&
            ranking.importance in android.app.NotificationManager.IMPORTANCE_NONE..android.app.NotificationManager.IMPORTANCE_LOW
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val notif = sbn.notification ?: return
        val flags = notif.flags
        val extras = notif.extras
        val rawTitle = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString()?.trim().orEmpty().take(48)
        val rawText = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)
            ?.toString()?.trim().orEmpty().take(110)
        val titleLower = rawTitle.lowercase()
        val channel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notif.channelId.orEmpty().lowercase()
        } else ""

        // Detectar llamadas entrantes o en curso (teléfono, WhatsApp, etc.)
        if (notif.category == android.app.Notification.CATEGORY_CALL) {
            val callPulse = com.stree.nexo.data.ClipboardHistoryStore.recordCall(
                context = applicationContext,
                key = sbn.key.orEmpty(),
                packageName = pkg,
                callerName = rawTitle.ifBlank { "Llamada en curso" },
                statusText = rawText.ifBlank { "En llamada" },
                contentIntent = notif.contentIntent,
                startedAtMs = if (notif.`when` > 0L) notif.`when` else System.currentTimeMillis()
            )
            com.stree.nexo.panel.EdgePanelService.notifyCallUpdate(applicationContext, callPulse)
            return
        }

        // Detectar capturas de pantalla del sistema (Samsung, AOSP, MIUI, etc.)
        if ("screenshot" in pkg.lowercase() || "screenshot" in channel || "captura" in titleLower || "screenshot" in titleLower) {
            com.stree.nexo.panel.EdgePanelService.notifyScreenshotDetected(applicationContext)
            return
        }

        // Ignorar servicios en curso, resúmenes de grupo, controles multimedia o avisos persistentes
        val isOngoing = (flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (flags and android.app.Notification.FLAG_NO_CLEAR) != 0 ||
            (flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        val ignoredCategory = notif.category == android.app.Notification.CATEGORY_TRANSPORT ||
            notif.category == android.app.Notification.CATEGORY_SERVICE ||
            notif.category == android.app.Notification.CATEGORY_PROGRESS ||
            notif.category == android.app.Notification.CATEGORY_STATUS ||
            notif.category == android.app.Notification.CATEGORY_SYSTEM
        if (isOngoing || ignoredCategory || pkg == "android" || pkg == "com.android.systemui") return

        // Evitar re-disparar la misma notificación repetida sin cambios de texto
        val notifKey = sbn.key.orEmpty()
        val signature = "$notifKey|$rawTitle|$rawText"
        val nowMs = System.currentTimeMillis()
        if (signature == lastNotifSignature && (nowMs - lastNotifTimestampMs) < 15_000L) return
        lastNotifSignature = signature
        lastNotifTimestampMs = nowMs

        // Acciones directas ejecutables (ej. Marcar como leído, Archivar, Silenciar)
        val quickActions = notif.actions
            ?.mapNotNull { action ->
                val actionIntent = action.actionIntent ?: return@mapNotNull null
                val label = action.title?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (!action.remoteInputs.isNullOrEmpty()) return@mapNotNull null
                com.stree.nexo.data.NotificationQuickAction(
                    title = label.take(18),
                    intent = actionIntent
                )
            }
            ?.take(2)
            .orEmpty()

        val accent = notif.color.takeIf { it != 0 }
        val pulse = com.stree.nexo.data.ClipboardHistoryStore.recordNotification(
            context = applicationContext,
            key = notifKey,
            packageName = pkg,
            title = rawTitle,
            textPreview = rawText,
            accentColor = accent,
            contentIntent = notif.contentIntent,
            actions = quickActions,
            silent = silent
        )
        com.stree.nexo.data.ClipboardHistoryStore.rememberIslandNotification(applicationContext, pulse)
        com.stree.nexo.panel.EdgePanelService.notifyNotificationPulse(applicationContext, pulse)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        if (com.stree.nexo.data.ClipboardHistoryStore.activeCall?.key == key) {
            com.stree.nexo.data.ClipboardHistoryStore.clearCall(key)
            com.stree.nexo.panel.EdgePanelService.notifyCallUpdate(applicationContext, null)
        }
        com.stree.nexo.panel.EdgePanelService.notifyNotificationRemoved(applicationContext, key)
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
