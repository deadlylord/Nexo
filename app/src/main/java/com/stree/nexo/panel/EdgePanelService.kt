package com.stree.nexo.panel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.stree.nexo.MainActivity
import com.stree.nexo.R
import com.stree.nexo.SearchActivity
import com.stree.nexo.ActivatorControlsActivity
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.Haptics
import kotlin.math.abs
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import com.stree.nexo.data.LastAppNavigator
import com.stree.nexo.data.UsageStore

class EdgePanelService : Service() {
    private val floatingWidget by lazy { com.stree.nexo.widget.FloatingWidgetController(this) }

    private lateinit var windowManager: WindowManager
    private var handleView: EdgeHandleView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

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
    private val holdAction = Runnable {
        if (pullArmed && !pullTriggered) {
            pullTriggered = true
            Haptics.perform(this, strong = true)
            if (!UsageStore(this).hasUsageAccess()) {
                openControls("lastapp")
            } else gestureScope.launch {
                val previous = withContext(Dispatchers.IO) { runCatching { LastAppNavigator.previousApp(this@EdgePanelService) }.getOrNull() }
                if (previous == null) {
                    android.widget.Toast.makeText(this@EdgePanelService, "No hay una aplicación anterior disponible en el historial", android.widget.Toast.LENGTH_SHORT).show()
                } else runCatching { startActivity(previous) }.onFailure {
                    android.widget.Toast.makeText(this@EdgePanelService, "Android no permitió abrir la aplicación anterior", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
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
                    openPanel()
                }
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                doubleTapHandled = true
                gestureHandler.removeCallbacks(holdAction)
                openControls("actions")
                return true
            }
        }).apply { setIsLongpressEnabled(false) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundNotification()
        if (Prefs.isPanelEnabled(this)) addHandle()

        handleView?.visibility = if (hostUiVisible || panelUiVisible) View.GONE else View.VISIBLE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) addHandle()
        if (intent?.action == ACTION_WIDGET) intent.getStringExtra("item_id")?.let { id ->
            runCatching { floatingWidget.show(id) }
                .onFailure { android.widget.Toast.makeText(this, "No se pudo abrir el widget", android.widget.Toast.LENGTH_SHORT).show() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        gestureHandler.removeCallbacksAndMessages(null)
        gestureScope.cancel()
        floatingWidget.close()
        removeHandle()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun addHandle() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        removeHandle()

        val side = Prefs.panelSide(this)
        val width = dp(Prefs.activationWidth(this))
        val height = dp(Prefs.activationHeight(this)).coerceAtMost(
            (screenHeight() - dp(56)).coerceAtLeast(dp(48))
        )

        layoutParams = WindowManager.LayoutParams(
            width,
            height,
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
        pullArmed = false
        val view = handleView ?: return
        runCatching { windowManager.removeView(view) }
        handleView = null
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
        }
        tapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true

            MotionEvent.ACTION_MOVE -> {
                if (doubleTapHandled) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                // Un desplazamiento nunca abre el panel principal.
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) rejectedGesture = true
                val inward = if (Prefs.panelSide(this) == PanelSide.START) dx else -dx
                val canHold = inward >= dp(30) && abs(dx) > abs(dy) * 1.5f
                if (!pullTriggered && canHold) {
                    if (!pullArmed) {
                        pullArmed = true
                        holdX = event.rawX; holdY = event.rawY
                        handleView?.scaleX = 1.5f
                        Haptics.perform(this)
                        gestureHandler.postDelayed(holdAction, HOLD_DELAY_MS)
                    } else if (abs(event.rawX - holdX) > touchSlop * 2 || abs(event.rawY - holdY) > touchSlop * 2) {
                        holdX = event.rawX; holdY = event.rawY
                        gestureHandler.removeCallbacks(holdAction)
                        gestureHandler.postDelayed(holdAction, HOLD_DELAY_MS)
                    }
                } else if (!pullTriggered) {
                    pullArmed = false
                    gestureHandler.removeCallbacks(holdAction)
                    handleView?.scaleX = 1f
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureHandler.removeCallbacks(holdAction)
                pullArmed = false
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    !doubleTapHandled && rejectedGesture && !pullTriggered
                ) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val inward = if (Prefs.panelSide(this) == PanelSide.START) dx else -dx
                    if (abs(dy) >= dp(32) && abs(dy) > abs(dx) * 1.5f) {
                        openControls(if (dy < 0) "brightness" else "volume")
                    } else if (inward >= dp(36) && abs(dx) > abs(dy) * 1.5f) {
                        // Respaldo: si la barra está separada del borde físico y Android no
                        // interceptó el gesto Atrás, delegamos en Accesibilidad si está activa.
                        SplitScreenService.performBack()
                    }
                }
                resetHandleVisual()
                return true
            }
        }
        return false
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
            startActivity(Intent(this, ActivatorControlsActivity::class.java)
                .putExtra("control", mode)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                ))
        }
    }

    private fun setHandleVisible(visible: Boolean) {
        handleView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun maxOffset(): Int {
        val halfScreen = screenHeight() / 2f
        val halfHandle = layoutParams.height / 2f
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

        /** Tiempo con el dedo quieto para volver a la app anterior. */
        private const val HOLD_DELAY_MS = 380L

        fun showWidget(context: Context, itemId: String) {
            if (Settings.canDrawOverlays(context)) {
                ContextCompat.startForegroundService(context, Intent(context, EdgePanelService::class.java)
                    .setAction(ACTION_WIDGET).putExtra("item_id", itemId))
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
            instance?.setHandleVisible(!hostUiVisible && !panelUiVisible)
        }

        /** Hides the edge handle while Nexo's own UI is in front. */
        fun setHandleVisible(visible: Boolean) {
            hostUiVisible = !visible
            instance?.setHandleVisible(!hostUiVisible && !panelUiVisible)
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
