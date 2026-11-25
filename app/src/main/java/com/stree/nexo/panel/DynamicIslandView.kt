package com.stree.nexo.panel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.ui.graphics.toArgb
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.theme.visualSpec
import kotlin.math.abs
import kotlin.math.sin

enum class IslandMode {
    HIDDEN,
    IDLE_PILL,
    COMPACT_MEDIA,
    EXPANDED_MEDIA,
    NOTIFICATION,
    EXPANDED_NOTIFICATION,
    EXPANDED_HUB,
    RINGER_MODE,
    CHARGING,
    BLUETOOTH,
    CALL,
    STOPWATCH,
    SILENT_TIMER,
    CLIPBOARD,
    SCREENSHOT
}

enum class IslandSatellite {
    NONE,
    MEDIA,
    STOPWATCH,
    SILENT_TIMER,
    CALL,
    NOTIFICATION
}

enum class IslandCalibrationMode {
    OFF,
    IDLE,
    COMPACT,
    EXPANDED
}

enum class IslandHubAction {
    SPOTLIGHT,
    FLASHLIGHT,
    SILENT,
    DO_NOT_DISTURB,
    SCREENSHOT,
    CLIPBOARD,
    STOPWATCH_TOGGLE,
    STOPWATCH_RESET
}

data class IslandDisplayState(
    val mode: IslandMode = IslandMode.HIDDEN,
    val title: String = "",
    val subtitle: String = "",
    val preview: String = "",
    val badge: String = "",
    val icon: Bitmap? = null,
    val accentColor: Int? = null,
    val secondaryAccentColor: Int? = null,
    val playing: Boolean = false,
    val progressFraction: Float = 0f,
    val progressLeftText: String = "",
    val progressRightText: String = "",
    val mediaDurationMs: Long = 0L,
    val lyricsAvailable: Boolean = false,
    val lyricsOpen: Boolean = false,
    val lyricsPrimary: String = "",
    val lyricsSecondary: String = "",
    val lyricsPaging: Boolean = false,
    val batteryPercent: Int = 0,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val doNotDisturbOn: Boolean = false,
    val notificationActionLabels: List<String> = emptyList(),
    val satellite: IslandSatellite = IslandSatellite.NONE,
    val satelliteIcon: Bitmap? = null,
    val flashlightOn: Boolean = false,
    val silentOn: Boolean = false,
    val stopwatchRunning: Boolean = false,
    val stopwatchLabel: String = "00:00",
    val hideIdleVisual: Boolean = false,
    val calibrationGuide: Boolean = false
)

interface IslandInteractionListener {
    fun onTapMain(mode: IslandMode)
    fun onDoubleTap(mode: IslandMode)
    fun onLongPress(mode: IslandMode)
    fun onSwipeDown(mode: IslandMode)
    fun onSwipeUpDismiss()
    fun onTapSatellite(satellite: IslandSatellite)
    fun onMediaPrev()
    fun onMediaPlayPause()
    fun onMediaNext()
    fun onMediaSeekTo(fraction: Float)
    fun onMediaSeekBy(deltaMs: Long)
    fun onMediaOpenApp()
    fun onMediaLyrics()
    fun onMediaLyricsPage(delta: Int)
    fun onMediaOpenFloating()
    fun onMediaSwipeSkip(next: Boolean)
    fun onNotificationOpenFloating()
    fun onNotificationQuickAction(index: Int)
    fun onHubAction(action: IslandHubAction)
    fun onOutsideTouch()
}

/**
 * Isla Dinámica OLED negra pura (#000000) fundida con la cámara perforada.
 * Respeta la zona segura del lente de la cámara en todos los modos (compactos
 * divididos izquierda/derecha y expandidos debajo de la cámara) para que el
 * hueco jamás tape el texto.
 */
class DynamicIslandView(context: Context) : View(context) {

    var listener: IslandInteractionListener? = null

    private var state: IslandDisplayState = IslandDisplayState()
    val mode: IslandMode get() = state.mode

    private var morphProgress = 1f
    private var pulsePhase = 0f
    private var eqPhase = 0f

    private var morphAnimator: ValueAnimator? = null
    private var loopAnimator: ValueAnimator? = null

    private val touchHandler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var gestureHandled = false
    private var swipedUp = false
    private var swipedDown = false
    private var swipedLeft = false
    private var swipedRight = false
    private var isSeeking = false
    private var seekPreviewFraction = 0f
    private var pendingSingleTap: Runnable? = null
    private var isSecondTap = false
    val isUserSeeking: Boolean get() = isSeeking

    private val longPressRunnable = Runnable {
        if (!gestureHandled && !swipedUp && !swipedDown && !swipedLeft && !swipedRight && !isSeeking && !isSecondTap) {
            gestureHandled = true
            cancelPendingSingleTap()
            animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            listener?.onLongPress(state.mode)
        }
    }

    private fun cancelPendingSingleTap() {
        pendingSingleTap?.let { touchHandler.removeCallbacks(it) }
        pendingSingleTap = null
    }

    // Hit-boxes interactivos
    private val pillRect = RectF()
    private val satelliteRect = RectF()
    private val rewindRect = RectF()
    private val prevRect = RectF()
    private val playRect = RectF()
    private val nextRect = RectF()
    private val forwardRect = RectF()
    private val mediaHeaderRect = RectF()
    private val mediaFloatRect = RectF()
    private val lyricsToggleRect = RectF()
    private val lyricsPreviousRect = RectF()
    private val lyricsNextRect = RectF()
    private var lyricsLayoutKey: List<Any>? = null
    private var lyricsLayout: StaticLayout? = null
    private val seekBarRect = RectF()
    private val seekBarHitRect = RectF()
    private val notifFloatBtnRect = RectF()
    private val notifActionRects = Array(2) { RectF() }
    private val hubTileRects = Array(6) { RectF() }
    private var backgroundAlpha = 255
    private var textScale = 1f
    private val hubStopwatchToggleRect = RectF()
    private val hubStopwatchResetRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 56, 189, 248)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 224, 228, 238)
        typeface = Typeface.DEFAULT
    }
    private val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val clipPath = Path()
    private val iconRect = RectF()

    private fun cameraTopSafeDp(): Int {
        val gap = Prefs.dynamicIslandCameraGap(context)
        if (gap <= 0) return 8
        return (Prefs.dynamicIslandIdleHeight(context) + 4).coerceIn(22, 46)
    }

    private fun hasSatellite(targetMode: IslandMode = state.mode, sat: IslandSatellite = state.satellite): Boolean =
        sat != IslandSatellite.NONE && (
            targetMode == IslandMode.COMPACT_MEDIA ||
                targetMode == IslandMode.CALL ||
                targetMode == IslandMode.STOPWATCH ||
                targetMode == IslandMode.SILENT_TIMER
            )

    private fun statusBarHeightDp(): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId <= 0) return 32
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        return (context.resources.getDimensionPixelSize(resId) / density).toInt().coerceIn(24, 54)
    }

    fun desiredWidthDp(
        targetMode: IslandMode = state.mode,
        sat: IslandSatellite = state.satellite,
        hideIdleVisual: Boolean = state.hideIdleVisual,
        calibrationGuide: Boolean = state.calibrationGuide,
        aboveStatusBar: Boolean = true
    ): Int {
        val camGap = Prefs.dynamicIslandCameraGap(context)
        val idleW = Prefs.dynamicIslandIdleWidth(context)
        val expW = Prefs.dynamicIslandExpandedWidth(context)
        val base = when (targetMode) {
            IslandMode.HIDDEN -> 1
            IslandMode.IDLE_PILL -> if (hideIdleVisual && !calibrationGuide) {
                idleW.coerceAtLeast(if (aboveStatusBar) 40 else 54)
            } else {
                idleW
            }
            IslandMode.COMPACT_MEDIA -> (camGap + 124).coerceIn(148, 252)
            IslandMode.RINGER_MODE -> (camGap + 168).coerceIn(184, 276)
            IslandMode.CHARGING -> (camGap + 148).coerceIn(170, 260)
            IslandMode.STOPWATCH -> (camGap + 150).coerceIn(170, 260)
            IslandMode.SILENT_TIMER -> (camGap + 156).coerceIn(174, 266)
            IslandMode.CALL -> (camGap + 158).coerceIn(176, 268)
            IslandMode.BLUETOOTH -> (expW * 0.78f).toInt().coerceIn(220, 310)
            IslandMode.CLIPBOARD -> (expW * 0.74f).toInt().coerceIn(210, 300)
            IslandMode.SCREENSHOT -> (expW * 0.76f).toInt().coerceIn(216, 304)
            IslandMode.NOTIFICATION -> if (Prefs.islandNotificationPill(context) && !calibrationGuide) {
                (camGap + 156).coerceIn(174, 266)
            } else {
                (expW * 0.90f).toInt().coerceIn(248, 356)
            }
            IslandMode.EXPANDED_MEDIA -> expW
            IslandMode.EXPANDED_NOTIFICATION -> expW
            IslandMode.EXPANDED_HUB -> expW
        }
        return if (hasSatellite(targetMode, sat)) base + 44 else base
    }

    fun desiredHeightDp(
        targetMode: IslandMode = state.mode,
        hideIdleVisual: Boolean = state.hideIdleVisual,
        calibrationGuide: Boolean = state.calibrationGuide,
        aboveStatusBar: Boolean = true,
        lyricsAvailable: Boolean = state.lyricsAvailable,
        lyricsOpen: Boolean = state.lyricsOpen
    ): Int {
        val idleH = Prefs.dynamicIslandIdleHeight(context)
        val topSafe = cameraTopSafeDp()
        val compactH = Prefs.dynamicIslandCompactHeight(context).coerceAtLeast(idleH)
        val expH = Prefs.dynamicIslandExpandedHeight(context)
        return when (targetMode) {
            IslandMode.HIDDEN -> 1
            IslandMode.IDLE_PILL -> if (hideIdleVisual && !calibrationGuide) {
                if (aboveStatusBar) {
                    idleH.coerceAtLeast(34)
                } else {
                    val reachBelowBar = statusBarHeightDp() - Prefs.dynamicIslandTopOffset(context) + 12
                    maxOf(idleH, reachBelowBar).coerceIn(36, 56)
                }
            } else {
                idleH
            }
            IslandMode.COMPACT_MEDIA,
            IslandMode.RINGER_MODE,
            IslandMode.CHARGING,
            IslandMode.STOPWATCH,
            IslandMode.SILENT_TIMER,
            IslandMode.CALL -> compactH
            IslandMode.BLUETOOTH,
            IslandMode.CLIPBOARD,
            IslandMode.SCREENSHOT -> topSafe + 40
            IslandMode.NOTIFICATION -> topSafe + if (Prefs.islandNotificationPill(context) && !calibrationGuide) 32 else 48
            IslandMode.EXPANDED_NOTIFICATION -> topSafe + (expH - 18).coerceAtLeast(108)
            IslandMode.EXPANDED_HUB -> topSafe + (expH - 12).coerceAtLeast(116)
            IslandMode.EXPANDED_MEDIA -> topSafe + expH + lyricsHeightDp(lyricsAvailable, lyricsOpen)
        }
    }

    private fun lyricsHeightDp(available: Boolean, open: Boolean): Int = when {
        !available -> 0
        open -> 112
        else -> 36
    }

    fun render(newState: IslandDisplayState) {
        backgroundAlpha = Prefs.dynamicIslandOpacity(context) * 255 / 100
        textScale = Prefs.dynamicIslandTextScale(context) / 100f
        val modeChanged = state.mode != newState.mode || state.satellite != newState.satellite
        if (modeChanged) {
            isSeeking = false
        }
        if (state.lyricsAvailable != newState.lyricsAvailable) {
            importantForAccessibility = if (newState.lyricsAvailable) {
                IMPORTANT_FOR_ACCESSIBILITY_YES
            } else {
                IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
        }
        state = newState

        if (modeChanged) {
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(0.78f, 1f).apply {
                duration = when (newState.mode) {
                    IslandMode.EXPANDED_MEDIA,
                    IslandMode.EXPANDED_NOTIFICATION,
                    IslandMode.EXPANDED_HUB -> 260L
                    else -> 210L
                }
                interpolator = OvershootInterpolator(0.7f)
                addUpdateListener {
                    morphProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            morphProgress = 1f
        }

        val needsLoop = newState.mode == IslandMode.CHARGING ||
            newState.mode == IslandMode.CALL ||
            ((newState.mode == IslandMode.COMPACT_MEDIA || newState.mode == IslandMode.EXPANDED_MEDIA) && newState.playing)
        if (needsLoop) startLoopAnimation() else stopLoopAnimation()
        invalidate()
    }

    fun hideImmediate() {
        state = IslandDisplayState(mode = IslandMode.HIDDEN)
        isSeeking = false
        touchHandler.removeCallbacks(longPressRunnable)
        stopLoopAnimation()
        morphAnimator?.cancel()
        invalidate()
    }

    private fun startLoopAnimation() {
        if (loopAnimator?.isRunning == true) return
        loopAnimator?.cancel()
        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1350L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                pulsePhase = v
                eqPhase = v * (Math.PI * 2.0).toFloat()
                invalidate()
            }
            start()
        }
    }

    private fun stopLoopAnimation() {
        loopAnimator?.cancel()
        loopAnimator = null
        pulsePhase = 0f
    }

    override fun onDetachedFromWindow() {
        touchHandler.removeCallbacksAndMessages(null)
        morphAnimator?.cancel()
        stopLoopAnimation()
        super.onDetachedFromWindow()
    }

    private fun computeSeekFraction(x: Float): Float {
        val span = seekBarRect.width()
        if (span <= 1f) return state.progressFraction.coerceIn(0f, 1f)
        return ((x - seekBarRect.left) / span).coerceIn(0f, 1f)
    }

    private fun supportsDoubleTap(mode: IslandMode): Boolean =
        mode != IslandMode.EXPANDED_MEDIA &&
            mode != IslandMode.EXPANDED_HUB &&
            mode != IslandMode.EXPANDED_NOTIFICATION &&
            mode != IslandMode.HIDDEN

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state.mode == IslandMode.HIDDEN) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                gestureHandled = false
                swipedUp = false
                swipedDown = false
                swipedLeft = false
                swipedRight = false
                isSeeking = state.mode == IslandMode.EXPANDED_MEDIA &&
                    state.mediaDurationMs > 0L &&
                    seekBarHitRect.contains(event.x, event.y)
                if (isSeeking) {
                    cancelPendingSingleTap()
                    isSecondTap = false
                    seekPreviewFraction = computeSeekFraction(event.x)
                    touchHandler.removeCallbacks(longPressRunnable)
                    invalidate()
                    return true
                }
                if (pendingSingleTap != null && supportsDoubleTap(state.mode)) {
                    cancelPendingSingleTap()
                    isSecondTap = true
                    touchHandler.removeCallbacks(longPressRunnable)
                } else {
                    isSecondTap = false
                    touchHandler.removeCallbacks(longPressRunnable)
                    touchHandler.postDelayed(longPressRunnable, 380L)
                }
                if (!(state.mode == IslandMode.IDLE_PILL && state.hideIdleVisual && !state.calibrationGuide)) {
                    animate().scaleX(0.975f).scaleY(0.975f).setDuration(75).start()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSeeking) {
                    seekPreviewFraction = computeSeekFraction(event.x)
                    invalidate()
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > dp(12f) || abs(dy) > dp(12f)) {
                    touchHandler.removeCallbacks(longPressRunnable)
                    cancelPendingSingleTap()
                    isSecondTap = false
                }
                if (dy < -dp(18f) && abs(dy) > abs(dx)) swipedUp = true
                if (dy > dp(18f) && abs(dy) > abs(dx)) swipedDown = true
                if (state.mode == IslandMode.COMPACT_MEDIA && abs(dx) > dp(24f) && abs(dx) > abs(dy) * 1.25f) {
                    if (dx < 0f) swipedLeft = true else swipedRight = true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchHandler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(105).start()
                if (isSeeking) {
                    val target = computeSeekFraction(event.x)
                    isSeeking = false
                    seekPreviewFraction = target
                    state = state.copy(progressFraction = target)
                    invalidate()
                    listener?.onMediaSeekTo(target)
                    return true
                }
                if (gestureHandled) {
                    isSecondTap = false
                    return true
                }
                if (swipedUp) {
                    isSecondTap = false
                    listener?.onSwipeUpDismiss()
                    return true
                }
                if (swipedDown) {
                    isSecondTap = false
                    listener?.onSwipeDown(state.mode)
                    return true
                }
                if (swipedLeft || swipedRight) {
                    isSecondTap = false
                    listener?.onMediaSwipeSkip(next = swipedLeft)
                    return true
                }
                if (isSecondTap) {
                    isSecondTap = false
                    listener?.onDoubleTap(state.mode)
                    return true
                }
                val tapX = event.x
                val tapY = event.y
                if (supportsDoubleTap(state.mode) && !(hasSatellite() && satelliteRect.contains(tapX, tapY))) {
                    val action = Runnable {
                        pendingSingleTap = null
                        handleTap(tapX, tapY)
                    }
                    pendingSingleTap = action
                    touchHandler.postDelayed(action, 240L)
                } else {
                    handleTap(tapX, tapY)
                }
                return true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                cancelPendingSingleTap()
                isSecondTap = false
                listener?.onOutsideTouch()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touchHandler.removeCallbacks(longPressRunnable)
                cancelPendingSingleTap()
                isSecondTap = false
                isSeeking = false
                animate().scaleX(1f).scaleY(1f).setDuration(105).start()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!state.lyricsAvailable) return
        info.contentDescription = listOf(state.title, state.subtitle, state.lyricsPrimary, state.lyricsSecondary)
            .filter(String::isNotBlank).joinToString("\n")
        val toggle = if (state.lyricsOpen) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND
        }
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            toggle.id, if (state.lyricsOpen) "Ocultar letra" else "Buscar letra en LRCLIB"
        ))
        if (state.lyricsPaging) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id, "Versos anteriores"
            ))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id, "Versos siguientes"
            ))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val target = listener
        if (target != null && state.lyricsAvailable) {
            when {
                action == AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id && !state.lyricsOpen ->
                    target.onMediaLyrics()
                action == AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id && state.lyricsOpen ->
                    target.onMediaLyrics()
                action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id && state.lyricsPaging ->
                    target.onMediaLyricsPage(-1)
                action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id && state.lyricsPaging ->
                    target.onMediaLyricsPage(1)
                else -> return super.performAccessibilityAction(action, arguments)
            }
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun handleTap(x: Float, y: Float) {
        if (hasSatellite() && satelliteRect.contains(x, y)) {
            listener?.onTapSatellite(state.satellite)
            return
        }
        when (state.mode) {
            IslandMode.EXPANDED_MEDIA -> when {
                state.lyricsAvailable && lyricsToggleRect.contains(x, y) -> listener?.onMediaLyrics()
                state.lyricsPaging && lyricsPreviousRect.contains(x, y) -> listener?.onMediaLyricsPage(-1)
                state.lyricsPaging && lyricsNextRect.contains(x, y) -> listener?.onMediaLyricsPage(1)
                mediaFloatRect.contains(x, y) -> listener?.onMediaOpenFloating()
                rewindRect.contains(x, y) -> listener?.onMediaSeekBy(-10_000L)
                prevRect.contains(x, y) -> listener?.onMediaPrev()
                playRect.contains(x, y) -> listener?.onMediaPlayPause()
                nextRect.contains(x, y) -> listener?.onMediaNext()
                forwardRect.contains(x, y) -> listener?.onMediaSeekBy(10_000L)
                mediaHeaderRect.contains(x, y) -> listener?.onMediaOpenApp()
                else -> listener?.onTapMain(state.mode)
            }
            IslandMode.EXPANDED_NOTIFICATION -> when {
                notifFloatBtnRect.contains(x, y) -> listener?.onNotificationOpenFloating()
                state.notificationActionLabels.isNotEmpty() && notifActionRects[0].contains(x, y) ->
                    listener?.onNotificationQuickAction(0)
                state.notificationActionLabels.size > 1 && notifActionRects[1].contains(x, y) ->
                    listener?.onNotificationQuickAction(1)
                else -> listener?.onNotificationOpenFloating()
            }
            IslandMode.EXPANDED_HUB -> {
                val actions = listOf(
                    IslandHubAction.SPOTLIGHT,
                    IslandHubAction.FLASHLIGHT,
                    IslandHubAction.SILENT,
                    IslandHubAction.SCREENSHOT,
                    IslandHubAction.CLIPBOARD,
                    IslandHubAction.DO_NOT_DISTURB
                )
                for (i in actions.indices) {
                    if (hubTileRects[i].contains(x, y)) {
                        listener?.onHubAction(actions[i])
                        return
                    }
                }
                when {
                    hubStopwatchToggleRect.contains(x, y) -> listener?.onHubAction(IslandHubAction.STOPWATCH_TOGGLE)
                    hubStopwatchResetRect.contains(x, y) -> listener?.onHubAction(IslandHubAction.STOPWATCH_RESET)
                    else -> listener?.onTapMain(state.mode)
                }
            }
            else -> listener?.onTapMain(state.mode)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (state.mode == IslandMode.HIDDEN || width <= 2 || height <= 2) return

        // En reposo oculto detrás de la cámara: imperceptible (alpha 1/255) para que InputDispatcher
        // siga entregando toques sobre la cámara en TODAS las apps sin mostrar ninguna barra negra extra.
        if (state.mode == IslandMode.IDLE_PILL && state.hideIdleVisual && !state.calibrationGuide) {
            pillRect.set(0f, 0f, width.toFloat(), height.toFloat())
            satelliteRect.setEmpty()
            bgPaint.color = Color.argb(1, 0, 0, 0)
            canvas.drawRoundRect(pillRect, pillRect.height() / 2f, pillRect.height() / 2f, bgPaint)
            return
        }

        val spec = Prefs.panelStyle(context).visualSpec(light = false)
        val primaryArgb = spec.primary.toArgb()
        val secondaryArgb = spec.secondary.toArgb()
        val accentArgb = state.accentColor ?: primaryArgb
        val secondaryAccentArgb = state.secondaryAccentColor ?: secondaryArgb

        val showSat = hasSatellite()
        val satDiameter = dp(36f).coerceAtMost(height.toFloat())
        val satGap = dp(8f)

        val scale = morphProgress.coerceIn(0.72f, 1.03f)
        val fullMainW = if (showSat) (width - satDiameter - satGap).coerceAtLeast(dp(64f)) else width.toFloat()
        val drawW = (fullMainW * scale).coerceAtMost(fullMainW)
        val drawH = (height * (0.86f + 0.14f * scale)).coerceAtMost(height.toFloat())

        val totalRowW = if (showSat) drawW + satGap + satDiameter else drawW
        val startX = (width - totalRowW) / 2f
        val top = 0f

        pillRect.set(startX, top, startX + drawW, top + drawH)
        val cornerRadius = when (state.mode) {
            IslandMode.EXPANDED_MEDIA,
            IslandMode.EXPANDED_NOTIFICATION,
            IslandMode.EXPANDED_HUB -> dp(30f)
            IslandMode.NOTIFICATION,
            IslandMode.BLUETOOTH,
            IslandMode.CLIPBOARD,
            IslandMode.SCREENSHOT -> dp(24f)
            else -> drawH / 2f
        }

        // Cápsula principal: Negro OLED puro (#000000) o semitransparente durante guía de calibración
        bgPaint.color = if (state.calibrationGuide && state.mode == IslandMode.IDLE_PILL && state.hideIdleVisual) {
            Color.argb(170, 0, 0, 0)
        } else {
            Color.argb(backgroundAlpha, 0, 0, 0)
        }
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)

        // Burbuja satélite independiente (modo burbuja dividida estilo iPhone)
        if (showSat) {
            val satLeft = pillRect.right + satGap
            val satTop = pillRect.centerY() - satDiameter / 2f
            satelliteRect.set(satLeft, satTop, satLeft + satDiameter, satTop + satDiameter)
            bgPaint.color = Color.argb(backgroundAlpha, 0, 0, 0)
            canvas.drawOval(satelliteRect, bgPaint)
            drawSatelliteContent(canvas, accentArgb, secondaryAccentArgb)
        } else {
            satelliteRect.setEmpty()
        }

        val camHalfPx = dp(Prefs.dynamicIslandCameraGap(context).toFloat()) / 2f
        val topSafePx = dp(cameraTopSafeDp().toFloat())

        when (state.mode) {
            IslandMode.IDLE_PILL -> Unit
            IslandMode.COMPACT_MEDIA -> drawCompactMedia(canvas, accentArgb, secondaryAccentArgb, camHalfPx)
            IslandMode.RINGER_MODE -> drawRingerMode(canvas, primaryArgb, camHalfPx)
            IslandMode.CHARGING -> drawCharging(canvas, camHalfPx)
            IslandMode.CALL -> drawCall(canvas, camHalfPx)
            IslandMode.STOPWATCH -> drawStopwatch(canvas, camHalfPx)
            IslandMode.SILENT_TIMER -> drawSilentTimer(canvas, primaryArgb, camHalfPx)
            IslandMode.BLUETOOTH -> drawBluetooth(canvas, topSafePx)
            IslandMode.CLIPBOARD -> drawActionAlert(canvas, primaryArgb, topSafePx, isScreenshot = false)
            IslandMode.SCREENSHOT -> drawActionAlert(canvas, secondaryArgb, topSafePx, isScreenshot = true)
            IslandMode.NOTIFICATION -> drawCompactNotification(canvas, accentArgb, topSafePx)
            IslandMode.EXPANDED_MEDIA -> drawExpandedMedia(canvas, accentArgb, secondaryAccentArgb, topSafePx)
            IslandMode.EXPANDED_NOTIFICATION -> drawExpandedNotification(canvas, primaryArgb, accentArgb, topSafePx)
            IslandMode.EXPANDED_HUB -> drawExpandedHub(canvas, primaryArgb, secondaryArgb, topSafePx)
            IslandMode.HIDDEN -> Unit
        }

        // Aro guía azul translúcido durante calibración en Ajustes para alinear exactamente sobre la cámara física
        if (state.calibrationGuide) {
            guidePaint.strokeWidth = dp(1.4f)
            val camR = (camHalfPx * 0.68f).coerceIn(dp(6f), dp(18f))
            val camCy = dp(Prefs.dynamicIslandIdleHeight(context).toFloat()) / 2f
            canvas.drawCircle(pillRect.centerX(), camCy, camR, guidePaint)
            canvas.drawLine(pillRect.centerX() - dp(4f), camCy, pillRect.centerX() + dp(4f), camCy, guidePaint)
            canvas.drawLine(pillRect.centerX(), camCy - dp(4f), pillRect.centerX(), camCy + dp(4f), guidePaint)
            canvas.drawRoundRect(
                pillRect.left + dp(1f),
                pillRect.top + dp(1f),
                pillRect.right - dp(1f),
                pillRect.bottom - dp(1f),
                cornerRadius,
                cornerRadius,
                guidePaint
            )
        }
    }

    private fun drawSatelliteContent(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val cx = satelliteRect.centerX()
        val cy = satelliteRect.centerY()
        when (state.satellite) {
            IslandSatellite.MEDIA -> {
                val bmp = state.satelliteIcon
                if (bmp != null) {
                    drawCircularBitmap(canvas, bmp, cx, cy, dp(12f))
                } else {
                    drawEqualizer(canvas, cx - dp(6.5f), cy, primaryArgb, secondaryArgb, playing = true)
                }
            }
            IslandSatellite.STOPWATCH -> drawClockGlyph(canvas, cx, cy, dp(7.5f), COLOR_AMBER)
            IslandSatellite.SILENT_TIMER -> drawBellOffGlyph(canvas, cx, cy, dp(7.2f), primaryArgb)
            IslandSatellite.CALL -> drawPhoneGlyph(canvas, cx, cy, dp(7.5f), COLOR_GREEN)
            IslandSatellite.NOTIFICATION -> {
                val bmp = state.satelliteIcon
                if (bmp != null) drawCircularBitmap(canvas, bmp, cx, cy, dp(12f))
                else drawFloatingWindowGlyph(canvas, cx, cy, dp(7f), primaryArgb)
            }
            IslandSatellite.NONE -> Unit
        }
    }

    // ---- Modos compactos de 1 fila con zona central libre para la cámara ----

    private fun drawCompactMedia(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int, camHalfPx: Float) {
        val cy = pillRect.centerY()
        val leftZoneEnd = pillRect.centerX() - camHalfPx
        val rightZoneStart = pillRect.centerX() + camHalfPx

        // Ala izquierda: carátula circular o nota musical
        val iconR = dp(11.5f)
        val iconCx = ((pillRect.left + dp(6f) + leftZoneEnd) / 2f).coerceAtLeast(pillRect.left + dp(16f))
        val bmp = state.icon
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, cy, iconR)
        } else {
            chipPaint.color = withAlpha(primaryArgb, 50)
            canvas.drawCircle(iconCx, cy, iconR, chipPaint)
            drawMusicNoteGlyph(canvas, iconCx, cy, dp(6f), primaryArgb)
        }

        // Ala derecha: ecualizador animado (jamás cruza el lente de la cámara)
        val eqWidth = dp(13.5f)
        val eqX = ((rightZoneStart + pillRect.right - dp(6f)) / 2f - eqWidth / 2f)
            .coerceAtMost(pillRect.right - dp(12f) - eqWidth)
        drawEqualizer(canvas, eqX, cy, primaryArgb, secondaryArgb, playing = state.playing)
    }

    private fun drawRingerMode(canvas: Canvas, primaryArgb: Int, camHalfPx: Float) {
        val cy = pillRect.centerY()
        val leftZoneEnd = pillRect.centerX() - camHalfPx
        val rightZoneStart = pillRect.centerX() + camHalfPx

        val (label, tint) = when (state.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silencio" to COLOR_CORAL
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrar" to COLOR_AMBER
            else -> "Sonido" to COLOR_GREEN
        }

        val iconCx = ((pillRect.left + dp(8f) + leftZoneEnd) / 2f).coerceAtLeast(pillRect.left + dp(17f))
        chipPaint.color = withAlpha(tint, 44)
        canvas.drawCircle(iconCx, cy, dp(11f), chipPaint)
        when (state.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> drawBellOffGlyph(canvas, iconCx, cy, dp(6.4f), tint)
            AudioManager.RINGER_MODE_VIBRATE -> drawVibrateGlyph(canvas, iconCx, cy, dp(6.6f), tint)
            else -> drawSoundOnGlyph(canvas, iconCx, cy, dp(6.6f), tint)
        }

        val rightCenter = (rightZoneStart + pillRect.right - dp(8f)) / 2f
        val maxW = (pillRect.right - dp(10f) - rightZoneStart).coerceAtLeast(dp(36f))
        badgePaint.textSize = dp(11.8f)
        badgePaint.color = tint
        val text = TextUtils.ellipsize(label, badgePaint, maxW, TextUtils.TruncateAt.END).toString()
        val base = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(text, rightCenter, base, badgePaint)
    }

    private fun drawCharging(canvas: Canvas, camHalfPx: Float) {
        val cy = pillRect.centerY()
        val leftZoneEnd = pillRect.centerX() - camHalfPx
        val rightZoneStart = pillRect.centerX() + camHalfPx

        val boltCx = ((pillRect.left + dp(8f) + leftZoneEnd) / 2f).coerceAtLeast(pillRect.left + dp(17f))
        val pulseAlpha = (165 + 90 * sin(pulsePhase * Math.PI * 2.0)).toInt().coerceIn(95, 255)
        drawBoltGlyph(canvas, boltCx, cy, dp(8f), withAlpha(COLOR_GREEN, pulseAlpha))

        val pctText = "${state.batteryPercent.coerceIn(0, 100)}%"
        val rightCenter = (rightZoneStart + pillRect.right - dp(8f)) / 2f
        badgePaint.textSize = dp(12.2f)
        badgePaint.color = COLOR_GREEN
        val base = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(pctText, rightCenter, base, badgePaint)
    }

    private fun drawCall(canvas: Canvas, camHalfPx: Float) {
        val cy = pillRect.centerY()
        val leftZoneEnd = pillRect.centerX() - camHalfPx
        val rightZoneStart = pillRect.centerX() + camHalfPx

        val iconCx = ((pillRect.left + dp(8f) + leftZoneEnd) / 2f).coerceAtLeast(pillRect.left + dp(17f))
        val pulseAlpha = (170 + 85 * sin(pulsePhase * Math.PI * 2.0)).toInt().coerceIn(100, 255)
        chipPaint.color = withAlpha(COLOR_GREEN, 48)
        canvas.drawCircle(iconCx, cy, dp(11f), chipPaint)
        drawPhoneGlyph(canvas, iconCx, cy, dp(6.5f), withAlpha(COLOR_GREEN, pulseAlpha))

        val timeText = state.badge.ifBlank { "00:00" }
        val rightCenter = (rightZoneStart + pillRect.right - dp(8f)) / 2f
        badgePaint.textSize = dp(11.8f)
        badgePaint.color = COLOR_GREEN
        val base = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(timeText, rightCenter, base, badgePaint)
    }

    private fun drawStopwatch(canvas: Canvas, camHalfPx: Float) {
        val cy = pillRect.centerY()
        val leftZoneEnd = pillRect.centerX() - camHalfPx
        val rightZoneStart = pillRect.centerX() + camHalfPx

        val iconCx = ((pillRect.left + dp(8f) + leftZoneEnd) / 2f).coerceAtLeast(pillRect.left + dp(17f))
        drawClockGlyph(canvas, iconCx, cy, dp(7f), COLOR_AMBER)

        val rightCenter = (rightZoneStart + pillRect.right - dp(8f)) / 2f
        badgePaint.textSize = dp(12f)
        badgePaint.color = COLOR_AMBER
        val base = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(state.stopwatchLabel, rightCenter, base, badgePaint)
    }

    private fun drawSilentTimer(canvas: Canvas, accentArgb: Int, camHalfPx: Float) {
        val cy = pillRect.centerY()
        val leftZoneEnd = pillRect.centerX() - camHalfPx
        val rightZoneStart = pillRect.centerX() + camHalfPx

        val iconCx = ((pillRect.left + dp(8f) + leftZoneEnd) / 2f).coerceAtLeast(pillRect.left + dp(17f))
        drawBellOffGlyph(canvas, iconCx, cy, dp(6.8f), accentArgb)

        val rightCenter = (rightZoneStart + pillRect.right - dp(8f)) / 2f
        val maxW = (pillRect.right - dp(10f) - rightZoneStart).coerceAtLeast(dp(36f))
        badgePaint.textSize = dp(11.5f)
        badgePaint.color = Color.WHITE
        val text = TextUtils.ellipsize(state.title, badgePaint, maxW, TextUtils.TruncateAt.END).toString()
        val base = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(text, rightCenter, base, badgePaint)
    }

    // ---- Modos con texto debajo de la zona de cámara (0 solapamiento con el lente) ----

    private fun drawBluetooth(canvas: Canvas, topSafePx: Float) {
        val cy = topSafePx + (pillRect.bottom - topSafePx) / 2f
        val iconCx = pillRect.left + dp(20f)
        drawBluetoothGlyph(canvas, iconCx, cy, dp(7.5f), COLOR_BLUE)

        val badgeW = dp(62f)
        val textLeft = iconCx + dp(14f)
        val maxW = (pillRect.right - badgeW - dp(12f) - textLeft).coerceAtLeast(dp(40f))
        titlePaint.textSize = dp(12f) * textScale
        titlePaint.color = Color.WHITE
        val name = TextUtils.ellipsize(
            state.title.ifBlank { "Bluetooth" },
            titlePaint,
            maxW,
            TextUtils.TruncateAt.END
        ).toString()
        val base = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(name, textLeft, base, titlePaint)

        subtitlePaint.textSize = dp(10.5f)
        subtitlePaint.color = COLOR_BLUE
        val tag = "Conectado"
        val tw = subtitlePaint.measureText(tag)
        canvas.drawText(tag, pillRect.right - dp(14f) - tw, base, subtitlePaint)
    }

    private fun drawActionAlert(canvas: Canvas, accentArgb: Int, topSafePx: Float, isScreenshot: Boolean) {
        val cy = topSafePx + (pillRect.bottom - topSafePx) / 2f
        val iconCx = pillRect.left + dp(20f)
        if (isScreenshot) {
            drawScreenshotGlyph(canvas, iconCx, cy, dp(7f), accentArgb)
        } else {
            drawClipboardGlyph(canvas, iconCx, cy, dp(7f), accentArgb)
        }

        val chipW = dp(46f)
        val chipH = dp(22f)
        val chipRect = RectF(
            pillRect.right - dp(12f) - chipW,
            cy - chipH / 2f,
            pillRect.right - dp(12f),
            cy + chipH / 2f
        )
        chipPaint.color = withAlpha(accentArgb, 78)
        canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, chipPaint)
        badgePaint.textSize = dp(10.5f)
        badgePaint.color = Color.WHITE
        val chipBaseline = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(state.badge.ifBlank { "Abrir" }, chipRect.centerX(), chipBaseline, badgePaint)

        val textLeft = iconCx + dp(16f)
        val textMaxW = (chipRect.left - dp(8f) - textLeft).coerceAtLeast(dp(30f))
        titlePaint.textSize = dp(12f) * textScale
        titlePaint.color = Color.WHITE
        val label = TextUtils.ellipsize(state.title, titlePaint, textMaxW, TextUtils.TruncateAt.END).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(label, textLeft, baseline, titlePaint)
    }

    private fun drawCompactNotification(canvas: Canvas, accentArgb: Int, topSafePx: Float) {
        val compact = Prefs.islandNotificationPill(context) && !state.calibrationGuide
        val cy = topSafePx + (pillRect.bottom - topSafePx) / 2f
        val iconR = dp(if (compact) 11f else 15.5f)
        val iconCx = pillRect.left + dp(14f) + iconR
        val bmp = state.icon
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, cy, iconR)
        } else {
            chipPaint.color = withAlpha(accentArgb, 60)
            canvas.drawCircle(iconCx, cy, iconR, chipPaint)
        }

        val pipR = dp(13f)
        val pipCx = pillRect.right - dp(14f) - pipR
        chipPaint.color = Color.argb(28, 255, 255, 255)
        canvas.drawCircle(pipCx, cy, pipR, chipPaint)
        drawFloatingWindowGlyph(canvas, pipCx, cy, dp(6.5f), accentArgb)

        val textLeft = iconCx + iconR + dp(10f)
        val textMaxW = (pipCx - pipR - dp(8f) - textLeft).coerceAtLeast(dp(40f))

        titlePaint.textSize = dp(12.5f) * textScale
        titlePaint.color = Color.WHITE
        val topLine = TextUtils.ellipsize(
            state.title.ifBlank { "Notificación" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        val baseline = if (compact) cy - (titlePaint.descent() + titlePaint.ascent()) / 2f else cy - dp(2.5f)
        canvas.drawText(topLine, textLeft, baseline, titlePaint)
        if (compact) return

        subtitlePaint.textSize = dp(11f) * textScale
        subtitlePaint.color = Color.argb(185, 214, 222, 236)
        val bottomLine = TextUtils.ellipsize(
            state.subtitle.ifBlank { "Toca: flotante · Sostén: leer" },
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(bottomLine, textLeft, cy + dp(12.5f), subtitlePaint)
    }

    private fun drawExpandedMedia(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int, topSafePx: Float) {
        val padH = dp(16f)
        val playerBottom = pillRect.bottom - dp(lyricsHeightDp(state.lyricsAvailable, state.lyricsOpen).toFloat())
        val availH = (playerBottom - topSafePx).coerceAtLeast(dp(106f))
        val artSize = dp(46f).coerceAtMost(availH * 0.42f)
        val artLeft = pillRect.left + padH
        val artTop = topSafePx + dp(6f)
        val artCy = artTop + artSize / 2f

        // Halo sutil teñido con el color dominante de la carátula
        chipPaint.color = withAlpha(primaryArgb, 42)
        canvas.drawRoundRect(
            artLeft - dp(3f),
            artTop - dp(3f),
            artLeft + artSize + dp(3f),
            artTop + artSize + dp(3f),
            dp(14f),
            dp(14f),
            chipPaint
        )

        val bmp = state.icon
        if (bmp != null) {
            drawRoundedBitmap(canvas, bmp, RectF(artLeft, artTop, artLeft + artSize, artTop + artSize), dp(12f))
        } else {
            chipPaint.color = withAlpha(primaryArgb, 54)
            canvas.drawRoundRect(artLeft, artTop, artLeft + artSize, artTop + artSize, dp(12f), dp(12f), chipPaint)
            drawMusicNoteGlyph(canvas, artLeft + artSize / 2f, artCy, dp(11f), primaryArgb)
        }

        // Botón compacto "Flotante" en la esquina derecha + ecualizador dinámico
        val floatBtnH = dp(26f)
        val floatBtnW = dp(30f)
        val floatRight = pillRect.right - padH
        val floatLeft = floatRight - floatBtnW
        mediaFloatRect.set(floatLeft, artCy - floatBtnH / 2f, floatRight, artCy + floatBtnH / 2f)
        chipPaint.color = withAlpha(primaryArgb, 56)
        canvas.drawRoundRect(mediaFloatRect, floatBtnH / 2f, floatBtnH / 2f, chipPaint)
        drawFloatingWindowGlyph(canvas, mediaFloatRect.centerX(), mediaFloatRect.centerY(), dp(6.2f), Color.WHITE)

        val eqStart = floatLeft - dp(21f)
        drawEqualizer(canvas, eqStart, artCy, primaryArgb, secondaryArgb, playing = state.playing)

        mediaHeaderRect.set(pillRect.left, pillRect.top, floatLeft - dp(4f), artTop + artSize + dp(4f))

        val textLeft = artLeft + artSize + dp(11f)
        val textMaxW = (eqStart - dp(8f) - textLeft).coerceAtLeast(dp(40f))

        titlePaint.textSize = dp(13.6f) * textScale
        titlePaint.color = Color.WHITE
        val title = TextUtils.ellipsize(
            state.title.ifBlank { "Reproducción multimedia" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(title, textLeft, artCy - dp(3f), titlePaint)

        subtitlePaint.textSize = dp(11.3f) * textScale
        subtitlePaint.color = Color.argb(185, 214, 222, 236)
        val artist = TextUtils.ellipsize(
            state.subtitle.ifBlank { "Toca aquí para abrir la app" },
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(artist, textLeft, artCy + dp(12.5f), subtitlePaint)

        // Barra de progreso TÁCTIL (arrastrar o tocar para adelantar/retroceder)
        val controlsH = dp(34f)
        val controlsCy = playerBottom - dp(12f) - controlsH / 2f
        val barY = ((artTop + artSize) + (controlsCy - controlsH / 2f)) / 2f

        val activeFraction = (if (isSeeking) seekPreviewFraction else state.progressFraction).coerceIn(0f, 1f)
        subtitlePaint.textSize = dp(10.2f)
        subtitlePaint.color = if (isSeeking) Color.WHITE else Color.argb(165, 212, 220, 234)
        val leftTime = if (isSeeking && state.mediaDurationMs > 0L) {
            formatMmSsLocal((state.mediaDurationMs * activeFraction).toLong())
        } else {
            state.progressLeftText.ifBlank { "0:00" }
        }
        val rightTime = state.progressRightText
        val timeW = dp(33f)
        val barLeft = if (rightTime.isNotBlank()) pillRect.left + padH + timeW else pillRect.left + padH
        val barRight = if (rightTime.isNotBlank()) pillRect.right - padH - timeW else pillRect.right - padH
        if (rightTime.isNotBlank()) {
            val tb = barY - (subtitlePaint.descent() + subtitlePaint.ascent()) / 2f
            canvas.drawText(leftTime, pillRect.left + padH, tb, subtitlePaint)
            subtitlePaint.color = Color.argb(155, 210, 218, 232)
            val rw = subtitlePaint.measureText(rightTime)
            canvas.drawText(rightTime, pillRect.right - padH - rw, tb, subtitlePaint)
        }

        val barH = if (isSeeking) dp(6f) else dp(4f)
        seekBarRect.set(barLeft, barY - barH / 2f, barRight, barY + barH / 2f)
        seekBarHitRect.set(barLeft - dp(10f), barY - dp(14f), barRight + dp(10f), barY + dp(14f))

        chipPaint.color = Color.argb(48, 255, 255, 255)
        canvas.drawRoundRect(seekBarRect, barH / 2f, barH / 2f, chipPaint)

        val progSpan = (barRight - barLeft).coerceAtLeast(1f)
        val progW = (progSpan * activeFraction).coerceAtLeast(dp(4f))
        chipPaint.shader = LinearGradient(
            barLeft, barY, barLeft + progW, barY,
            intArrayOf(primaryArgb, secondaryArgb),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(barLeft, barY - barH / 2f, barLeft + progW, barY + barH / 2f, barH / 2f, barH / 2f, chipPaint)
        chipPaint.shader = null

        // Punto deslizador (thumb) visible y ampliado al arrastrar
        if (state.mediaDurationMs > 0L) {
            val thumbX = (barLeft + progSpan * activeFraction).coerceIn(barLeft, barRight)
            val thumbR = if (isSeeking) dp(6.4f) else dp(4.2f)
            if (isSeeking) {
                chipPaint.color = withAlpha(primaryArgb, 95)
                canvas.drawCircle(thumbX, barY, thumbR + dp(3.5f), chipPaint)
            }
            chipPaint.color = Color.WHITE
            canvas.drawCircle(thumbX, barY, thumbR, chipPaint)
        }

        // Fila de 5 controles: -10s | Anterior | Play/Pausa | Siguiente | +10s
        val availBtnW = (pillRect.width() - padH * 2f).coerceAtLeast(dp(180f))
        val gap = dp(7f)
        val playW = (availBtnW * 0.25f).coerceIn(dp(50f), dp(72f))
        val sideW = ((availBtnW - playW - gap * 4f) / 4f).coerceAtLeast(dp(32f))
        val totalW = sideW * 4f + playW + gap * 4f
        val startX = pillRect.centerX() - totalW / 2f

        rewindRect.set(startX, controlsCy - controlsH / 2f, startX + sideW, controlsCy + controlsH / 2f)
        prevRect.set(rewindRect.right + gap, controlsCy - controlsH / 2f, rewindRect.right + gap + sideW, controlsCy + controlsH / 2f)
        playRect.set(prevRect.right + gap, controlsCy - controlsH / 2f, prevRect.right + gap + playW, controlsCy + controlsH / 2f)
        nextRect.set(playRect.right + gap, controlsCy - controlsH / 2f, playRect.right + gap + sideW, controlsCy + controlsH / 2f)
        forwardRect.set(nextRect.right + gap, controlsCy - controlsH / 2f, nextRect.right + gap + sideW, controlsCy + controlsH / 2f)

        val sideBg = Color.argb(24, 255, 255, 255)
        chipPaint.color = sideBg
        canvas.drawRoundRect(rewindRect, controlsH / 2f, controlsH / 2f, chipPaint)
        badgePaint.textSize = dp(10.5f)
        badgePaint.color = Color.argb(225, 236, 240, 248)
        val seekBaseline = controlsCy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText("-10", rewindRect.centerX(), seekBaseline, badgePaint)

        chipPaint.color = sideBg
        canvas.drawRoundRect(prevRect, controlsH / 2f, controlsH / 2f, chipPaint)
        drawSkipGlyph(canvas, prevRect.centerX(), prevRect.centerY(), dp(7.2f), next = false)

        // Botón central Play/Pausa con brillo del color de la carátula
        chipPaint.color = withAlpha(primaryArgb, 75)
        canvas.drawRoundRect(
            playRect.left - dp(1.5f),
            playRect.top - dp(1.5f),
            playRect.right + dp(1.5f),
            playRect.bottom + dp(1.5f),
            (controlsH + dp(3f)) / 2f,
            (controlsH + dp(3f)) / 2f,
            chipPaint
        )
        chipPaint.color = Color.WHITE
        canvas.drawRoundRect(playRect, controlsH / 2f, controlsH / 2f, chipPaint)
        if (state.playing) {
            drawPauseGlyph(canvas, playRect.centerX(), playRect.centerY(), dp(7.6f), Color.BLACK)
        } else {
            drawPlayGlyph(canvas, playRect.centerX(), playRect.centerY(), dp(7.6f), Color.BLACK)
        }

        chipPaint.color = sideBg
        canvas.drawRoundRect(nextRect, controlsH / 2f, controlsH / 2f, chipPaint)
        drawSkipGlyph(canvas, nextRect.centerX(), nextRect.centerY(), dp(7.2f), next = true)

        chipPaint.color = sideBg
        canvas.drawRoundRect(forwardRect, controlsH / 2f, controlsH / 2f, chipPaint)
        badgePaint.color = Color.argb(225, 236, 240, 248)
        canvas.drawText("+10", forwardRect.centerX(), seekBaseline, badgePaint)
        if (state.lyricsAvailable) drawMediaLyrics(canvas, playerBottom, primaryArgb)
    }

    private fun drawMediaLyrics(canvas: Canvas, top: Float, accent: Int) {
        val center = pillRect.centerX()
        lyricsToggleRect.set(center - dp(58f), top - dp(3f), center + dp(58f), top + dp(30f))
        chipPaint.color = withAlpha(accent, if (state.lyricsOpen) 72 else 44)
        canvas.drawRoundRect(lyricsToggleRect, dp(13f), dp(13f), chipPaint)
        badgePaint.textSize = dp(11f)
        badgePaint.color = Color.WHITE
        val toggleLabel = if (state.lyricsOpen) "Ocultar letra" else "Letra · LRCLIB"
        val baseline = lyricsToggleRect.centerY() - (badgePaint.ascent() + badgePaint.descent()) / 2f
        canvas.drawText(toggleLabel, center, baseline, badgePaint)
        if (!state.lyricsOpen) return

        val pad = dp(16f)
        val side = if (state.lyricsPaging) dp(30f) else 0f
        val textWidth = (pillRect.width() - (pad + side) * 2f).toInt().coerceAtLeast(1)
        val lineSize = dp(13f) * textScale
        val key = listOf(state.lyricsPrimary, textWidth, lineSize)
        if (key != lyricsLayoutKey) {
            lyricsLayoutKey = key
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = lineSize
            }
            lyricsLayout = StaticLayout.Builder.obtain(state.lyricsPrimary, 0, state.lyricsPrimary.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
        }
        val layout = lyricsLayout
        if (layout != null) {
            canvas.save()
            canvas.translate(center - textWidth / 2f, top + dp(40f))
            layout.draw(canvas)
            canvas.restore()
        }
        badgePaint.textSize = dp(11f) * textScale
        badgePaint.color = Color.argb(185, 214, 222, 236)
        val next = TextUtils.ellipsize(state.lyricsSecondary, badgePaint, textWidth.toFloat(), TextUtils.TruncateAt.END)
        canvas.drawText(next.toString(), center, top + dp(91f), badgePaint)
        if (state.lyricsPaging) {
            lyricsPreviousRect.set(pillRect.left + pad, top + dp(37f), pillRect.left + pad + side, top + dp(86f))
            lyricsNextRect.set(pillRect.right - pad - side, top + dp(37f), pillRect.right - pad, top + dp(86f))
            badgePaint.textSize = dp(20f)
            canvas.drawText("‹", lyricsPreviousRect.centerX(), top + dp(66f), badgePaint)
            canvas.drawText("›", lyricsNextRect.centerX(), top + dp(66f), badgePaint)
        }
    }

    private fun formatMmSsLocal(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
    }

    private fun drawExpandedNotification(canvas: Canvas, primaryArgb: Int, accentArgb: Int, topSafePx: Float) {
        val padH = dp(16f)
        val iconR = dp(17f)
        val iconCx = pillRect.left + padH + iconR
        val iconCy = topSafePx + dp(6f) + iconR

        val bmp = state.icon
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, iconCy, iconR)
        } else {
            chipPaint.color = withAlpha(accentArgb, 55)
            canvas.drawCircle(iconCx, iconCy, iconR, chipPaint)
        }

        val textLeft = iconCx + iconR + dp(11f)
        val textMaxW = (pillRect.right - padH - textLeft).coerceAtLeast(dp(60f))

        titlePaint.textSize = dp(13.2f) * textScale
        titlePaint.color = Color.WHITE
        val header = TextUtils.ellipsize(
            state.title.ifBlank { "Notificación" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(header, textLeft, iconCy - dp(3f), titlePaint)

        subtitlePaint.textSize = dp(11.3f) * textScale
        subtitlePaint.color = Color.argb(190, 220, 226, 238)
        val sub = TextUtils.ellipsize(
            state.subtitle,
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        if (sub.isNotBlank()) {
            canvas.drawText(sub, textLeft, iconCy + dp(11.5f), subtitlePaint)
        }

        val previewY = iconCy + iconR + dp(15f)
        subtitlePaint.textSize = dp(11.8f) * textScale
        subtitlePaint.color = Color.argb(225, 238, 242, 248)
        val previewLine = TextUtils.ellipsize(
            state.preview.ifBlank { "Toca abajo para abrir en ventana flotante" },
            subtitlePaint,
            (pillRect.width() - padH * 2f).coerceAtLeast(dp(60f)),
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(previewLine, pillRect.left + padH, previewY, subtitlePaint)

        val btnH = dp(31f)
        val btnTop = pillRect.bottom - dp(13f) - btnH
        val gap = dp(8f)
        val actionsCount = state.notificationActionLabels.size.coerceAtMost(2)
        val totalBtns = 1 + actionsCount
        val availW = pillRect.width() - padH * 2f - gap * (totalBtns - 1)
        val btnW = availW / totalBtns

        notifFloatBtnRect.set(pillRect.left + padH, btnTop, pillRect.left + padH + btnW, btnTop + btnH)
        chipPaint.color = withAlpha(primaryArgb, 75)
        canvas.drawRoundRect(notifFloatBtnRect, btnH / 2f, btnH / 2f, chipPaint)
        badgePaint.textSize = dp(11.2f)
        badgePaint.color = Color.WHITE
        val floatBaseline = notifFloatBtnRect.centerY() - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText("Flotante", notifFloatBtnRect.centerX(), floatBaseline, badgePaint)

        for (i in 0 until 2) {
            if (i < actionsCount) {
                val left = notifFloatBtnRect.right + gap + i * (btnW + gap)
                notifActionRects[i].set(left, btnTop, left + btnW, btnTop + btnH)
                chipPaint.color = Color.argb(30, 255, 255, 255)
                canvas.drawRoundRect(notifActionRects[i], btnH / 2f, btnH / 2f, chipPaint)
                val label = TextUtils.ellipsize(
                    state.notificationActionLabels[i],
                    badgePaint,
                    btnW - dp(10f),
                    TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(label, notifActionRects[i].centerX(), floatBaseline, badgePaint)
            } else {
                notifActionRects[i].setEmpty()
            }
        }
    }

    private fun drawExpandedHub(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int, topSafePx: Float) {
        val padH = dp(14f)
        val topRowTop = topSafePx + dp(6f)
        val tileSize = dp(46f).coerceAtMost((pillRect.width() - padH * 2f) / hubTileRects.size)
        val gap = ((pillRect.width() - padH * 2f) - tileSize * hubTileRects.size) / (hubTileRects.size - 1)
        val soundLabel = when (state.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silencio"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrar"
            else -> "Sonido"
        }
        val labels = listOf("Buscar", "Linterna", soundLabel, "Captura", "Clips", "No mol.")

        for (i in hubTileRects.indices) {
            val left = pillRect.left + padH + i * (tileSize + gap)
            hubTileRects[i].set(left, topRowTop, left + tileSize, topRowTop + tileSize + dp(14f))

            val isActive = (i == 1 && state.flashlightOn) ||
                (i == 2 && state.ringerMode != AudioManager.RINGER_MODE_NORMAL) ||
                (i == 5 && state.doNotDisturbOn)
            val cx = left + tileSize / 2f
            val cy = topRowTop + tileSize / 2f

            chipPaint.color = when {
                i == 1 && state.flashlightOn -> withAlpha(COLOR_AMBER, 82)
                i == 2 && state.ringerMode == AudioManager.RINGER_MODE_SILENT -> withAlpha(COLOR_CORAL, 78)
                i == 2 && state.ringerMode == AudioManager.RINGER_MODE_VIBRATE -> withAlpha(COLOR_AMBER, 78)
                i == 0 -> withAlpha(primaryArgb, 48)
                i == 5 && state.doNotDisturbOn -> withAlpha(primaryArgb, 82)
                else -> Color.argb(26, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, dp(20f).coerceAtMost(tileSize * 0.46f), chipPaint)

            val tint = when {
                i == 1 && state.flashlightOn -> COLOR_AMBER
                i == 2 && state.ringerMode == AudioManager.RINGER_MODE_SILENT -> COLOR_CORAL
                i == 2 && state.ringerMode == AudioManager.RINGER_MODE_VIBRATE -> COLOR_AMBER
                i == 2 -> COLOR_GREEN
                i == 0 -> primaryArgb
                i == 4 -> secondaryArgb
                i == 5 && state.doNotDisturbOn -> primaryArgb
                else -> Color.WHITE
            }
            when (i) {
                0 -> drawSearchGlyph(canvas, cx, cy, dp(8.2f), tint)
                1 -> drawFlashlightGlyph(canvas, cx, cy, dp(8.2f), tint)
                2 -> when (state.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> drawBellOffGlyph(canvas, cx, cy, dp(8.2f), tint)
                    AudioManager.RINGER_MODE_VIBRATE -> drawVibrateGlyph(canvas, cx, cy, dp(8.2f), tint)
                    else -> drawSoundOnGlyph(canvas, cx, cy, dp(8.2f), tint)
                }
                3 -> drawScreenshotGlyph(canvas, cx, cy, dp(7.8f), tint)
                4 -> drawClipboardGlyph(canvas, cx, cy, dp(7.8f), tint)
                5 -> drawBellOffGlyph(canvas, cx, cy, dp(8.2f), tint)
            }

            badgePaint.textSize = dp(9.6f)
            badgePaint.color = if (isActive) Color.WHITE else Color.argb(185, 215, 222, 236)
            val label = TextUtils.ellipsize(labels[i], badgePaint, tileSize + gap - dp(2f), TextUtils.TruncateAt.END)
            canvas.drawText(label.toString(), cx, topRowTop + tileSize + dp(10f), badgePaint)
        }

        // Fila inferior: Cronómetro integrado rápido
        val rowH = dp(33f)
        val rowTop = pillRect.bottom - dp(13f) - rowH
        val rowRect = RectF(pillRect.left + padH, rowTop, pillRect.right - padH, rowTop + rowH)
        chipPaint.color = Color.argb(20, 255, 255, 255)
        canvas.drawRoundRect(rowRect, rowH / 2f, rowH / 2f, chipPaint)

        val clockCx = rowRect.left + dp(18f)
        val rowCy = rowRect.centerY()
        drawClockGlyph(canvas, clockCx, rowCy, dp(7.5f), if (state.stopwatchRunning) COLOR_AMBER else Color.WHITE)

        titlePaint.textSize = dp(12.8f) * textScale
        titlePaint.color = Color.WHITE
        val swText = "Cronómetro  ${state.stopwatchLabel}"
        val swBase = rowCy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(swText, clockCx + dp(14f), swBase, titlePaint)

        val btnW = dp(62f)
        val btnH = dp(24f)
        hubStopwatchResetRect.set(rowRect.right - dp(6f) - dp(52f), rowCy - btnH / 2f, rowRect.right - dp(6f), rowCy + btnH / 2f)
        hubStopwatchToggleRect.set(hubStopwatchResetRect.left - dp(6f) - btnW, rowCy - btnH / 2f, hubStopwatchResetRect.left - dp(6f), rowCy + btnH / 2f)

        chipPaint.color = if (state.stopwatchRunning) withAlpha(COLOR_AMBER, 85) else withAlpha(primaryArgb, 82)
        canvas.drawRoundRect(hubStopwatchToggleRect, btnH / 2f, btnH / 2f, chipPaint)
        badgePaint.textSize = dp(10.5f)
        badgePaint.color = Color.WHITE
        val btnBase = rowCy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(if (state.stopwatchRunning) "Pausar" else "Iniciar", hubStopwatchToggleRect.centerX(), btnBase, badgePaint)

        chipPaint.color = Color.argb(32, 255, 255, 255)
        canvas.drawRoundRect(hubStopwatchResetRect, btnH / 2f, btnH / 2f, chipPaint)
        canvas.drawText("Cero", hubStopwatchResetRect.centerX(), btnBase, badgePaint)
    }

    // ---- Dibujo de iconos vectoriales ----

    private fun drawEqualizer(
        canvas: Canvas,
        startX: Float,
        cy: Float,
        primaryArgb: Int,
        secondaryArgb: Int,
        playing: Boolean
    ) {
        val barW = dp(2.7f)
        val gap = dp(2.4f)
        val maxH = dp(12.5f)
        val minH = dp(3.8f)
        for (i in 0..2) {
            val factor = if (playing) 0.5f + 0.5f * sin(eqPhase + i * 1.9f) else 0.25f + (i % 2) * 0.22f
            val h = minH + (maxH - minH) * factor
            val x = startX + i * (barW + gap)
            chipPaint.color = if (i == 1) secondaryArgb else primaryArgb
            canvas.drawRoundRect(x, cy - h / 2f, x + barW, cy + h / 2f, barW / 2f, barW / 2f, chipPaint)
        }
    }

    private fun drawCircularBitmap(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, radius: Float) {
        iconRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bmp, null, iconRect, bitmapPaint)
        canvas.restore()
    }

    private fun drawRoundedBitmap(canvas: Canvas, bmp: Bitmap, rect: RectF, radius: Float) {
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        canvas.restore()
    }

    private fun drawFloatingWindowGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(cx - r, cy - r * 0.78f, cx + r, cy + r * 0.78f, dp(2f), dp(2f), iconPaint)
        fillGlyphPaint.color = color
        canvas.drawRoundRect(cx - r * 0.05f, cy - r * 0.05f, cx + r * 0.72f, cy + r * 0.52f, dp(1.2f), dp(1.2f), fillGlyphPaint)
    }

    private fun drawClipboardGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        canvas.drawRoundRect(cx - r * 0.7f, cy - r * 0.72f, cx + r * 0.7f, cy + r * 0.88f, dp(2f), dp(2f), iconPaint)
        canvas.drawRoundRect(cx - r * 0.34f, cy - r * 0.98f, cx + r * 0.34f, cy - r * 0.54f, dp(1.2f), dp(1.2f), iconPaint)
    }

    private fun drawScreenshotGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        val b = r * 0.85f
        val arm = r * 0.44f
        val path = Path().apply {
            moveTo(cx - b, cy - b + arm); lineTo(cx - b, cy - b); lineTo(cx - b + arm, cy - b)
            moveTo(cx + b - arm, cy - b); lineTo(cx + b, cy - b); lineTo(cx + b, cy - b + arm)
            moveTo(cx + b, cy + b - arm); lineTo(cx + b, cy + b); lineTo(cx + b - arm, cy + b)
            moveTo(cx - b + arm, cy + b); lineTo(cx - b, cy + b); lineTo(cx - b, cy + b - arm)
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawSearchGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.8f)
        canvas.drawCircle(cx - r * 0.14f, cy - r * 0.14f, r * 0.56f, iconPaint)
        canvas.drawLine(cx + r * 0.28f, cy + r * 0.28f, cx + r * 0.76f, cy + r * 0.76f, iconPaint)
    }

    private fun drawFlashlightGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.7f)
        val path = Path().apply {
            moveTo(cx - r * 0.52f, cy - r * 0.78f)
            lineTo(cx + r * 0.52f, cy - r * 0.78f)
            lineTo(cx + r * 0.28f, cy - r * 0.18f)
            lineTo(cx + r * 0.28f, cy + r * 0.82f)
            lineTo(cx - r * 0.28f, cy + r * 0.82f)
            lineTo(cx - r * 0.28f, cy - r * 0.18f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawCircle(cx, cy + r * 0.22f, dp(1.2f), fillGlyphPaint.apply { this.color = color })
    }

    private fun drawBellOffGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        val path = Path().apply {
            moveTo(cx - r * 0.65f, cy + r * 0.45f)
            lineTo(cx - r * 0.5f, cy - r * 0.22f)
            quadTo(cx, cy - r * 0.95f, cx + r * 0.5f, cy - r * 0.22f)
            lineTo(cx + r * 0.65f, cy + r * 0.45f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawLine(cx - r * 0.75f, cy - r * 0.65f, cx + r * 0.75f, cy + r * 0.72f, iconPaint)
    }

    private fun drawVibrateGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        canvas.drawRoundRect(cx - r * 0.42f, cy - r * 0.72f, cx + r * 0.42f, cy + r * 0.72f, dp(2f), dp(2f), iconPaint)
        canvas.drawLine(cx - r * 0.78f, cy - r * 0.42f, cx - r * 0.78f, cy + r * 0.42f, iconPaint)
        canvas.drawLine(cx + r * 0.78f, cy - r * 0.42f, cx + r * 0.78f, cy + r * 0.42f, iconPaint)
    }

    private fun drawSoundOnGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        val path = Path().apply {
            moveTo(cx - r * 0.65f, cy + r * 0.45f)
            lineTo(cx - r * 0.5f, cy - r * 0.22f)
            quadTo(cx, cy - r * 0.95f, cx + r * 0.5f, cy - r * 0.22f)
            lineTo(cx + r * 0.65f, cy + r * 0.45f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawCircle(cx, cy + r * 0.72f, dp(1.3f), fillGlyphPaint.apply { this.color = color })
    }

    private fun drawClockGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        canvas.drawCircle(cx, cy, r * 0.85f, iconPaint)
        canvas.drawLine(cx, cy, cx, cy - r * 0.48f, iconPaint)
        canvas.drawLine(cx, cy, cx + r * 0.36f, cy + r * 0.18f, iconPaint)
    }

    private fun drawBoltGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fillGlyphPaint.color = color
        val path = Path().apply {
            moveTo(cx + r * 0.12f, cy - r * 0.92f)
            lineTo(cx - r * 0.62f, cy + r * 0.08f)
            lineTo(cx - r * 0.02f, cy + r * 0.08f)
            lineTo(cx - r * 0.18f, cy + r * 0.92f)
            lineTo(cx + r * 0.62f, cy - r * 0.08f)
            lineTo(cx + r * 0.04f, cy - r * 0.08f)
            close()
        }
        canvas.drawPath(path, fillGlyphPaint)
    }

    private fun drawBluetoothGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.7f)
        val path = Path().apply {
            moveTo(cx - r * 0.45f, cy - r * 0.42f)
            lineTo(cx + r * 0.45f, cy + r * 0.42f)
            lineTo(cx, cy + r * 0.88f)
            lineTo(cx, cy - r * 0.88f)
            lineTo(cx + r * 0.45f, cy - r * 0.42f)
            lineTo(cx - r * 0.45f, cy + r * 0.42f)
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawPhoneGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.8f)
        val path = Path().apply {
            moveTo(cx - r * 0.55f, cy - r * 0.68f)
            quadTo(cx - r * 0.72f, cy + r * 0.15f, cx + r * 0.58f, cy + r * 0.68f)
        }
        canvas.drawPath(path, iconPaint)
        fillGlyphPaint.color = color
        canvas.drawCircle(cx - r * 0.52f, cy - r * 0.58f, dp(1.7f), fillGlyphPaint)
        canvas.drawCircle(cx + r * 0.52f, cy + r * 0.58f, dp(1.7f), fillGlyphPaint)
    }

    private fun drawMusicNoteGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        canvas.drawLine(cx + r * 0.25f, cy - r * 0.7f, cx + r * 0.25f, cy + r * 0.45f, iconPaint)
        canvas.drawLine(cx + r * 0.25f, cy - r * 0.7f, cx + r * 0.75f, cy - r * 0.45f, iconPaint)
        fillGlyphPaint.color = color
        canvas.drawCircle(cx - r * 0.12f, cy + r * 0.45f, r * 0.36f, fillGlyphPaint)
    }

    private fun drawPlayGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fillGlyphPaint.color = color
        val path = Path().apply {
            moveTo(cx - r * 0.46f, cy - r * 0.76f)
            lineTo(cx + r * 0.76f, cy)
            lineTo(cx - r * 0.46f, cy + r * 0.76f)
            close()
        }
        canvas.drawPath(path, fillGlyphPaint)
    }

    private fun drawPauseGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fillGlyphPaint.color = color
        val w = r * 0.34f
        canvas.drawRoundRect(cx - r * 0.56f, cy - r * 0.72f, cx - r * 0.56f + w, cy + r * 0.72f, dp(1.4f), dp(1.4f), fillGlyphPaint)
        canvas.drawRoundRect(cx + r * 0.22f, cy - r * 0.72f, cx + r * 0.22f + w, cy + r * 0.72f, dp(1.4f), dp(1.4f), fillGlyphPaint)
    }

    private fun drawSkipGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, next: Boolean) {
        fillGlyphPaint.color = Color.WHITE
        val dir = if (next) 1f else -1f
        val path = Path().apply {
            moveTo(cx - dir * r * 0.65f, cy - r * 0.65f)
            lineTo(cx + dir * r * 0.32f, cy)
            lineTo(cx - dir * r * 0.65f, cy + r * 0.65f)
            close()
        }
        canvas.drawPath(path, fillGlyphPaint)
        val barX = cx + dir * r * 0.42f
        val barW = r * 0.24f
        canvas.drawRoundRect(
            minOf(barX, barX + dir * barW),
            cy - r * 0.65f,
            maxOf(barX, barX + dir * barW),
            cy + r * 0.65f,
            dp(1f),
            dp(1f),
            fillGlyphPaint
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val COLOR_GREEN = 0xFF34D399.toInt()
        private const val COLOR_BLUE = 0xFF38BDF8.toInt()
        private const val COLOR_AMBER = 0xFFFBBF24.toInt()
        private const val COLOR_CORAL = 0xFFFB7185.toInt()
    }
}
