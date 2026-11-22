package com.stree.nexo.panel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
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

enum class IslandHubAction {
    SPOTLIGHT,
    FLASHLIGHT,
    SILENT,
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
    val playing: Boolean = false,
    val progressFraction: Float = 0f,
    val progressLeftText: String = "",
    val progressRightText: String = "",
    val batteryPercent: Int = 0,
    val notificationActionLabels: List<String> = emptyList(),
    val satellite: IslandSatellite = IslandSatellite.NONE,
    val satelliteIcon: Bitmap? = null,
    val flashlightOn: Boolean = false,
    val silentOn: Boolean = false,
    val stopwatchRunning: Boolean = false,
    val stopwatchLabel: String = "00:00"
)

interface IslandInteractionListener {
    fun onTapMain(mode: IslandMode)
    fun onLongPress(mode: IslandMode)
    fun onSwipeDown(mode: IslandMode)
    fun onSwipeUpDismiss()
    fun onTapSatellite(satellite: IslandSatellite)
    fun onMediaPrev()
    fun onMediaPlayPause()
    fun onMediaNext()
    fun onMediaOpenApp()
    fun onNotificationOpenFloating()
    fun onNotificationQuickAction(index: Int)
    fun onHubAction(action: IslandHubAction)
}

/**
 * Isla Dinámica OLED negra pura (#000000) fundida con la cámara perforada.
 * Sin bordes de color: solo acentos internos sutiles sincronizados con Nexo.
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

    private val longPressRunnable = Runnable {
        if (!gestureHandled && !swipedUp && !swipedDown) {
            gestureHandled = true
            animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            listener?.onLongPress(state.mode)
        }
    }

    // Hit-boxes interactivos
    private val pillRect = RectF()
    private val satelliteRect = RectF()
    private val prevRect = RectF()
    private val playRect = RectF()
    private val nextRect = RectF()
    private val mediaHeaderRect = RectF()
    private val notifFloatBtnRect = RectF()
    private val notifActionRects = Array(2) { RectF() }
    private val hubTileRects = Array(5) { RectF() }
    private val hubStopwatchToggleRect = RectF()
    private val hubStopwatchResetRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
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

    private fun hasSatellite(targetMode: IslandMode = state.mode, sat: IslandSatellite = state.satellite): Boolean =
        sat != IslandSatellite.NONE && (
            targetMode == IslandMode.COMPACT_MEDIA ||
                targetMode == IslandMode.CALL ||
                targetMode == IslandMode.STOPWATCH ||
                targetMode == IslandMode.SILENT_TIMER
            )

    fun desiredWidthDp(
        targetMode: IslandMode = state.mode,
        sat: IslandSatellite = state.satellite
    ): Int {
        val base = when (targetMode) {
            IslandMode.HIDDEN -> 1
            IslandMode.IDLE_PILL -> Prefs.dynamicIslandIdleWidth(context)
            IslandMode.SILENT_TIMER -> 178
            IslandMode.STOPWATCH -> 182
            IslandMode.COMPACT_MEDIA -> 212
            IslandMode.CLIPBOARD -> 214
            IslandMode.SCREENSHOT -> 224
            IslandMode.CHARGING -> 222
            IslandMode.BLUETOOTH -> 236
            IslandMode.CALL -> 232
            IslandMode.NOTIFICATION -> 290
            IslandMode.EXPANDED_MEDIA -> 334
            IslandMode.EXPANDED_NOTIFICATION -> 334
            IslandMode.EXPANDED_HUB -> 334
        }
        return if (hasSatellite(targetMode, sat)) base + 44 else base
    }

    fun desiredHeightDp(targetMode: IslandMode = state.mode): Int = when (targetMode) {
        IslandMode.HIDDEN -> 1
        IslandMode.IDLE_PILL -> Prefs.dynamicIslandIdleHeight(context)
        IslandMode.SILENT_TIMER -> 34
        IslandMode.STOPWATCH -> 34
        IslandMode.COMPACT_MEDIA -> 36
        IslandMode.CLIPBOARD -> 38
        IslandMode.SCREENSHOT -> 38
        IslandMode.CHARGING -> 38
        IslandMode.BLUETOOTH -> 38
        IslandMode.CALL -> 38
        IslandMode.NOTIFICATION -> 54
        IslandMode.EXPANDED_NOTIFICATION -> 132
        IslandMode.EXPANDED_HUB -> 138
        IslandMode.EXPANDED_MEDIA -> 142
    }

    fun render(newState: IslandDisplayState) {
        val modeChanged = state.mode != newState.mode || state.satellite != newState.satellite
        state = newState

        if (modeChanged) {
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(0.76f, 1f).apply {
                duration = when (newState.mode) {
                    IslandMode.EXPANDED_MEDIA,
                    IslandMode.EXPANDED_NOTIFICATION,
                    IslandMode.EXPANDED_HUB -> 270L
                    else -> 220L
                }
                interpolator = OvershootInterpolator(0.75f)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state.mode == IslandMode.HIDDEN) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                gestureHandled = false
                swipedUp = false
                swipedDown = false
                animate().scaleX(0.975f).scaleY(0.975f).setDuration(75).start()
                touchHandler.removeCallbacks(longPressRunnable)
                touchHandler.postDelayed(longPressRunnable, 380L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > dp(12f) || abs(dy) > dp(12f)) {
                    touchHandler.removeCallbacks(longPressRunnable)
                }
                if (dy < -dp(18f) && abs(dy) > abs(dx)) swipedUp = true
                if (dy > dp(18f) && abs(dy) > abs(dx)) swipedDown = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchHandler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(105).start()
                if (gestureHandled) return true
                if (swipedUp) {
                    listener?.onSwipeUpDismiss()
                    return true
                }
                if (swipedDown) {
                    listener?.onSwipeDown(state.mode)
                    return true
                }
                handleTap(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touchHandler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(105).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        if (hasSatellite() && satelliteRect.contains(x, y)) {
            listener?.onTapSatellite(state.satellite)
            return
        }
        when (state.mode) {
            IslandMode.EXPANDED_MEDIA -> when {
                prevRect.contains(x, y) -> listener?.onMediaPrev()
                playRect.contains(x, y) -> listener?.onMediaPlayPause()
                nextRect.contains(x, y) -> listener?.onMediaNext()
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
                    IslandHubAction.CLIPBOARD
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

        val spec = Prefs.panelStyle(context).visualSpec(light = false)
        val primaryArgb = spec.primary.toArgb()
        val secondaryArgb = spec.secondary.toArgb()
        val accentArgb = state.accentColor ?: primaryArgb

        val showSat = hasSatellite()
        val satDiameter = dp(36f).coerceAtMost(height.toFloat())
        val satGap = dp(8f)

        val scale = morphProgress.coerceIn(0.70f, 1.03f)
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
            else -> drawH / 2f
        }

        // Cápsula principal: Negro OLED puro (#000000) sin borde
        bgPaint.color = Color.BLACK
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)

        // Burbuja satélite independiente (modo burbuja dividida estilo iPhone)
        if (showSat) {
            val satLeft = pillRect.right + satGap
            val satTop = pillRect.centerY() - satDiameter / 2f
            satelliteRect.set(satLeft, satTop, satLeft + satDiameter, satTop + satDiameter)
            canvas.drawOval(satelliteRect, bgPaint)
            drawSatelliteContent(canvas, primaryArgb, secondaryArgb)
        } else {
            satelliteRect.setEmpty()
        }

        when (state.mode) {
            IslandMode.IDLE_PILL -> Unit // Negro puro absoluto fundido con la cámara
            IslandMode.COMPACT_MEDIA -> drawCompactMedia(canvas, primaryArgb, secondaryArgb)
            IslandMode.EXPANDED_MEDIA -> drawExpandedMedia(canvas, primaryArgb, secondaryArgb)
            IslandMode.NOTIFICATION -> drawCompactNotification(canvas, accentArgb)
            IslandMode.EXPANDED_NOTIFICATION -> drawExpandedNotification(canvas, primaryArgb, accentArgb)
            IslandMode.EXPANDED_HUB -> drawExpandedHub(canvas, primaryArgb, secondaryArgb)
            IslandMode.CHARGING -> drawCharging(canvas)
            IslandMode.BLUETOOTH -> drawBluetooth(canvas)
            IslandMode.CALL -> drawCall(canvas)
            IslandMode.STOPWATCH -> drawStopwatch(canvas)
            IslandMode.SILENT_TIMER -> drawSilentTimer(canvas, primaryArgb)
            IslandMode.CLIPBOARD -> drawActionAlert(canvas, primaryArgb, isScreenshot = false)
            IslandMode.SCREENSHOT -> drawActionAlert(canvas, secondaryArgb, isScreenshot = true)
            IslandMode.HIDDEN -> Unit
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

    private fun drawCompactMedia(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val cy = pillRect.centerY()
        val iconR = dp(11.5f)
        val iconCx = pillRect.left + dp(17f)
        val bmp = state.icon
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, cy, iconR)
        } else {
            chipPaint.color = withAlpha(primaryArgb, 50)
            canvas.drawCircle(iconCx, cy, iconR, chipPaint)
            drawMusicNoteGlyph(canvas, iconCx, cy, dp(6f), primaryArgb)
        }

        val eqRight = pillRect.right - dp(12f)
        drawEqualizer(canvas, eqRight - dp(13.5f), cy, primaryArgb, secondaryArgb, playing = state.playing)

        val textLeft = iconCx + iconR + dp(8f)
        val textMaxW = (eqRight - dp(21f) - textLeft).coerceAtLeast(dp(24f))
        titlePaint.textSize = dp(11.8f)
        titlePaint.color = Color.WHITE
        val ellipsized = TextUtils.ellipsize(
            state.title.ifBlank { "Reproduciendo" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(ellipsized, textLeft, baseline, titlePaint)
    }

    private fun drawExpandedMedia(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val padH = dp(18f)
        val artSize = dp(46f)
        val artLeft = pillRect.left + padH
        val artTop = pillRect.top + dp(16f)
        mediaHeaderRect.set(pillRect.left, pillRect.top, pillRect.right, artTop + artSize + dp(4f))

        val bmp = state.icon
        if (bmp != null) {
            drawRoundedBitmap(canvas, bmp, RectF(artLeft, artTop, artLeft + artSize, artTop + artSize), dp(12f))
        } else {
            chipPaint.color = withAlpha(primaryArgb, 45)
            canvas.drawRoundRect(artLeft, artTop, artLeft + artSize, artTop + artSize, dp(12f), dp(12f), chipPaint)
            drawMusicNoteGlyph(canvas, artLeft + artSize / 2f, artTop + artSize / 2f, dp(11f), primaryArgb)
        }

        val eqStart = pillRect.right - padH - dp(15f)
        val artCy = artTop + artSize / 2f
        drawEqualizer(canvas, eqStart, artCy, primaryArgb, secondaryArgb, playing = state.playing)

        val textLeft = artLeft + artSize + dp(12f)
        val textMaxW = (eqStart - dp(10f) - textLeft).coerceAtLeast(dp(40f))

        titlePaint.textSize = dp(14f)
        titlePaint.color = Color.WHITE
        val title = TextUtils.ellipsize(
            state.title.ifBlank { "Reproducción multimedia" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(title, textLeft, artCy - dp(3f), titlePaint)

        subtitlePaint.textSize = dp(11.8f)
        subtitlePaint.color = Color.argb(175, 214, 220, 232)
        val artist = TextUtils.ellipsize(
            state.subtitle.ifBlank { "Toca aquí para abrir la app" },
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(artist, textLeft, artCy + dp(13f), subtitlePaint)

        // Barra de progreso real de la canción
        val barY = artTop + artSize + dp(15f)
        subtitlePaint.textSize = dp(10f)
        subtitlePaint.color = Color.argb(150, 210, 216, 228)
        val leftTime = state.progressLeftText.ifBlank { "0:00" }
        val rightTime = state.progressRightText
        val timeW = dp(32f)
        val barLeft = if (rightTime.isNotBlank()) pillRect.left + padH + timeW else pillRect.left + padH
        val barRight = if (rightTime.isNotBlank()) pillRect.right - padH - timeW else pillRect.right - padH
        if (rightTime.isNotBlank()) {
            val tb = barY - (subtitlePaint.descent() + subtitlePaint.ascent()) / 2f
            canvas.drawText(leftTime, pillRect.left + padH, tb, subtitlePaint)
            val rw = subtitlePaint.measureText(rightTime)
            canvas.drawText(rightTime, pillRect.right - padH - rw, tb, subtitlePaint)
        }
        val barH = dp(3.5f)
        chipPaint.color = Color.argb(46, 255, 255, 255)
        canvas.drawRoundRect(barLeft, barY - barH / 2f, barRight, barY + barH / 2f, barH / 2f, barH / 2f, chipPaint)
        val progW = ((barRight - barLeft) * state.progressFraction.coerceIn(0f, 1f)).coerceAtLeast(dp(4f))
        chipPaint.shader = LinearGradient(
            barLeft, barY, barLeft + progW, barY,
            intArrayOf(primaryArgb, secondaryArgb),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(barLeft, barY - barH / 2f, barLeft + progW, barY + barH / 2f, barH / 2f, barH / 2f, chipPaint)
        chipPaint.shader = null

        // Botones Anterior / Play-Pausa / Siguiente
        val controlsCy = pillRect.bottom - dp(25f)
        val btnH = dp(34f)
        val btnW = dp(66f)
        val gap = dp(14f)
        val totalW = btnW * 3f + gap * 2f
        val startX = pillRect.centerX() - totalW / 2f

        prevRect.set(startX, controlsCy - btnH / 2f, startX + btnW, controlsCy + btnH / 2f)
        playRect.set(prevRect.right + gap, controlsCy - btnH / 2f, prevRect.right + gap + btnW, controlsCy + btnH / 2f)
        nextRect.set(playRect.right + gap, controlsCy - btnH / 2f, playRect.right + gap + btnW, controlsCy + btnH / 2f)

        chipPaint.color = Color.argb(26, 255, 255, 255)
        canvas.drawRoundRect(prevRect, btnH / 2f, btnH / 2f, chipPaint)
        drawSkipGlyph(canvas, prevRect.centerX(), prevRect.centerY(), dp(7.5f), next = false)

        chipPaint.color = Color.WHITE
        canvas.drawRoundRect(playRect, btnH / 2f, btnH / 2f, chipPaint)
        if (state.playing) {
            drawPauseGlyph(canvas, playRect.centerX(), playRect.centerY(), dp(7.5f), Color.BLACK)
        } else {
            drawPlayGlyph(canvas, playRect.centerX(), playRect.centerY(), dp(7.5f), Color.BLACK)
        }

        chipPaint.color = Color.argb(26, 255, 255, 255)
        canvas.drawRoundRect(nextRect, btnH / 2f, btnH / 2f, chipPaint)
        drawSkipGlyph(canvas, nextRect.centerX(), nextRect.centerY(), dp(7.5f), next = true)
    }

    private fun drawCompactNotification(canvas: Canvas, accentArgb: Int) {
        val cy = pillRect.centerY()
        val iconR = dp(16f)
        val iconCx = pillRect.left + dp(12f) + iconR
        val bmp = state.icon
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, cy, iconR)
        } else {
            chipPaint.color = withAlpha(accentArgb, 60)
            canvas.drawCircle(iconCx, cy, iconR, chipPaint)
        }

        val pipR = dp(13f)
        val pipCx = pillRect.right - dp(12f) - pipR
        chipPaint.color = Color.argb(28, 255, 255, 255)
        canvas.drawCircle(pipCx, cy, pipR, chipPaint)
        drawFloatingWindowGlyph(canvas, pipCx, cy, dp(6.5f), accentArgb)

        val textLeft = iconCx + iconR + dp(10f)
        val textMaxW = (pipCx - pipR - dp(8f) - textLeft).coerceAtLeast(dp(40f))

        titlePaint.textSize = dp(12.5f)
        titlePaint.color = Color.WHITE
        val topLine = TextUtils.ellipsize(
            state.title.ifBlank { "Notificación" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(topLine, textLeft, cy - dp(2.5f), titlePaint)

        subtitlePaint.textSize = dp(11f)
        subtitlePaint.color = Color.argb(185, 214, 222, 236)
        val bottomLine = TextUtils.ellipsize(
            state.subtitle.ifBlank { "Toca: flotante · Sostén: leer" },
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(bottomLine, textLeft, cy + dp(12.5f), subtitlePaint)
    }

    private fun drawExpandedNotification(canvas: Canvas, primaryArgb: Int, accentArgb: Int) {
        val padH = dp(16f)
        val iconR = dp(18f)
        val iconCx = pillRect.left + padH + iconR
        val iconCy = pillRect.top + dp(16f) + iconR

        val bmp = state.icon
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, iconCy, iconR)
        } else {
            chipPaint.color = withAlpha(accentArgb, 55)
            canvas.drawCircle(iconCx, iconCy, iconR, chipPaint)
        }

        val textLeft = iconCx + iconR + dp(11f)
        val textMaxW = (pillRect.right - padH - textLeft).coerceAtLeast(dp(60f))

        titlePaint.textSize = dp(13.5f)
        titlePaint.color = Color.WHITE
        val header = TextUtils.ellipsize(
            state.title.ifBlank { "Notificación" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(header, textLeft, iconCy - dp(3f), titlePaint)

        subtitlePaint.textSize = dp(11.5f)
        subtitlePaint.color = Color.argb(190, 220, 226, 238)
        val sub = TextUtils.ellipsize(
            state.subtitle,
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        if (sub.isNotBlank()) {
            canvas.drawText(sub, textLeft, iconCy + dp(12f), subtitlePaint)
        }

        // Vista previa corta del mensaje (o aviso oculto si está bloqueado)
        val previewY = iconCy + iconR + dp(16f)
        subtitlePaint.textSize = dp(12f)
        subtitlePaint.color = Color.argb(225, 238, 242, 248)
        val previewLine = TextUtils.ellipsize(
            state.preview.ifBlank { "Toca abajo para abrir en ventana flotante" },
            subtitlePaint,
            (pillRect.width() - padH * 2f).coerceAtLeast(dp(60f)),
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(previewLine, pillRect.left + padH, previewY, subtitlePaint)

        // Fila de botones: Ventana flotante + acciones rápidas reales (ej. Marcar leído)
        val btnH = dp(32f)
        val btnTop = pillRect.bottom - dp(14f) - btnH
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

    private fun drawExpandedHub(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val padH = dp(14f)
        val topRowTop = pillRect.top + dp(16f)
        val tileSize = dp(48f)
        val gap = ((pillRect.width() - padH * 2f) - tileSize * 5f) / 4f
        val labels = listOf("Buscar", "Linterna", "Silencio", "Captura", "Clips")

        for (i in 0 until 5) {
            val left = pillRect.left + padH + i * (tileSize + gap)
            hubTileRects[i].set(left, topRowTop, left + tileSize, topRowTop + tileSize + dp(14f))

            val isActive = (i == 1 && state.flashlightOn) || (i == 2 && state.silentOn)
            val cx = left + tileSize / 2f
            val cy = topRowTop + tileSize / 2f

            chipPaint.color = when {
                i == 1 && state.flashlightOn -> withAlpha(COLOR_AMBER, 82)
                i == 2 && state.silentOn -> withAlpha(primaryArgb, 84)
                i == 0 -> withAlpha(primaryArgb, 48)
                else -> Color.argb(26, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, dp(21f), chipPaint)

            val tint = when {
                i == 1 && state.flashlightOn -> COLOR_AMBER
                i == 2 && state.silentOn -> primaryArgb
                i == 0 -> primaryArgb
                i == 4 -> secondaryArgb
                else -> Color.WHITE
            }
            when (i) {
                0 -> drawSearchGlyph(canvas, cx, cy, dp(8.5f), tint)
                1 -> drawFlashlightGlyph(canvas, cx, cy, dp(8.5f), tint)
                2 -> drawBellOffGlyph(canvas, cx, cy, dp(8.5f), tint)
                3 -> drawScreenshotGlyph(canvas, cx, cy, dp(8f), tint)
                4 -> drawClipboardGlyph(canvas, cx, cy, dp(8f), tint)
            }

            badgePaint.textSize = dp(9.8f)
            badgePaint.color = if (isActive) Color.WHITE else Color.argb(185, 215, 222, 236)
            canvas.drawText(labels[i], cx, topRowTop + tileSize + dp(10f), badgePaint)
        }

        // Fila inferior: Cronómetro integrado rápido
        val rowH = dp(34f)
        val rowTop = pillRect.bottom - dp(14f) - rowH
        val rowRect = RectF(pillRect.left + padH, rowTop, pillRect.right - padH, rowTop + rowH)
        chipPaint.color = Color.argb(20, 255, 255, 255)
        canvas.drawRoundRect(rowRect, rowH / 2f, rowH / 2f, chipPaint)

        val clockCx = rowRect.left + dp(18f)
        val rowCy = rowRect.centerY()
        drawClockGlyph(canvas, clockCx, rowCy, dp(7.5f), if (state.stopwatchRunning) COLOR_AMBER else Color.WHITE)

        titlePaint.textSize = dp(13f)
        titlePaint.color = Color.WHITE
        val swText = "Cronómetro  ${state.stopwatchLabel}"
        val swBase = rowCy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(swText, clockCx + dp(14f), swBase, titlePaint)

        val btnW = dp(64f)
        val btnH = dp(24f)
        hubStopwatchResetRect.set(rowRect.right - dp(6f) - dp(54f), rowCy - btnH / 2f, rowRect.right - dp(6f), rowCy + btnH / 2f)
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

    private fun drawCharging(canvas: Canvas) {
        val cy = pillRect.centerY()
        val boltCx = pillRect.left + dp(18f)
        val pulseAlpha = (160 + 95 * sin(pulsePhase * Math.PI * 2.0)).toInt().coerceIn(90, 255)
        drawBoltGlyph(canvas, boltCx, cy, dp(8f), withAlpha(COLOR_GREEN, pulseAlpha))

        titlePaint.textSize = dp(12f)
        titlePaint.color = Color.WHITE
        val label = state.title.ifBlank { "Cargando" }
        val base = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(label, boltCx + dp(13f), base, titlePaint)

        val pctText = "${state.batteryPercent.coerceIn(0, 100)}%"
        titlePaint.color = COLOR_GREEN
        val pctW = titlePaint.measureText(pctText)
        canvas.drawText(pctText, pillRect.right - dp(14f) - pctW, base, titlePaint)
    }

    private fun drawBluetooth(canvas: Canvas) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(18f)
        drawBluetoothGlyph(canvas, iconCx, cy, dp(7.5f), COLOR_BLUE)

        val badgeW = dp(58f)
        val textLeft = iconCx + dp(14f)
        val maxW = (pillRect.right - badgeW - dp(12f) - textLeft).coerceAtLeast(dp(36f))
        titlePaint.textSize = dp(11.8f)
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
        canvas.drawText(tag, pillRect.right - dp(12f) - tw, base, subtitlePaint)
    }

    private fun drawCall(canvas: Canvas) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(18f)
        val pulseAlpha = (170 + 85 * sin(pulsePhase * Math.PI * 2.0)).toInt().coerceIn(100, 255)
        chipPaint.color = withAlpha(COLOR_GREEN, 48)
        canvas.drawCircle(iconCx, cy, dp(11.5f), chipPaint)
        drawPhoneGlyph(canvas, iconCx, cy, dp(6.8f), withAlpha(COLOR_GREEN, pulseAlpha))

        val timeText = state.badge.ifBlank { "00:00" }
        titlePaint.textSize = dp(11.5f)
        titlePaint.color = COLOR_GREEN
        val timeW = titlePaint.measureText(timeText)
        val base = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(timeText, pillRect.right - dp(13f) - timeW, base, titlePaint)

        val textLeft = iconCx + dp(16f)
        val maxW = (pillRect.right - dp(20f) - timeW - textLeft).coerceAtLeast(dp(30f))
        titlePaint.color = Color.WHITE
        val caller = TextUtils.ellipsize(
            state.title.ifBlank { "En llamada" },
            titlePaint,
            maxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(caller, textLeft, base, titlePaint)
    }

    private fun drawStopwatch(canvas: Canvas) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(17f)
        drawClockGlyph(canvas, iconCx, cy, dp(7f), COLOR_AMBER)

        titlePaint.textSize = dp(12f)
        titlePaint.color = Color.WHITE
        val base = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(state.title.ifBlank { "Cronómetro" }, iconCx + dp(14f), base, titlePaint)

        titlePaint.color = COLOR_AMBER
        val time = state.stopwatchLabel
        val tw = titlePaint.measureText(time)
        canvas.drawText(time, pillRect.right - dp(14f) - tw, base, titlePaint)
    }

    private fun drawSilentTimer(canvas: Canvas, accentArgb: Int) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(17f)
        drawBellOffGlyph(canvas, iconCx, cy, dp(6.8f), accentArgb)

        titlePaint.textSize = dp(11.5f)
        titlePaint.color = Color.WHITE
        val textLeft = iconCx + dp(14f)
        val maxW = (pillRect.right - dp(12f) - textLeft).coerceAtLeast(dp(24f))
        val text = TextUtils.ellipsize(state.title, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(text, textLeft, baseline, titlePaint)
    }

    private fun drawActionAlert(canvas: Canvas, accentArgb: Int, isScreenshot: Boolean) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(18f)
        if (isScreenshot) {
            drawScreenshotGlyph(canvas, iconCx, cy, dp(7f), accentArgb)
        } else {
            drawClipboardGlyph(canvas, iconCx, cy, dp(7f), accentArgb)
        }

        val chipW = dp(44f)
        val chipH = dp(22f)
        val chipRect = RectF(
            pillRect.right - dp(9f) - chipW,
            cy - chipH / 2f,
            pillRect.right - dp(9f),
            cy + chipH / 2f
        )
        chipPaint.color = withAlpha(accentArgb, 75)
        canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, chipPaint)
        badgePaint.textSize = dp(10.5f)
        badgePaint.color = Color.WHITE
        val chipBaseline = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(state.badge.ifBlank { "Abrir" }, chipRect.centerX(), chipBaseline, badgePaint)

        val textLeft = iconCx + dp(15f)
        val textMaxW = (chipRect.left - dp(6f) - textLeft).coerceAtLeast(dp(30f))
        titlePaint.textSize = dp(12f)
        titlePaint.color = Color.WHITE
        val label = TextUtils.ellipsize(state.title, titlePaint, textMaxW, TextUtils.TruncateAt.END).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(label, textLeft, baseline, titlePaint)
    }

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
    }
}
