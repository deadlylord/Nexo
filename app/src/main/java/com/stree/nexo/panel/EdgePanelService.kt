package com.stree.nexo.panel

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.stree.nexo.ActivatorControlsActivity
import com.stree.nexo.MainActivity
import com.stree.nexo.QuickClipActivity
import com.stree.nexo.R
import com.stree.nexo.SearchActivity
import com.stree.nexo.FloatingWindowMenuActivity
import com.stree.nexo.SpotlightSearchActivity
import com.stree.nexo.data.CallPulse
import com.stree.nexo.data.ClipboardHistoryStore
import com.stree.nexo.data.EdgeGesture
import com.stree.nexo.data.EdgeGestureAction
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.LastAppNavigator
import com.stree.nexo.data.NotificationPulse
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.PreviousAppTarget
import com.stree.nexo.data.UsageStore
import com.stree.nexo.floating.FloatingAppBubbleController
import com.stree.nexo.floating.FloatingWindowRegistry
import com.stree.nexo.media.MediaControlService
import com.stree.nexo.media.NowPlaying
import androidx.compose.ui.graphics.toArgb
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.ui.theme.visualSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

class EdgePanelService : Service() {
    private val floatingWidget by lazy { com.stree.nexo.widget.FloatingWidgetController(this) }
    private val floatingBubble by lazy {
        FloatingAppBubbleController(this) { startSplit -> openFloatingWindowMenu(startSplit) }
    }

    private lateinit var windowManager: WindowManager
    private var handleView: EdgeHandleView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var idleWidthPx = 0
    private var idleHeightPx = 0

    // Isla Dinámica superior (OLED negra)
    private var islandView: DynamicIslandView? = null
    private var islandParams: WindowManager.LayoutParams? = null
    private var islandTemporaryAlert = false
    private var islandMediaExpanded = false
    private var islandHubExpanded = false
    private var islandNotificationExpanded = false
    private var mediaHiddenWhilePaused = false
    private var currentPlayback: NowPlaying? = null
    private var currentPlaybackIcon: Bitmap? = null
    private var currentPlaybackPkg: String? = null
    private var currentCall: CallPulse? = null
    private var playbackJob: Job? = null

    // Cronómetro integrado en la Isla Dinámica
    private var stopwatchRunning = false
    private var stopwatchStartElapsed = 0L
    private var stopwatchAccumulatedMs = 0L

    // Receptores del sistema (batería, modo de sonido, audio Bluetooth y desbloqueo)
    private var systemReceiverRegistered = false
    private var audioCallbackRegistered = false
    private var audioCallbackPrimed = false
    private var lastKnownRingerMode = -1
    private var screenWasOff = false
    private var lastUnlockOpenMs = 0L

    private val autoOpenUnlockRunnable = Runnable {
        if (!Prefs.autoOpenOnUnlock(this)) return@Runnable
        if (!Settings.canDrawOverlays(this)) return@Runnable
        if (panelUiVisible || hostUiVisible) return@Runnable
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return@Runnable
        val audioMode = getSystemService(AudioManager::class.java)?.mode
        if (audioMode == AudioManager.MODE_IN_CALL || audioMode == AudioManager.MODE_RINGTONE) return@Runnable

        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlockOpenMs < 1_500L) return@Runnable
        lastUnlockOpenMs = now

        if (Prefs.autoOpenOnUnlockSpotlight(this)) {
            openSpotlight()
        } else {
            openPanel()
        }
    }

    private fun scheduleAutoOpenOnUnlock() {
        if (!Prefs.autoOpenOnUnlock(this)) return
        gestureHandler.removeCallbacks(autoOpenUnlockRunnable)
        // Breve pausa para que termine la animación de desbloqueo sin cancelar el panel ni el teclado.
        gestureHandler.postDelayed(autoOpenUnlockRunnable, 280L)
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenWasOff = true
                    gestureHandler.removeCallbacks(autoOpenUnlockRunnable)
                }
                Intent.ACTION_USER_PRESENT -> {
                    screenWasOff = false
                    scheduleAutoOpenOnUnlock()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // En equipos sin bloqueo de pantalla Android no envía ACTION_USER_PRESENT.
                    val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                    if (screenWasOff && !locked) {
                        screenWasOff = false
                        scheduleAutoOpenOnUnlock()
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    val pct = readBatteryPercent()
                    showIslandAlert(
                        IslandDisplayState(
                            mode = IslandMode.CHARGING,
                            title = "Cargando",
                            batteryPercent = pct
                        ),
                        durationMs = 3_600L
                    )
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = intent.getIntExtra(
                        AudioManager.EXTRA_RINGER_MODE,
                        getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                    )
                    if (lastKnownRingerMode == -1) {
                        lastKnownRingerMode = mode
                        return
                    }
                    if (mode == lastKnownRingerMode) return
                    lastKnownRingerMode = mode
                    if (islandHubExpanded) {
                        refreshIslandIdleState()
                    } else {
                        showIslandAlert(
                            IslandDisplayState(
                                mode = IslandMode.RINGER_MODE,
                                ringerMode = mode
                            ),
                            durationMs = 2_800L
                        )
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!audioCallbackPrimed) {
                audioCallbackPrimed = true
                return
            }
            val bt = addedDevices?.firstOrNull { dev ->
                dev.isSink && (
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                        dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    )
            } ?: return
            val name = bt.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Audífonos Bluetooth"
            showIslandAlert(
                IslandDisplayState(
                    mode = IslandMode.BLUETOOTH,
                    title = name
                ),
                durationMs = 3_800L
            )
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var rejectedGesture = false
    private var doubleTapHandled = false
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val gestureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pullArmed = false
    private var pullTriggered = false
    private var holdX = 0f
    private var holdY = 0f

    private var cachedPreviousTarget: PreviousAppTarget? = null
    private var cachedPreviousIcon: Bitmap? = null
    private var prefetchPreviousJob: Job? = null

    private var lastCopyAlertMs = 0L
    private var lastShotAlertMs = 0L
    private var lastSeenScreenshotUri: Uri? = null
    private var screenshotObserverRegistered = false
    private var clipboardListenerRegistered = false
    private var torchCallbackRegistered = false
    private var torchCameraId: String? = null
    private var torchEnabled = false

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val now = System.currentTimeMillis()
        if (now - lastCopyAlertMs < 900L) return@OnPrimaryClipChangedListener
        ClipboardHistoryStore.readSystemClipboard(applicationContext)
        triggerCopyAlert()
    }

    private val screenshotCheckRunnable = Runnable {
        if (!ClipboardHistoryStore.hasScreenshotPermission(this)) return@Runnable
        gestureScope.launch {
            val latest = withContext(Dispatchers.IO) {
                ClipboardHistoryStore.queryLatestScreenshot(this@EdgePanelService)
            } ?: return@launch
            val isNew = latest.uri != lastSeenScreenshotUri
            val isRecent = (System.currentTimeMillis() - latest.timestamp) in -5_000L..20_000L
            if (isNew) {
                lastSeenScreenshotUri = latest.uri
                if (isRecent) {
                    triggerScreenshotAlert(alreadyQueried = true)
                }
            }
        }
    }

    private val screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            gestureHandler.removeCallbacks(screenshotCheckRunnable)
            gestureHandler.postDelayed(screenshotCheckRunnable, 1_100L)
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId || torchCameraId == null) {
                torchCameraId = cameraId
                torchEnabled = enabled
                if (islandHubExpanded) refreshIslandIdleState()
            }
        }
    }

    private val collapseAlertRunnable = Runnable {
        collapseLiveAlert()
    }

    private val islandAlertTimeoutRunnable = Runnable {
        islandTemporaryAlert = false
        islandNotificationExpanded = false
        refreshIslandIdleState()
    }

    private val islandExpandTimeoutRunnable = Runnable {
        islandMediaExpanded = false
        islandHubExpanded = false
        islandNotificationExpanded = false
        refreshIslandIdleState()
    }

    private val mediaPauseHideRunnable = Runnable {
        if (currentPlayback?.playing != true) {
            mediaHiddenWhilePaused = true
            islandMediaExpanded = false
            refreshIslandIdleState()
        }
    }

    private val islandLiveTickRunnable = object : Runnable {
        override fun run() {
            val mode = islandView?.mode ?: return
            val needsFastTick = mode == IslandMode.EXPANDED_MEDIA ||
                mode == IslandMode.CALL ||
                mode == IslandMode.STOPWATCH ||
                (mode == IslandMode.EXPANDED_HUB && stopwatchRunning)
            val needsSlowTick = mode == IslandMode.SILENT_TIMER
            if (needsFastTick) {
                refreshIslandIdleState()
                gestureHandler.postDelayed(this, 1_000L)
            } else if (needsSlowTick) {
                refreshIslandIdleState()
                gestureHandler.postDelayed(this, 30_000L)
            }
        }
    }

    private val holdAction = Runnable {
        if (pullArmed && !pullTriggered) {
            pullTriggered = true
            Haptics.perform(this, strong = true)
            collapseLiveAlert()
            executeGesture(EdgeGesture.PULL_HOLD)
        }
    }

    private val touchSlop: Int by lazy {
        ViewConfiguration.get(this).scaledTouchSlop
    }

    private val tapDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                if (!rejectedGesture && !pullTriggered && !doubleTapHandled) {
                    val view = handleView
                    if (view != null && view.isShowingLiveAlert()) {
                        handleLiveAlertTap(view.liveMode)
                    } else {
                        executeGesture(EdgeGesture.SINGLE_TAP)
                    }
                }
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                doubleTapHandled = true
                gestureHandler.removeCallbacks(holdAction)
                val view = handleView
                if (view != null && view.isShowingLiveAlert() && view.liveMode == EdgeLiveMode.NOTIFICATION) {
                    Haptics.perform(this@EdgePanelService)
                    collapseLiveAlert()
                    showNotificationPreview()
                    return true
                }
                collapseLiveAlert()
                executeGesture(EdgeGesture.DOUBLE_TAP)
                return true
            }
        }).apply { setIsLongpressEnabled(false) }
    }

    private val restoreSoundRunnable = Runnable {
        SoundControl.restoreIfExpired(applicationContext)
        refreshIslandIdleState()
    }

    private fun rescheduleSilentTimer() {
        gestureHandler.removeCallbacks(restoreSoundRunnable)
        val until = Prefs.silentUntilMillis(this)
        if (until <= 0L) {
            refreshIslandIdleState()
            return
        }
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            SoundControl.restoreIfExpired(applicationContext)
            refreshIslandIdleState()
        } else {
            gestureHandler.postDelayed(restoreSoundRunnable, remaining)
            refreshIslandIdleState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundNotification()
        currentCall = ClipboardHistoryStore.activeCall
        lastKnownRingerMode = getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (Prefs.isPanelEnabled(this)) {
            addHandle()
            setupDynamicIsland()
        }
        rescheduleSilentTimer()
        registerClipboardObserver()
        registerScreenshotObserver()
        registerTorchCallback()
        registerSystemIslandObservers()
        observeMediaPlayback()

        setHandleVisible(!hostUiVisible && !panelUiVisible)
        floatingBubble.setSuppressed(hostUiVisible || panelUiVisible)
        floatingBubble.sync()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshIslandIdleState()
        floatingBubble.sync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            addHandle()
            setupDynamicIsland()
            registerScreenshotObserver()
            floatingBubble.sync()
        }
        if (intent?.action == ACTION_SYNC_BUBBLE) {
            floatingBubble.setSuppressed(hostUiVisible || panelUiVisible)
            floatingBubble.sync()
        }
        if (intent?.action == ACTION_WIDGET) intent.getStringExtra("item_id")?.let { id ->
            runCatching { floatingWidget.show(id) }
                .onFailure { Toast.makeText(this, "No se pudo abrir el widget", Toast.LENGTH_SHORT).show() }
        }
        rescheduleSilentTimer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        playbackJob?.cancel()
        unregisterObservers()
        gestureHandler.removeCallbacksAndMessages(null)
        gestureScope.cancel()
        floatingWidget.close()
        floatingBubble.hide()
        hideNotificationPreviewPopup()
        removeDynamicIsland()
        removeHandle()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun registerSystemIslandObservers() {
        if (!systemReceiverRegistered) {
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(systemReceiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(systemReceiver, filter)
                }
                systemReceiverRegistered = true
            }
        }
        if (!audioCallbackRegistered) {
            runCatching {
                audioCallbackPrimed = false
                getSystemService(AudioManager::class.java)
                    ?.registerAudioDeviceCallback(audioDeviceCallback, gestureHandler)
                audioCallbackRegistered = true
            }
        }
    }

    private fun readBatteryPercent(): Int {
        val bm = getSystemService(BatteryManager::class.java)
        val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (cap in 0..100) return cap
        val sticky = runCatching { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun isDeviceLocked(): Boolean =
        runCatching { getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true }
            .getOrDefault(false)

    private fun isLandscapeHidden(): Boolean =
        Prefs.dynamicIslandHideLandscape(this) &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun observeMediaPlayback() {
        playbackJob?.cancel()
        playbackJob = gestureScope.launch {
            MediaControlService.playback.collectLatest { playing ->
                currentPlayback = playing
                val pkg = playing?.packageName?.takeIf { it.isNotBlank() }
                if (pkg != currentPlaybackPkg) {
                    currentPlaybackPkg = pkg
                    currentPlaybackIcon = if (pkg != null) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                packageManager.getApplicationIcon(pkg).toBitmap(72, 72)
                            }.getOrNull()
                        }
                    } else null
                }

                gestureHandler.removeCallbacks(mediaPauseHideRunnable)
                when {
                    playing == null -> {
                        mediaHiddenWhilePaused = false
                        islandMediaExpanded = false
                    }
                    playing.playing -> {
                        mediaHiddenWhilePaused = false
                    }
                    else -> {
                        // Al pausar, cerrar suavemente la vista de música tras una breve pausa
                        val hideDelay = if (islandMediaExpanded) 3_200L else 1_800L
                        gestureHandler.postDelayed(mediaPauseHideRunnable, hideDelay)
                    }
                }

                if (!islandTemporaryAlert && !islandHubExpanded && !islandNotificationExpanded) {
                    refreshIslandIdleState()
                }
            }
        }
    }

    private fun setupDynamicIsland() {
        removeDynamicIsland()
        if (!Settings.canDrawOverlays(this) || !Prefs.dynamicIslandEnabled(this)) return

        val params = WindowManager.LayoutParams(
            dp(Prefs.dynamicIslandIdleWidth(this)),
            dp(Prefs.dynamicIslandIdleHeight(this)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(Prefs.dynamicIslandHorizontalOffset(this@EdgePanelService))
            y = dp(Prefs.dynamicIslandTopOffset(this@EdgePanelService))
            // Imprescindible para flotar y recibir toques sobre la barra de estado y cámara en TODAS las apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = DynamicIslandView(this).apply {
            visibility = View.GONE
            listener = object : IslandInteractionListener {
                override fun onTapMain(mode: IslandMode) = handleIslandMainTap(mode)
                override fun onLongPress(mode: IslandMode) = handleIslandLongPress(mode)
                override fun onSwipeDown(mode: IslandMode) = handleIslandSwipeDown(mode)
                override fun onSwipeUpDismiss() = handleIslandSwipeUp()
                override fun onTapSatellite(satellite: IslandSatellite) = handleSatelliteTap(satellite)
                override fun onMediaPrev() {
                    Haptics.perform(this@EdgePanelService)
                    currentPlayback?.controller?.transportControls?.skipToPrevious()
                    scheduleExpandAutoCollapse(6_500L)
                }
                override fun onMediaPlayPause() {
                    Haptics.perform(this@EdgePanelService)
                    val now = currentPlayback ?: return
                    if (now.playing) {
                        now.controller.transportControls.pause()
                        gestureHandler.removeCallbacks(mediaPauseHideRunnable)
                        gestureHandler.postDelayed(mediaPauseHideRunnable, 2_500L)
                    } else {
                        mediaHiddenWhilePaused = false
                        now.controller.transportControls.play()
                        scheduleExpandAutoCollapse(6_500L)
                    }
                }
                override fun onMediaNext() {
                    Haptics.perform(this@EdgePanelService)
                    currentPlayback?.controller?.transportControls?.skipToNext()
                    scheduleExpandAutoCollapse(6_500L)
                }
                override fun onMediaOpenApp() {
                    Haptics.perform(this@EdgePanelService)
                    collapseIslandExpansion()
                    val pkg = currentPlayback?.packageName
                    if (!pkg.isNullOrBlank()) {
                        if (!ItemLauncher.launchPackageFloating(this@EdgePanelService, pkg)) {
                            MediaControlService.openPlayingApp(this@EdgePanelService)
                        }
                    } else {
                        MediaControlService.openPlayingApp(this@EdgePanelService)
                    }
                }
                override fun onNotificationOpenFloating() {
                    collapseIslandAllTemp()
                    openLatestNotification()
                }
                override fun onNotificationQuickAction(index: Int) {
                    Haptics.perform(this@EdgePanelService)
                    val action = ClipboardHistoryStore.latestNotification?.actions?.getOrNull(index)
                    collapseIslandAllTemp()
                    if (action != null) {
                        runCatching { action.intent.send() }
                            .onFailure { Toast.makeText(this@EdgePanelService, "No se pudo ejecutar ${action.title}", Toast.LENGTH_SHORT).show() }
                    }
                }
                override fun onHubAction(action: IslandHubAction) = handleHubAction(action)
            }
        }

        islandView = view
        islandParams = params
        runCatching {
            windowManager.addView(view, params)
            refreshIslandIdleState()
        }.onFailure {
            islandView = null
            islandParams = null
        }
    }

    private fun removeDynamicIsland() {
        gestureHandler.removeCallbacks(islandAlertTimeoutRunnable)
        gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
        gestureHandler.removeCallbacks(islandLiveTickRunnable)
        gestureHandler.removeCallbacks(mediaPauseHideRunnable)
        islandTemporaryAlert = false
        islandMediaExpanded = false
        islandHubExpanded = false
        islandNotificationExpanded = false
        val view = islandView ?: return
        runCatching { windowManager.removeView(view) }
        islandView = null
        islandParams = null
    }

    private fun collapseIslandExpansion() {
        gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
        islandMediaExpanded = false
        islandHubExpanded = false
        islandNotificationExpanded = false
        refreshIslandIdleState()
    }

    private fun collapseIslandAllTemp() {
        gestureHandler.removeCallbacks(islandAlertTimeoutRunnable)
        gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
        islandTemporaryAlert = false
        islandMediaExpanded = false
        islandHubExpanded = false
        islandNotificationExpanded = false
        refreshIslandIdleState()
    }

    private fun handleIslandMainTap(mode: IslandMode) {
        Haptics.perform(this)
        when (mode) {
            IslandMode.IDLE_PILL -> openIslandHub()
            IslandMode.COMPACT_MEDIA -> {
                islandHubExpanded = false
                islandNotificationExpanded = false
                islandMediaExpanded = true
                refreshIslandIdleState()
                scheduleExpandAutoCollapse(6_500L)
            }
            IslandMode.EXPANDED_MEDIA, IslandMode.EXPANDED_HUB -> collapseIslandExpansion()
            IslandMode.NOTIFICATION, IslandMode.EXPANDED_NOTIFICATION -> {
                collapseIslandAllTemp()
                openLatestNotification()
            }
            IslandMode.CLIPBOARD -> {
                collapseIslandAllTemp()
                openQuickClip("clipboard")
            }
            IslandMode.SCREENSHOT -> {
                collapseIslandAllTemp()
                openQuickClip("screenshot")
            }
            IslandMode.CALL -> {
                val call = currentCall ?: return
                ItemLauncher.launchNotificationFloating(this, call.packageName, call.contentIntent)
            }
            IslandMode.STOPWATCH -> openIslandHub()
            IslandMode.SILENT_TIMER, IslandMode.RINGER_MODE -> openControls("actions")
            IslandMode.CHARGING, IslandMode.BLUETOOTH -> collapseIslandAllTemp()
            IslandMode.HIDDEN -> Unit
        }
    }

    private fun handleIslandLongPress(mode: IslandMode) {
        Haptics.perform(this, strong = true)
        if (mode == IslandMode.NOTIFICATION) {
            expandLatestNotification()
        } else {
            openIslandHub()
        }
    }

    private fun handleIslandSwipeDown(mode: IslandMode) {
        Haptics.perform(this)
        when (mode) {
            IslandMode.NOTIFICATION -> expandLatestNotification()
            IslandMode.COMPACT_MEDIA -> {
                islandMediaExpanded = true
                refreshIslandIdleState()
                scheduleExpandAutoCollapse(6_500L)
            }
            else -> openIslandHub()
        }
    }

    private fun handleIslandSwipeUp() {
        Haptics.perform(this)
        if (islandMediaExpanded || islandHubExpanded || islandNotificationExpanded || islandTemporaryAlert) {
            collapseIslandAllTemp()
        } else if (islandView?.mode == IslandMode.COMPACT_MEDIA) {
            mediaHiddenWhilePaused = true
            refreshIslandIdleState()
        } else if (islandView?.mode == IslandMode.STOPWATCH) {
            if (Prefs.dynamicIslandIdlePill(this)) {
                renderIsland(IslandDisplayState(mode = IslandMode.IDLE_PILL))
            } else {
                renderIsland(IslandDisplayState(mode = IslandMode.HIDDEN))
            }
        }
    }

    private fun handleSatelliteTap(satellite: IslandSatellite) {
        Haptics.perform(this)
        when (satellite) {
            IslandSatellite.MEDIA -> {
                islandHubExpanded = false
                islandNotificationExpanded = false
                islandMediaExpanded = true
                refreshIslandIdleState()
                scheduleExpandAutoCollapse(6_500L)
            }
            IslandSatellite.STOPWATCH -> openIslandHub()
            IslandSatellite.SILENT_TIMER -> openControls("actions")
            IslandSatellite.CALL -> {
                currentCall?.let { call ->
                    ItemLauncher.launchNotificationFloating(this, call.packageName, call.contentIntent)
                }
            }
            IslandSatellite.NOTIFICATION -> expandLatestNotification()
            IslandSatellite.NONE -> Unit
        }
    }

    private fun openIslandHub() {
        islandTemporaryAlert = false
        islandMediaExpanded = false
        islandNotificationExpanded = false
        islandHubExpanded = true
        gestureHandler.removeCallbacks(islandAlertTimeoutRunnable)
        refreshIslandIdleState()
        scheduleExpandAutoCollapse(7_000L)
    }

    private fun expandLatestNotification() {
        val pulse = ClipboardHistoryStore.latestNotification ?: run {
            openIslandHub()
            return
        }
        islandTemporaryAlert = true
        islandMediaExpanded = false
        islandHubExpanded = false
        islandNotificationExpanded = true
        gestureHandler.removeCallbacks(islandAlertTimeoutRunnable)
        val locked = isDeviceLocked()
        val preview = when {
            locked -> "Contenido oculto (pantalla bloqueada)"
            pulse.textPreview.isNotBlank() -> pulse.textPreview
            else -> "Toca Flotante para abrir en ventana emergente"
        }
        renderIsland(
            IslandDisplayState(
                mode = IslandMode.EXPANDED_NOTIFICATION,
                title = pulse.appName,
                subtitle = if (locked) "Notificación recibida" else pulse.title,
                preview = preview,
                icon = pulse.appIcon,
                accentColor = pulse.accentColor,
                notificationActionLabels = if (locked) emptyList() else pulse.actions.map { it.title }
            )
        )
        gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
        gestureHandler.postDelayed(islandExpandTimeoutRunnable, 7_500L)
    }

    private fun handleHubAction(action: IslandHubAction) {
        Haptics.perform(this)
        when (action) {
            IslandHubAction.SPOTLIGHT -> {
                collapseIslandExpansion()
                openSpotlight()
            }
            IslandHubAction.FLASHLIGHT -> {
                toggleFlashlight()
                refreshIslandIdleState()
                scheduleExpandAutoCollapse(6_500L)
            }
            IslandHubAction.SILENT -> {
                cycleRingerModeFromHub()
                gestureHandler.postDelayed({ if (islandHubExpanded) refreshIslandIdleState() }, 180L)
                scheduleExpandAutoCollapse(6_500L)
            }
            IslandHubAction.SCREENSHOT -> {
                collapseIslandExpansion()
                gestureHandler.postDelayed({
                    if (!SplitScreenService.performTakeScreenshot()) promptAccessibilityNeeded()
                }, 220L)
            }
            IslandHubAction.CLIPBOARD -> {
                collapseIslandExpansion()
                openQuickClip("clipboard")
            }
            IslandHubAction.STOPWATCH_TOGGLE -> {
                if (stopwatchRunning) {
                    stopwatchAccumulatedMs = currentStopwatchMs()
                    stopwatchRunning = false
                } else {
                    stopwatchStartElapsed = SystemClock.elapsedRealtime()
                    stopwatchRunning = true
                }
                refreshIslandIdleState()
                scheduleExpandAutoCollapse(6_500L)
            }
            IslandHubAction.STOPWATCH_RESET -> {
                stopwatchRunning = false
                stopwatchAccumulatedMs = 0L
                refreshIslandIdleState()
                scheduleExpandAutoCollapse(6_500L)
            }
        }
    }

    /** Cicla Normal -> Vibrar -> Silencio -> Normal desde el Hub de la isla. */
    private fun cycleRingerModeFromHub() {
        val audio = getSystemService(AudioManager::class.java) ?: return
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                SoundControl.enableVibrate(this)
                lastKnownRingerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                gestureScope.launch {
                    SoundControl.cancelTimedSilence(this@EdgePanelService)
                    when (SoundControl.silenceWithoutRestrictingNotifications(this@EdgePanelService)) {
                        SilenceResult.SILENCED -> lastKnownRingerMode = AudioManager.RINGER_MODE_SILENT
                        SilenceResult.PERMISSION_REQUIRED -> {
                            Toast.makeText(this@EdgePanelService, "Autoriza a Nexo para activar silencio", Toast.LENGTH_SHORT).show()
                            startOverlayActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                        SilenceResult.FAILED ->
                            Toast.makeText(this@EdgePanelService, "No se pudo activar silencio", Toast.LENGTH_SHORT).show()
                    }
                    if (islandHubExpanded) refreshIslandIdleState()
                }
            }
            else -> {
                SoundControl.restoreNormalSound(this)
                lastKnownRingerMode = AudioManager.RINGER_MODE_NORMAL
            }
        }
    }

    private fun currentStopwatchMs(): Long =
        if (stopwatchRunning) stopwatchAccumulatedMs + (SystemClock.elapsedRealtime() - stopwatchStartElapsed)
        else stopwatchAccumulatedMs

    private fun isStopwatchActive(): Boolean = stopwatchRunning || stopwatchAccumulatedMs > 0L

    private fun formatMmSs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private fun scheduleExpandAutoCollapse(delayMs: Long) {
        gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
        gestureHandler.postDelayed(islandExpandTimeoutRunnable, delayMs)
    }

    private fun canShowIsland(): Boolean {
        if (!Prefs.dynamicIslandEnabled(this)) return false
        if (isLandscapeHidden()) return false
        if (calibrationPreviewActive) return true
        return !hostUiVisible && !panelUiVisible
    }

    private fun renderIsland(displayState: IslandDisplayState) {
        val view = islandView ?: return
        val params = islandParams ?: return
        if (displayState.mode == IslandMode.HIDDEN || !canShowIsland()) {
            view.hideImmediate()
            view.visibility = View.GONE
            gestureHandler.removeCallbacks(islandLiveTickRunnable)
            return
        }

        val finalState = if (calibrationPreviewActive) {
            displayState.copy(calibrationGuide = true)
        } else {
            displayState
        }

        val targetW = dp(view.desiredWidthDp(finalState.mode, finalState.satellite))
        val targetH = dp(view.desiredHeightDp(finalState.mode))
        val targetX = dp(Prefs.dynamicIslandHorizontalOffset(this))
        val targetY = dp(Prefs.dynamicIslandTopOffset(this))
        if (params.width != targetW || params.height != targetH || params.x != targetX || params.y != targetY) {
            params.width = targetW
            params.height = targetH
            params.x = targetX
            params.y = targetY
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
        }
        view.render(finalState)

        gestureHandler.removeCallbacks(islandLiveTickRunnable)
        when (finalState.mode) {
            IslandMode.EXPANDED_MEDIA, IslandMode.CALL, IslandMode.STOPWATCH ->
                gestureHandler.postDelayed(islandLiveTickRunnable, 1_000L)
            IslandMode.EXPANDED_HUB ->
                if (stopwatchRunning) gestureHandler.postDelayed(islandLiveTickRunnable, 1_000L)
            IslandMode.SILENT_TIMER ->
                gestureHandler.postDelayed(islandLiveTickRunnable, 30_000L)
            else -> Unit
        }
    }

    private fun showIslandAlert(displayState: IslandDisplayState, durationMs: Long = ISLAND_ALERT_DURATION_MS) {
        if (!canShowIsland()) return
        if (islandView == null) return
        islandTemporaryAlert = true
        islandMediaExpanded = false
        islandHubExpanded = false
        islandNotificationExpanded = false
        gestureHandler.removeCallbacks(islandAlertTimeoutRunnable)
        gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
        renderIsland(displayState)
        gestureHandler.postDelayed(islandAlertTimeoutRunnable, durationMs)
    }

    private fun hasActiveSilentTimer(): Boolean {
        val until = Prefs.silentUntilMillis(this)
        return until > 0L && until > System.currentTimeMillis()
    }

    private fun hasRecentNotification(): Boolean {
        val latest = ClipboardHistoryStore.latestNotification ?: return false
        return (System.currentTimeMillis() - latest.timestamp) in 0L..120_000L
    }

    private fun buildMediaDisplayState(expanded: Boolean, satellite: IslandSatellite, satIcon: Bitmap?): IslandDisplayState {
        val playing = currentPlayback!!
        val art = playing.albumArt ?: currentPlaybackIcon
        val duration = playing.durationMs
        val currentPos = if (playing.playing) {
            val delta = (SystemClock.elapsedRealtime() - playing.positionUpdateTimeMs).coerceAtLeast(0L)
            (playing.positionMs + (delta * playing.playbackSpeed).toLong()).coerceAtLeast(0L)
        } else {
            playing.positionMs
        }
        val clampedPos = if (duration > 0L) currentPos.coerceAtMost(duration) else currentPos
        val fraction = if (duration > 0L) (clampedPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

        return IslandDisplayState(
            mode = if (expanded) IslandMode.EXPANDED_MEDIA else IslandMode.COMPACT_MEDIA,
            title = playing.title,
            subtitle = playing.artist,
            icon = art,
            playing = playing.playing,
            progressFraction = fraction,
            progressLeftText = if (duration > 0L) formatMmSs(clampedPos) else "",
            progressRightText = if (duration > 0L) formatMmSs(duration) else "",
            satellite = if (expanded) IslandSatellite.NONE else satellite,
            satelliteIcon = if (expanded) null else satIcon
        )
    }

    private fun refreshIslandIdleState() {
        if (islandView == null) return
        if (!canShowIsland()) {
            renderIsland(IslandDisplayState(mode = IslandMode.HIDDEN))
            return
        }
        if (islandTemporaryAlert || islandNotificationExpanded) return

        // Modo calibración desde Ajustes: muestra la cápsula con aro guía sobre la cámara real
        if (calibrationPreviewActive) {
            renderIsland(
                IslandDisplayState(
                    mode = IslandMode.RINGER_MODE,
                    ringerMode = getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL,
                    calibrationGuide = true
                )
            )
            return
        }

        // 1. Hub rápido expandido
        if (islandHubExpanded) {
            val ringer = getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            renderIsland(
                IslandDisplayState(
                    mode = IslandMode.EXPANDED_HUB,
                    flashlightOn = torchEnabled,
                    silentOn = ringer == AudioManager.RINGER_MODE_SILENT,
                    ringerMode = ringer,
                    stopwatchRunning = stopwatchRunning,
                    stopwatchLabel = formatMmSs(currentStopwatchMs())
                )
            )
            return
        }

        val playing = currentPlayback?.takeIf {
            Prefs.dynamicIslandMedia(this) && (!mediaHiddenWhilePaused || it.playing)
        }
        val call = currentCall
        val swActive = isStopwatchActive()
        val silentTimer = hasActiveSilentTimer()
        val recentNotif = hasRecentNotification()

        // 2. Música expandida
        if (islandMediaExpanded && playing != null) {
            renderIsland(buildMediaDisplayState(expanded = true, satellite = IslandSatellite.NONE, satIcon = null))
            return
        }

        // 3. Llamada en curso
        if (call != null) {
            val (sat, satIcon) = when {
                playing != null -> IslandSatellite.MEDIA to (playing.albumArt ?: currentPlaybackIcon)
                swActive -> IslandSatellite.STOPWATCH to null
                silentTimer -> IslandSatellite.SILENT_TIMER to null
                else -> IslandSatellite.NONE to null
            }
            val elapsed = (System.currentTimeMillis() - call.startedAtMs).coerceAtLeast(0L)
            renderIsland(
                IslandDisplayState(
                    mode = IslandMode.CALL,
                    title = call.callerName,
                    badge = formatMmSs(elapsed),
                    icon = call.appIcon,
                    satellite = sat,
                    satelliteIcon = satIcon
                )
            )
            return
        }

        // 4. Música compacta (se oculta sola al pausarse)
        if (playing != null) {
            val (sat, satIcon) = when {
                swActive -> IslandSatellite.STOPWATCH to null
                silentTimer -> IslandSatellite.SILENT_TIMER to null
                recentNotif -> IslandSatellite.NOTIFICATION to ClipboardHistoryStore.latestNotification?.appIcon
                else -> IslandSatellite.NONE to null
            }
            renderIsland(buildMediaDisplayState(expanded = false, satellite = sat, satIcon = satIcon))
            return
        }

        // 5. Cronómetro activo
        if (swActive) {
            val (sat, satIcon) = when {
                silentTimer -> IslandSatellite.SILENT_TIMER to null
                recentNotif -> IslandSatellite.NOTIFICATION to ClipboardHistoryStore.latestNotification?.appIcon
                else -> IslandSatellite.NONE to null
            }
            renderIsland(
                IslandDisplayState(
                    mode = IslandMode.STOPWATCH,
                    title = if (stopwatchRunning) "Cronómetro" else "En pausa",
                    stopwatchRunning = stopwatchRunning,
                    stopwatchLabel = formatMmSs(currentStopwatchMs()),
                    satellite = sat,
                    satelliteIcon = satIcon
                )
            )
            return
        }

        // 6. Temporizador de silencio activo
        if (silentTimer) {
            val remainingMs = (Prefs.silentUntilMillis(this) - System.currentTimeMillis()).coerceAtLeast(0L)
            val mins = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            val label = if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                if (m == 0) "${h}h" else "${h}h ${m}m"
            } else {
                "${mins}m"
            }
            val (sat, satIcon) = if (recentNotif) {
                IslandSatellite.NOTIFICATION to ClipboardHistoryStore.latestNotification?.appIcon
            } else {
                IslandSatellite.NONE to null
            }
            renderIsland(
                IslandDisplayState(
                    mode = IslandMode.SILENT_TIMER,
                    title = label,
                    satellite = sat,
                    satelliteIcon = satIcon
                )
            )
            return
        }

        // 7. Cápsula negra OLED mínima sobre la cámara en reposo
        if (Prefs.dynamicIslandIdlePill(this)) {
            renderIsland(IslandDisplayState(mode = IslandMode.IDLE_PILL))
            return
        }

        renderIsland(IslandDisplayState(mode = IslandMode.HIDDEN))
    }

    private fun registerClipboardObserver() {
        if (clipboardListenerRegistered) return
        runCatching {
            getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipboardListener)
            clipboardListenerRegistered = true
        }
    }

    private fun registerScreenshotObserver() {
        if (!screenshotObserverRegistered) {
            runCatching {
                contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    screenshotObserver
                )
                screenshotObserverRegistered = true
            }
        }
        if (lastSeenScreenshotUri == null && ClipboardHistoryStore.hasScreenshotPermission(this)) {
            gestureScope.launch {
                val latest = withContext(Dispatchers.IO) {
                    ClipboardHistoryStore.queryLatestScreenshot(this@EdgePanelService)
                }
                if (latest != null) lastSeenScreenshotUri = latest.uri
            }
        }
    }

    private fun registerTorchCallback() {
        if (torchCallbackRegistered) return
        runCatching {
            val cm = getSystemService(CameraManager::class.java) ?: return
            torchCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facingBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && facingBack
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cm.registerTorchCallback(torchCallback, gestureHandler)
            torchCallbackRegistered = true
        }
    }

    private fun unregisterObservers() {
        if (clipboardListenerRegistered) {
            runCatching {
                getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipboardListener)
            }
            clipboardListenerRegistered = false
        }
        if (screenshotObserverRegistered) {
            runCatching { contentResolver.unregisterContentObserver(screenshotObserver) }
            screenshotObserverRegistered = false
        }
        if (torchCallbackRegistered) {
            runCatching { getSystemService(CameraManager::class.java)?.unregisterTorchCallback(torchCallback) }
            torchCallbackRegistered = false
        }
        if (systemReceiverRegistered) {
            runCatching { unregisterReceiver(systemReceiver) }
            systemReceiverRegistered = false
        }
        if (audioCallbackRegistered) {
            runCatching { getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(audioDeviceCallback) }
            audioCallbackRegistered = false
        }
    }

    private fun addHandle() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        removeHandle()

        val side = Prefs.panelSide(this)
        idleWidthPx = dp(Prefs.activationWidth(this))
        idleHeightPx = dp(Prefs.activationHeight(this)).coerceAtMost(
            (screenHeight() - dp(56)).coerceAtLeast(dp(48))
        )

        layoutParams = WindowManager.LayoutParams(
            idleWidthPx,
            idleHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (side == PanelSide.START) Gravity.START else Gravity.END) or
                Gravity.CENTER_VERTICAL
            x = dp(Prefs.activationInset(this@EdgePanelService))
            y = 0
        }

        layoutParams.y = (Prefs.panelYFraction(this@EdgePanelService) * maxOffset()).toInt()

        val view = EdgeHandleView(this).apply {
            this.side = side
            visibility = if (hostUiVisible || panelUiVisible) View.GONE else View.VISIBLE
            alpha = Prefs.activatorOpacity(this@EdgePanelService)
            isHapticFeedbackEnabled = true
            setOnTouchListener { _, event -> handleTouch(event) }
        }

        // No excluimos el gesto del sistema para que el gesto nativo "Atrás"
        // de Android funcione directamente sobre la barra sin interrupciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                view.systemGestureExclusionRects = emptyList()
            }
        }

        handleView = view

        runCatching {
            windowManager.addView(view, layoutParams)
        }.onFailure {
            handleView = null
            stopSelf()
        }
    }

    private fun removeHandle() {
        gestureHandler.removeCallbacks(holdAction)
        gestureHandler.removeCallbacks(collapseAlertRunnable)
        pullArmed = false
        val view = handleView ?: return
        runCatching { windowManager.removeView(view) }
        handleView = null
    }

    private fun setOverlayExpanded(expanded: Boolean) {
        val view = handleView ?: return
        val targetW = if (expanded) maxOf(idleWidthPx, dp(64)) else idleWidthPx
        val targetH = if (expanded) maxOf(idleHeightPx, dp(76)) else idleHeightPx
        if (layoutParams.width != targetW || layoutParams.height != targetH) {
            layoutParams.width = targetW
            layoutParams.height = targetH
            runCatching { windowManager.updateViewLayout(view, layoutParams) }
        }
    }

    private fun triggerCopyAlert() {
        if (hostUiVisible || panelUiVisible) return
        val now = System.currentTimeMillis()
        if (now - lastCopyAlertMs < 1_800L) return
        lastCopyAlertMs = now
        ClipboardHistoryStore.readSystemClipboard(applicationContext)
        if (Prefs.edgePulseOnCopy(this)) {
            showLiveAlert(EdgeLiveMode.CLIPBOARD, bitmap = null, accentColor = null)
        }
        if (Prefs.dynamicIslandEnabled(this)) {
            showIslandAlert(
                IslandDisplayState(
                    mode = IslandMode.CLIPBOARD,
                    title = "Texto copiado",
                    badge = "Abrir"
                )
            )
        }
    }

    private fun triggerScreenshotAlert(alreadyQueried: Boolean = false) {
        if (hostUiVisible || panelUiVisible) return
        val now = System.currentTimeMillis()
        if (now - lastShotAlertMs < 2_500L) return
        lastShotAlertMs = now
        if (!alreadyQueried && ClipboardHistoryStore.hasScreenshotPermission(this)) {
            gestureScope.launch {
                val latest = withContext(Dispatchers.IO) {
                    ClipboardHistoryStore.queryLatestScreenshot(this@EdgePanelService)
                }
                if (latest != null) lastSeenScreenshotUri = latest.uri
            }
        }
        if (Prefs.edgePulseOnScreenshot(this)) {
            showLiveAlert(EdgeLiveMode.SCREENSHOT, bitmap = null, accentColor = null)
        }
        if (Prefs.dynamicIslandEnabled(this)) {
            showIslandAlert(
                IslandDisplayState(
                    mode = IslandMode.SCREENSHOT,
                    title = "Captura lista",
                    badge = "Ver"
                )
            )
        }
    }

    private fun triggerNotificationAlert(pulse: NotificationPulse) {
        if (hostUiVisible || panelUiVisible) return
        if (Prefs.edgePulseOnNotification(this)) {
            showLiveAlert(
                mode = EdgeLiveMode.NOTIFICATION,
                bitmap = pulse.appIcon,
                accentColor = pulse.accentColor
            )
        }
        if (Prefs.dynamicIslandEnabled(this) && Prefs.dynamicIslandNotifications(this)) {
            val locked = isDeviceLocked()
            showIslandAlert(
                IslandDisplayState(
                    mode = IslandMode.NOTIFICATION,
                    title = pulse.appName,
                    subtitle = if (locked) "Nueva notificación" else pulse.title.ifBlank { "Toca: flotante · Sostén: leer" },
                    icon = pulse.appIcon,
                    accentColor = pulse.accentColor
                ),
                durationMs = 4_800L
            )
        }
    }

    private fun showLiveAlert(mode: EdgeLiveMode, bitmap: Bitmap?, accentColor: Int?) {
        val view = handleView ?: return
        if (view.visibility != View.VISIBLE) return
        if (pullArmed) return
        gestureHandler.removeCallbacks(collapseAlertRunnable)
        setOverlayExpanded(true)
        view.alpha = 1f
        view.showLiveAlert(mode, bitmap, accentColor)
        gestureHandler.postDelayed(collapseAlertRunnable, LIVE_ALERT_DURATION_MS)
    }

    private fun collapseLiveAlert() {
        gestureHandler.removeCallbacks(collapseAlertRunnable)
        val view = handleView ?: return
        view.collapseToIdle {
            setOverlayExpanded(false)
            resetHandleVisual()
        }
    }

    private fun handleLiveAlertTap(mode: EdgeLiveMode) {
        collapseLiveAlert()
        when (mode) {
            EdgeLiveMode.CLIPBOARD -> openQuickClip("clipboard")
            EdgeLiveMode.SCREENSHOT -> openQuickClip("screenshot")
            EdgeLiveMode.NOTIFICATION -> openLatestNotification()
            else -> executeGesture(EdgeGesture.SINGLE_TAP)
        }
    }

    /** Abre la última notificación recibida directamente en ventana flotante (freeform). */
    private fun openLatestNotification() {
        Haptics.perform(this)
        hideNotificationPreviewPopup()
        val pulse = ClipboardHistoryStore.latestNotification
        if (pulse == null) {
            openPanel()
            return
        }
        ItemLauncher.launchNotificationFloating(
            context = this,
            packageName = pulse.packageName,
            contentIntent = pulse.contentIntent
        )
    }

    private var notificationPreviewView: View? = null
    private val hideNotificationPreviewRunnable = Runnable { hideNotificationPreviewPopup() }

    /**
     * Muestra o expande una vista previa de la última notificación al dar doble toque
     * en el botón de notificación del borde.
     */
    private fun showNotificationPreview() {
        val pulse = ClipboardHistoryStore.latestNotification ?: return
        if (islandView != null && Prefs.dynamicIslandEnabled(this)) {
            expandLatestNotification()
            return
        }
        showNotificationPreviewPopup(pulse)
    }

    private fun showNotificationPreviewPopup(pulse: NotificationPulse) {
        hideNotificationPreviewPopup()
        if (!Settings.canDrawOverlays(this)) return

        val dm = resources.displayMetrics
        val cardWidth = (dm.widthPixels - dp(28)).coerceIn(dp(240), dp(390))
        val locked = isDeviceLocked()
        val spec = Prefs.panelStyle(this).visualSpec(light = false)
        val accent = pulse.accentColor ?: spec.primary.toArgb()

        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(10).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(android.graphics.Color.argb(242, 16, 18, 28))
                setStroke(dp(1), accent)
            }
            setOnClickListener { openLatestNotification() }
        }

        val headerRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (pulse.appIcon != null) {
            val icon = android.widget.ImageView(this).apply {
                setImageBitmap(pulse.appIcon)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, dp(8).toFloat())
                    }
                }
            }
            headerRow.addView(icon, android.widget.LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(8)
            })
        }

        val appTitle = android.widget.TextView(this).apply {
            text = pulse.appName
            textSize = 12f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        headerRow.addView(appTitle, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = android.widget.TextView(this).apply {
            text = "✕"
            textSize = 13f
            setTextColor(android.graphics.Color.argb(190, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener {
                Haptics.perform(this@EdgePanelService)
                hideNotificationPreviewPopup()
            }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        val titleText = if (locked) "Nueva notificación" else pulse.title
        if (titleText.isNotBlank()) {
            card.addView(
                android.widget.TextView(this).apply {
                    text = titleText
                    textSize = 14f
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                    setTextColor(android.graphics.Color.WHITE)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            )
        }

        val bodyText = when {
            locked -> "Contenido oculto mientras el teléfono está bloqueado"
            pulse.textPreview.isNotBlank() -> pulse.textPreview
            else -> "Toca para abrir en ventana flotante"
        }
        card.addView(
            android.widget.TextView(this).apply {
                text = bodyText
                textSize = 13f
                setTextColor(android.graphics.Color.argb(215, 232, 236, 245))
                maxLines = 5
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        )

        val actionsRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        fun makeActionChip(label: String, highlight: Boolean, onClick: () -> Unit): android.widget.TextView =
            android.widget.TextView(this).apply {
                text = label
                textSize = 12f
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                setTextColor(if (highlight) accent else android.graphics.Color.WHITE)
                setPadding(dp(11), dp(6), dp(11), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(
                        if (highlight) android.graphics.Color.argb(46, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent))
                        else android.graphics.Color.argb(32, 255, 255, 255)
                    )
                }
                setOnClickListener {
                    Haptics.perform(this@EdgePanelService)
                    onClick()
                }
            }

        if (!locked) {
            pulse.actions.take(2).forEach { action ->
                actionsRow.addView(
                    makeActionChip(action.title, highlight = false) {
                        hideNotificationPreviewPopup()
                        runCatching { action.intent.send() }
                    },
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                )
            }
        }

        actionsRow.addView(
            makeActionChip("Abrir flotante", highlight = true) {
                openLatestNotification()
            }
        )

        card.addView(
            actionsRow,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        val statusRes = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusPx = if (statusRes > 0) resources.getDimensionPixelSize(statusRes) else dp(32)
        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusPx + dp(10)
        }

        runCatching {
            card.alpha = 0f
            card.translationY = -dp(12).toFloat()
            windowManager.addView(card, params)
            notificationPreviewView = card
            card.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            gestureHandler.removeCallbacks(hideNotificationPreviewRunnable)
            gestureHandler.postDelayed(hideNotificationPreviewRunnable, 8_000L)
        }
    }

    private fun hideNotificationPreviewPopup() {
        gestureHandler.removeCallbacks(hideNotificationPreviewRunnable)
        notificationPreviewView?.let { runCatching { windowManager.removeView(it) } }
        notificationPreviewView = null
    }

    private fun prefetchPreviousApp(updatePreview: Boolean = false) {
        if (!UsageStore(this).hasUsageAccess()) {
            cachedPreviousTarget = null
            cachedPreviousIcon = null
            return
        }
        prefetchPreviousJob?.cancel()
        prefetchPreviousJob = gestureScope.launch {
            val target = withContext(Dispatchers.IO) {
                runCatching { LastAppNavigator.previousAppInfo(this@EdgePanelService) }.getOrNull()
            }
            val bmp = withContext(Dispatchers.IO) {
                runCatching { target?.icon?.toBitmap(84, 84) }.getOrNull()
            }
            cachedPreviousTarget = target
            cachedPreviousIcon = bmp
            if (updatePreview && pullArmed && !pullTriggered) {
                handleView?.updatePreviewIcon(bmp)
            }
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downRawX = event.rawX
            downRawY = event.rawY
            rejectedGesture = false
            doubleTapHandled = false
            pullArmed = false
            pullTriggered = false
            gestureHandler.removeCallbacks(holdAction)
            handleView?.alpha = 1f
            if (Prefs.edgeGestureAction(this, EdgeGesture.PULL_HOLD) == EdgeGestureAction.LAST_APP) {
                prefetchPreviousApp()
            }
        }
        tapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true

            MotionEvent.ACTION_MOVE -> {
                if (doubleTapHandled) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) rejectedGesture = true
                val inward = if (Prefs.panelSide(this) == PanelSide.START) dx else -dx
                val canHold = inward >= dp(30) && abs(dx) > abs(dy) * 1.5f
                val holdGestureAction = Prefs.edgeGestureAction(this, EdgeGesture.PULL_HOLD)

                if (!pullTriggered && canHold && holdGestureAction != EdgeGestureAction.NONE) {
                    if (!pullArmed) {
                        pullArmed = true
                        holdX = event.rawX; holdY = event.rawY
                        Haptics.perform(this)
                        if (holdGestureAction == EdgeGestureAction.LAST_APP) {
                            gestureHandler.removeCallbacks(collapseAlertRunnable)
                            setOverlayExpanded(true)
                            handleView?.showLastAppPreview(cachedPreviousIcon, HOLD_DELAY_MS)
                            if (cachedPreviousIcon == null) prefetchPreviousApp(updatePreview = true)
                        } else {
                            handleView?.scaleX = 1.5f
                        }
                        gestureHandler.postDelayed(holdAction, HOLD_DELAY_MS)
                    } else if (abs(event.rawX - holdX) > touchSlop * 2 || abs(event.rawY - holdY) > touchSlop * 2) {
                        holdX = event.rawX; holdY = event.rawY
                        gestureHandler.removeCallbacks(holdAction)
                        gestureHandler.postDelayed(holdAction, HOLD_DELAY_MS)
                        if (holdGestureAction == EdgeGestureAction.LAST_APP) {
                            handleView?.showLastAppPreview(cachedPreviousIcon, HOLD_DELAY_MS)
                        }
                    }
                } else if (!pullTriggered && pullArmed) {
                    pullArmed = false
                    gestureHandler.removeCallbacks(holdAction)
                    if (handleView?.liveMode == EdgeLiveMode.LAST_APP_PREVIEW) {
                        collapseLiveAlert()
                    } else {
                        handleView?.scaleX = 1f
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureHandler.removeCallbacks(holdAction)
                val wasPreview = handleView?.liveMode == EdgeLiveMode.LAST_APP_PREVIEW
                pullArmed = false
                if (wasPreview && !pullTriggered) {
                    collapseLiveAlert()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    !doubleTapHandled && rejectedGesture && !pullTriggered
                ) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val inward = if (Prefs.panelSide(this) == PanelSide.START) dx else -dx
                    if (abs(dy) >= dp(32) && abs(dy) > abs(dx) * 1.5f) {
                        collapseLiveAlert()
                        executeGesture(if (dy < 0) EdgeGesture.SWIPE_UP else EdgeGesture.SWIPE_DOWN)
                    } else if (inward >= dp(36) && abs(dx) > abs(dy) * 1.5f) {
                        collapseLiveAlert()
                        executeGesture(EdgeGesture.SWIPE_IN)
                    }
                }
                if (handleView?.isShowingLiveAlert() != true && !wasPreview) {
                    resetHandleVisual()
                }
                return true
            }
        }
        return false
    }

    private fun executeGesture(gesture: EdgeGesture) {
        val action = Prefs.edgeGestureAction(this, gesture)
        executeAction(action, gesture)
    }

    private fun executeAction(action: EdgeGestureAction, gesture: EdgeGesture) {
        when (action) {
            EdgeGestureAction.OPEN_PANEL -> openPanel()
            EdgeGestureAction.OPEN_SPOTLIGHT -> openSpotlight()
            EdgeGestureAction.OPEN_CONTROLS -> openControls("actions")
            EdgeGestureAction.OPEN_BRIGHTNESS -> openControls("brightness")
            EdgeGestureAction.OPEN_VOLUME -> openControls("volume")
            EdgeGestureAction.TOGGLE_SILENT -> toggleSilent()
            EdgeGestureAction.OPEN_CLIPBOARD -> openQuickClip("clipboard")
            EdgeGestureAction.LAST_APP -> openLastApp()
            EdgeGestureAction.SYSTEM_BACK -> {
                val ok = SplitScreenService.performBack()
                if (!ok && gesture != EdgeGesture.SWIPE_IN) {
                    promptAccessibilityNeeded()
                }
            }
            EdgeGestureAction.SYSTEM_HOME -> {
                Haptics.perform(this)
                if (!SplitScreenService.performHome()) {
                    startOverlayActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    )
                }
            }
            EdgeGestureAction.SYSTEM_RECENTS -> {
                Haptics.perform(this)
                if (!SplitScreenService.performRecents()) promptAccessibilityNeeded()
            }
            EdgeGestureAction.TAKE_SCREENSHOT -> {
                Haptics.perform(this)
                if (!SplitScreenService.performTakeScreenshot()) promptAccessibilityNeeded()
            }
            EdgeGestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            EdgeGestureAction.VOICE_ASSISTANT -> openVoiceAssistant()
            EdgeGestureAction.MINIMIZE_TO_BUBBLE -> minimizeCurrentAppToBubble()
            EdgeGestureAction.LAUNCH_APP -> launchConfiguredApp(gesture)
            EdgeGestureAction.NONE -> Unit
        }
    }

    private fun minimizeCurrentAppToBubble() {
        Haptics.perform(this, strong = true)
        if (!UsageStore(this).hasUsageAccess()) {
            val existing = FloatingWindowRegistry.entries.value.firstOrNull()
            if (existing != null) {
                FloatingWindowRegistry.markAllMinimized(this)
                sendToHomeBackground()
            } else {
                openControls("lastapp")
                Toast.makeText(
                    this,
                    "Autoriza el acceso al uso para detectar y minimizar la app actual a burbuja",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        gestureScope.launch {
            val active = withContext(Dispatchers.IO) {
                runCatching { LastAppNavigator.currentForegroundAppInfo(this@EdgePanelService) }.getOrNull()
            }
            if (active != null) {
                FloatingWindowRegistry.registerMinimized(this@EdgePanelService, active.packageName)
                sendToHomeBackground()
            } else {
                val existing = FloatingWindowRegistry.entries.value.firstOrNull()
                if (existing != null) {
                    FloatingWindowRegistry.markAllMinimized(this@EdgePanelService)
                    sendToHomeBackground()
                } else {
                    Toast.makeText(
                        this@EdgePanelService,
                        "No se detectó una app activa para minimizar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendToHomeBackground() {
        if (!SplitScreenService.performHome()) {
            startOverlayActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            )
        }
    }

    private fun openFloatingWindowMenu(startSplit: Boolean = false) {
        Haptics.perform(this)
        startOverlayActivity(
            Intent(this, FloatingWindowMenuActivity::class.java).apply {
                if (startSplit) putExtra(FloatingWindowMenuActivity.EXTRA_START_SPLIT, true)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
    }

    private fun openLastApp() {
        if (!UsageStore(this).hasUsageAccess()) {
            openControls("lastapp")
            return
        }
        val cached = cachedPreviousTarget
        if (cached != null) {
            cachedPreviousTarget = null
            cachedPreviousIcon = null
            runCatching { startOverlayActivity(cached.launchIntent) }.onFailure {
                Toast.makeText(this, "Android no permitió abrir la aplicación anterior", Toast.LENGTH_SHORT).show()
            }
            return
        }
        gestureScope.launch {
            val previous = withContext(Dispatchers.IO) {
                runCatching { LastAppNavigator.previousApp(this@EdgePanelService) }.getOrNull()
            }
            if (previous == null) {
                Toast.makeText(this@EdgePanelService, "No hay una aplicación anterior disponible en el historial", Toast.LENGTH_SHORT).show()
            } else runCatching { startOverlayActivity(previous) }.onFailure {
                Toast.makeText(this@EdgePanelService, "Android no permitió abrir la aplicación anterior", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSilent() {
        Haptics.perform(this)
        val audio = getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            if (SoundControl.restoreNormalSound(this)) {
                Toast.makeText(this, "Sonido normal", Toast.LENGTH_SHORT).show()
            }
        } else {
            gestureScope.launch {
                SoundControl.cancelTimedSilence(this@EdgePanelService)
                when (SoundControl.silenceWithoutRestrictingNotifications(this@EdgePanelService)) {
                    SilenceResult.SILENCED ->
                        Toast.makeText(this@EdgePanelService, "Teléfono en silencio", Toast.LENGTH_SHORT).show()
                    SilenceResult.PERMISSION_REQUIRED -> {
                        Toast.makeText(this@EdgePanelService, "Autoriza a Nexo para activar silencio", Toast.LENGTH_SHORT).show()
                        startOverlayActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                    SilenceResult.FAILED ->
                        Toast.makeText(this@EdgePanelService, "No se pudo activar el modo silencio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleFlashlight() {
        Haptics.perform(this)
        val cm = getSystemService(CameraManager::class.java)
        val id = torchCameraId ?: cm?.cameraIdList?.firstOrNull { camId ->
            runCatching {
                cm.getCameraCharacteristics(camId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        }
        if (cm == null || id == null) {
            Toast.makeText(this, "No se encontró linterna disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val next = !torchEnabled
        runCatching {
            cm.setTorchMode(id, next)
            torchEnabled = next
        }.onFailure {
            Toast.makeText(this, "No se pudo cambiar la linterna", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVoiceAssistant() {
        Haptics.perform(this)
        val voice = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/voice"))
            .setPackage("com.openai.chatgpt")
        val opened = runCatching { startOverlayActivity(voice); true }.getOrDefault(false)
        if (!opened) {
            val app = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
            if (app == null || runCatching { startOverlayActivity(app) }.isFailure) {
                val fallback = Intent(Intent.ACTION_VOICE_COMMAND)
                runCatching { startOverlayActivity(fallback) }.onFailure {
                    Toast.makeText(this, "No se encontró un asistente de voz instalado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchConfiguredApp(gesture: EdgeGesture) {
        Haptics.perform(this)
        val pkg = Prefs.edgeGestureApp(this, gesture)
        if (pkg.isNullOrBlank()) {
            Toast.makeText(this, "Elige una app para este gesto en Ajustes de Nexo", Toast.LENGTH_SHORT).show()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null || runCatching { startOverlayActivity(launch) }.isFailure) {
            Toast.makeText(this, "No se pudo abrir la aplicación asignada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptAccessibilityNeeded() {
        Toast.makeText(this, "Activa Nexo en Accesibilidad para usar esta acción", Toast.LENGTH_SHORT).show()
    }

    private fun resetHandleVisual() {
        handleView?.let {
            it.alpha = Prefs.activatorOpacity(this)
            it.translationX = 0f
            it.scaleX = 1f
        }
    }

    /** Lanza actividades desde el overlay asegurando compatibilidad sobre otras apps en Android 14–16. */
    private fun startOverlayActivity(rawIntent: Intent) {
        val intent = Intent(rawIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sent = runCatching {
                @Suppress("DEPRECATION")
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    ).toBundle()
                startActivity(intent, opts)
                true
            }.getOrDefault(false)
            if (sent) return
        }
        runCatching { startActivity(intent) }
    }

    private fun openPanel() {
        Haptics.perform(this)
        startOverlayActivity(
            Intent(this, SearchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
    }

    private fun openSpotlight() {
        Haptics.perform(this)
        startOverlayActivity(
            Intent(this, SpotlightSearchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
    }

    private fun openControls(mode: String) {
        Haptics.perform(this)
        startOverlayActivity(
            Intent(this, ActivatorControlsActivity::class.java)
                .putExtra("control", mode)
                .addFlags(
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        )
    }

    private fun openQuickClip(mode: String) {
        Haptics.perform(this)
        startOverlayActivity(
            Intent(this, QuickClipActivity::class.java)
                .putExtra("mode", mode)
                .addFlags(
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        )
    }

    private fun setHandleVisible(visible: Boolean) {
        if (!visible) {
            gestureHandler.removeCallbacks(collapseAlertRunnable)
            gestureHandler.removeCallbacks(islandAlertTimeoutRunnable)
            gestureHandler.removeCallbacks(islandExpandTimeoutRunnable)
            islandTemporaryAlert = false
            islandMediaExpanded = false
            islandHubExpanded = false
            islandNotificationExpanded = false
            handleView?.collapseToIdle { setOverlayExpanded(false) }
            if (calibrationPreviewActive) {
                refreshIslandIdleState()
            } else {
                renderIsland(IslandDisplayState(mode = IslandMode.HIDDEN))
            }
        } else {
            refreshIslandIdleState()
        }
        handleView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun maxOffset(): Int {
        val halfScreen = screenHeight() / 2f
        val halfHandle = idleHeightPx / 2f
        val margin = dp(28)
        return (halfScreen - halfHandle - margin).toInt().coerceAtLeast(0)
    }

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "nexo_edge_panel"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.stree.nexo.action.STOP_PANEL"
        private const val ACTION_REFRESH = "com.stree.nexo.action.REFRESH_PANEL"
        private const val ACTION_SYNC_BUBBLE = "com.stree.nexo.action.SYNC_FLOATING_BUBBLE"
        private const val ACTION_WIDGET = "com.stree.nexo.action.SHOW_WIDGET"

        /** Tiempo con el dedo quieto para ejecutar el gesto Jalar y sostener. */
        private const val HOLD_DELAY_MS = 460L

        /** Tiempo que permanece iluminada la barra viva tras un evento. */
        private const val LIVE_ALERT_DURATION_MS = 4_000L

        /** Tiempo que permanece activa una alerta en la Isla Dinámica. */
        private const val ISLAND_ALERT_DURATION_MS = 4_400L

        fun showWidget(context: Context, itemId: String) {
            if (Settings.canDrawOverlays(context)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EdgePanelService::class.java)
                        .setAction(ACTION_WIDGET)
                        .putExtra("item_id", itemId)
                )
            }
        }

        fun notifyCopyDetected(context: Context) {
            instance?.let { service ->
                service.gestureHandler.post { service.triggerCopyAlert() }
            }
        }

        fun notifyScreenshotDetected(context: Context) {
            instance?.let { service ->
                service.gestureHandler.post { service.triggerScreenshotAlert() }
            }
        }

        fun notifyNotificationPulse(context: Context, pulse: NotificationPulse) {
            instance?.let { service ->
                service.gestureHandler.post { service.triggerNotificationAlert(pulse) }
            }
        }

        fun notifyCallUpdate(context: Context, call: CallPulse?) {
            instance?.let { service ->
                service.gestureHandler.post {
                    service.currentCall = call
                    service.refreshIslandIdleState()
                }
            }
        }

        @Volatile
        private var instance: EdgePanelService? = null

        @Volatile
        private var hostUiVisible = false

        @Volatile
        private var panelUiVisible = false

        @Volatile
        private var calibrationPreviewActive = false

        fun setIslandCalibrationPreview(active: Boolean) {
            calibrationPreviewActive = active
            instance?.let { service ->
                service.gestureHandler.post {
                    service.islandTemporaryAlert = false
                    service.islandMediaExpanded = false
                    service.islandHubExpanded = false
                    service.islandNotificationExpanded = false
                    service.refreshIslandIdleState()
                }
            }
        }

        fun triggerIslandTestAlert(context: Context) {
            val service = instance ?: return
            service.gestureHandler.post {
                val prev = calibrationPreviewActive
                calibrationPreviewActive = true
                service.showIslandAlert(
                    IslandDisplayState(
                        mode = IslandMode.NOTIFICATION,
                        title = "Nexo · Prueba",
                        subtitle = "Texto seguro debajo de la cámara",
                        calibrationGuide = true
                    ),
                    durationMs = 4_000L
                )
                service.gestureHandler.postDelayed({
                    calibrationPreviewActive = prev
                    service.refreshIslandIdleState()
                }, 4_100L)
            }
        }

        fun setPanelOpen(open: Boolean) {
            panelUiVisible = open
            instance?.let { service ->
                service.gestureHandler.post {
                    service.setHandleVisible(!hostUiVisible && !panelUiVisible)
                    service.floatingBubble.setSuppressed(hostUiVisible || panelUiVisible)
                }
            }
        }

        /** Hides the edge handle and dynamic island while Nexo's own UI is in front. */
        fun setHandleVisible(visible: Boolean) {
            hostUiVisible = !visible
            instance?.let { service ->
                service.gestureHandler.post {
                    service.setHandleVisible(!hostUiVisible && !panelUiVisible)
                    service.floatingBubble.setSuppressed(hostUiVisible || panelUiVisible)
                }
            }
        }

        fun syncFloatingBubble(context: Context) {
            val running = instance
            if (running != null) {
                running.gestureHandler.post {
                    running.floatingBubble.setSuppressed(hostUiVisible || panelUiVisible)
                    running.floatingBubble.sync()
                }
                return
            }
            if (Prefs.floatingBubbleEnabled(context) &&
                Settings.canDrawOverlays(context) &&
                FloatingWindowRegistry.entries.value.isNotEmpty()
            ) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, EdgePanelService::class.java).setAction(ACTION_SYNC_BUBBLE)
                    )
                }
            }
        }

        fun minimizeToBackground(context: Context) {
            val running = instance
            if (running != null) {
                running.gestureHandler.post { running.sendToHomeBackground() }
                return
            }
            if (!SplitScreenService.performHome()) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, EdgePanelService::class.java)
            )
        }

        fun refresh(context: Context) {
            if (Prefs.isPanelEnabled(context) && Settings.canDrawOverlays(context)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EdgePanelService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }

        fun syncSilentTimer(context: Context) {
            instance?.let { service ->
                service.gestureHandler.post { service.rescheduleSilentTimer() }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EdgePanelService::class.java))
        }
    }
}
