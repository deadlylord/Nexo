package com.stree.nexo.panel

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class EdgePanelService : Service() {
    private val floatingWidget by lazy { com.stree.nexo.widget.FloatingWidgetController(this) }

    private lateinit var windowManager: WindowManager
    private var handleView: EdgeHandleView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var idleWidthPx = 0
    private var idleHeightPx = 0

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
            }
        }
    }

    private val collapseAlertRunnable = Runnable {
        collapseLiveAlert()
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
                collapseLiveAlert()
                executeGesture(EdgeGesture.DOUBLE_TAP)
                return true
            }
        }).apply { setIsLongpressEnabled(false) }
    }

    private val restoreSoundRunnable = Runnable {
        SoundControl.restoreIfExpired(applicationContext)
    }

    private fun rescheduleSilentTimer() {
        gestureHandler.removeCallbacks(restoreSoundRunnable)
        val until = Prefs.silentUntilMillis(this)
        if (until <= 0L) return
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            SoundControl.restoreIfExpired(applicationContext)
        } else {
            gestureHandler.postDelayed(restoreSoundRunnable, remaining)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundNotification()
        if (Prefs.isPanelEnabled(this)) addHandle()
        rescheduleSilentTimer()
        registerClipboardObserver()
        registerScreenshotObserver()
        registerTorchCallback()

        handleView?.visibility = if (hostUiVisible || panelUiVisible) View.GONE else View.VISIBLE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            addHandle()
            registerScreenshotObserver()
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
        unregisterObservers()
        gestureHandler.removeCallbacksAndMessages(null)
        gestureScope.cancel()
        floatingWidget.close()
        removeHandle()
        if (instance === this) instance = null
        super.onDestroy()
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
        if (!Prefs.edgePulseOnCopy(this)) return
        if (hostUiVisible || panelUiVisible) return
        val now = System.currentTimeMillis()
        if (now - lastCopyAlertMs < 1_800L) return
        lastCopyAlertMs = now
        ClipboardHistoryStore.readSystemClipboard(applicationContext)
        showLiveAlert(EdgeLiveMode.CLIPBOARD, bitmap = null, accentColor = null)
    }

    private fun triggerScreenshotAlert(alreadyQueried: Boolean = false) {
        if (!Prefs.edgePulseOnScreenshot(this)) return
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
        showLiveAlert(EdgeLiveMode.SCREENSHOT, bitmap = null, accentColor = null)
    }

    private fun triggerNotificationAlert(pulse: NotificationPulse) {
        if (!Prefs.edgePulseOnNotification(this)) return
        if (hostUiVisible || panelUiVisible) return
        showLiveAlert(
            mode = EdgeLiveMode.NOTIFICATION,
            bitmap = pulse.appIcon,
            accentColor = pulse.accentColor
        )
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

    private fun openLatestNotification() {
        Haptics.perform(this)
        val pulse = ClipboardHistoryStore.latestNotification
        if (pulse == null) {
            openPanel()
            return
        }
        val sent = pulse.contentIntent?.let { pending ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        ).toBundle()
                    pending.send(this, 0, null, null, null, null, opts)
                } else {
                    pending.send()
                }
                true
            }.getOrDefault(false)
        } ?: false

        if (!sent) {
            val launch = packageManager.getLaunchIntentForPackage(pulse.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch != null) {
                runCatching { startActivity(launch) }
            }
        }
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
                // Un desplazamiento nunca cuenta como toque simple.
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
            EdgeGestureAction.OPEN_CONTROLS -> openControls("actions")
            EdgeGestureAction.OPEN_BRIGHTNESS -> openControls("brightness")
            EdgeGestureAction.OPEN_VOLUME -> openControls("volume")
            EdgeGestureAction.TOGGLE_SILENT -> toggleSilent()
            EdgeGestureAction.OPEN_CLIPBOARD -> openQuickClip("clipboard")
            EdgeGestureAction.LAST_APP -> openLastApp()
            EdgeGestureAction.SYSTEM_BACK -> {
                // En SWIPE_IN no molestamos con aviso si Android ya interceptó el borde.
                val ok = SplitScreenService.performBack()
                if (!ok && gesture != EdgeGesture.SWIPE_IN) {
                    promptAccessibilityNeeded()
                }
            }
            EdgeGestureAction.SYSTEM_HOME -> {
                Haptics.perform(this)
                if (!SplitScreenService.performHome()) {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
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
            EdgeGestureAction.LAUNCH_APP -> launchConfiguredApp(gesture)
            EdgeGestureAction.NONE -> Unit
        }
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
            runCatching { startActivity(cached.launchIntent) }.onFailure {
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
            } else runCatching { startActivity(previous) }.onFailure {
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
                        runCatching {
                            startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
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
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { startActivity(voice); true }.getOrDefault(false)
        if (!opened) {
            val app = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (app == null || runCatching { startActivity(app) }.isFailure) {
                val fallback = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(fallback) }.onFailure {
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
        val launch = packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch == null || runCatching { startActivity(launch) }.isFailure) {
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

    private fun openPanel() {
        Haptics.perform(this)
        val intent = Intent(this, SearchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        runCatching { startActivity(intent) }
    }

    private fun openControls(mode: String) {
        Haptics.perform(this)
        runCatching {
            startActivity(
                Intent(this, ActivatorControlsActivity::class.java)
                    .putExtra("control", mode)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
            )
        }
    }

    private fun openQuickClip(mode: String) {
        Haptics.perform(this)
        runCatching {
            startActivity(
                Intent(this, QuickClipActivity::class.java)
                    .putExtra("mode", mode)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
            )
        }
    }

    private fun setHandleVisible(visible: Boolean) {
        if (!visible) {
            gestureHandler.removeCallbacks(collapseAlertRunnable)
            handleView?.collapseToIdle { setOverlayExpanded(false) }
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
        private const val ACTION_WIDGET = "com.stree.nexo.action.SHOW_WIDGET"

        /** Tiempo con el dedo quieto para ejecutar el gesto Jalar y sostener. */
        private const val HOLD_DELAY_MS = 460L

        /** Tiempo que permanece iluminada la barra viva tras un evento. */
        private const val LIVE_ALERT_DURATION_MS = 4_000L

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
            if (!Prefs.edgePulseOnCopy(context)) return
            instance?.let { service ->
                service.gestureHandler.post { service.triggerCopyAlert() }
            }
        }

        fun notifyScreenshotDetected(context: Context) {
            if (!Prefs.edgePulseOnScreenshot(context)) return
            instance?.let { service ->
                service.gestureHandler.post { service.triggerScreenshotAlert() }
            }
        }

        fun notifyNotificationPulse(context: Context, pulse: NotificationPulse) {
            if (!Prefs.edgePulseOnNotification(context)) return
            instance?.let { service ->
                service.gestureHandler.post { service.triggerNotificationAlert(pulse) }
            }
        }

        @Volatile
        private var instance: EdgePanelService? = null

        @Volatile
        private var hostUiVisible = false

        @Volatile
        private var panelUiVisible = false

        fun setPanelOpen(open: Boolean) {
            panelUiVisible = open
            instance?.let { service ->
                service.gestureHandler.post {
                    service.setHandleVisible(!hostUiVisible && !panelUiVisible)
                }
            }
        }

        /** Hides the edge handle while Nexo's own UI is in front. */
        fun setHandleVisible(visible: Boolean) {
            hostUiVisible = !visible
            instance?.let { service ->
                service.gestureHandler.post {
                    service.setHandleVisible(!hostUiVisible && !panelUiVisible)
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
