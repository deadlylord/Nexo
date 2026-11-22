package com.stree.nexo.panel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.compose.ui.graphics.toArgb
import com.stree.nexo.data.ActivatorStyle
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.theme.visualSpec

enum class EdgeLiveMode {
    IDLE,
    CLIPBOARD,
    SCREENSHOT,
    NOTIFICATION,
    LAST_APP_PREVIEW
}

/**
 * Barra lateral viva: en reposo dibuja la cápsula delgada sincronizada con el
 * tema; al detectar copia, captura, notificación o al jalar para volver a la
 * última app, se expande suavemente con onda luminosa e icono.
 */
class EdgeHandleView(context: Context) : View(context) {

    var side: PanelSide = PanelSide.END
        set(value) {
            field = value
            invalidate()
        }

    var liveMode: EdgeLiveMode = EdgeLiveMode.IDLE
        private set

    private var iconBitmap: Bitmap? = null
    private var customAccentColor: Int? = null
    private var expandProgress = 0f
    private var wavePhase = 0f
    private var holdProgress = 0f

    private var expandAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null
    private var holdAnimator: ValueAnimator? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pillBounds = RectF()
    private val iconRect = RectF()
    private val clipPath = Path()

    fun showLiveAlert(
        mode: EdgeLiveMode,
        bitmap: Bitmap? = null,
        accentColor: Int? = null
    ) {
        liveMode = mode
        iconBitmap = bitmap
        customAccentColor = accentColor
        holdProgress = 0f
        holdAnimator?.cancel()
        animateExpand(to = 1f)
        startWave()
    }

    fun showLastAppPreview(appIcon: Bitmap?, holdDurationMs: Long) {
        liveMode = EdgeLiveMode.LAST_APP_PREVIEW
        iconBitmap = appIcon
        customAccentColor = null
        waveAnimator?.cancel()
        wavePhase = 0f
        animateExpand(to = 1f, durationMs = 160L)
        holdAnimator?.cancel()
        holdAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = holdDurationMs.coerceAtLeast(120L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                holdProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun updatePreviewIcon(appIcon: Bitmap?) {
        if (liveMode == EdgeLiveMode.LAST_APP_PREVIEW && appIcon != null) {
            iconBitmap = appIcon
            invalidate()
        }
    }

    fun collapseToIdle(onFinished: (() -> Unit)? = null) {
        holdAnimator?.cancel()
        waveAnimator?.cancel()
        if (liveMode == EdgeLiveMode.IDLE && expandProgress <= 0.01f) {
            onFinished?.invoke()
            return
        }
        animateExpand(to = 0f, durationMs = 210L) {
            liveMode = EdgeLiveMode.IDLE
            iconBitmap = null
            customAccentColor = null
            holdProgress = 0f
            wavePhase = 0f
            invalidate()
            onFinished?.invoke()
        }
    }

    fun isShowingLiveAlert(): Boolean =
        liveMode == EdgeLiveMode.CLIPBOARD ||
            liveMode == EdgeLiveMode.SCREENSHOT ||
            liveMode == EdgeLiveMode.NOTIFICATION

    private fun animateExpand(to: Float, durationMs: Long = 230L, onEnd: (() -> Unit)? = null) {
        expandAnimator?.cancel()
        expandAnimator = ValueAnimator.ofFloat(expandProgress, to).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                expandProgress = it.animatedValue as Float
                invalidate()
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }
                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) onEnd()
                    }
                })
            }
            start()
        }
    }

    private fun startWave() {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1350L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                wavePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        expandAnimator?.cancel()
        waveAnimator?.cancel()
        holdAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (Prefs.showActivationArea(context) && expandProgress < 0.05f) {
            canvas.drawColor(Color.argb(65, 80, 160, 255))
        }

        val spec = Prefs.panelStyle(context).visualSpec(light = Prefs.panelLightBackground(context))
        val syncTheme = Prefs.activatorSyncTheme(context)
        val primaryArgb = if (syncTheme) spec.primary.toArgb() else Prefs.activatorColor(context)
        val secondaryArgb = if (syncTheme) spec.secondary.toArgb() else shiftColorBrighter(primaryArgb)
        val surfaceArgb = spec.surface.toArgb()

        val activeAccent = customAccentColor ?: when (liveMode) {
            EdgeLiveMode.CLIPBOARD -> spec.primary.toArgb()
            EdgeLiveMode.SCREENSHOT -> spec.secondary.toArgb()
            EdgeLiveMode.NOTIFICATION -> primaryArgb
            EdgeLiveMode.LAST_APP_PREVIEW -> spec.primary.toArgb()
            EdgeLiveMode.IDLE -> primaryArgb
        }

        val style = Prefs.activatorStyle(context)
        val compact = style == ActivatorStyle.CIRCLE || style == ActivatorStyle.ICON
        val idleW = if (compact) dp(24f).coerceAtMost(width.toFloat())
            else dp(Prefs.indicatorWidth(context).toFloat()).coerceAtMost(width.toFloat())
        val idleH = if (compact) idleW.coerceAtMost(height.toFloat())
            else dp(Prefs.indicatorHeight(context).toFloat()).coerceAtMost(height.toFloat())

        val expandedW = dp(if (liveMode == EdgeLiveMode.LAST_APP_PREVIEW) 50f else 46f)
            .coerceAtMost(width - dp(6f))
        val expandedH = dp(if (liveMode == EdgeLiveMode.LAST_APP_PREVIEW) 50f else 54f)
            .coerceAtMost(height - dp(6f))

        val curW = lerp(idleW, expandedW, expandProgress)
        val curH = lerp(idleH, expandedH, expandProgress)
        val edgeGap = dp(4f) * expandProgress
        val left = if (side == PanelSide.START) edgeGap else width - curW - edgeGap
        val top = (height - curH) / 2f

        pillBounds.set(left, top, left + curW, top + curH)

        // Onda luminosa expansiva durante avisos vivos
        if (expandProgress > 0.15f && liveMode != EdgeLiveMode.LAST_APP_PREVIEW) {
            val waveSpread = dp(10f) * wavePhase
            val waveAlpha = ((1f - wavePhase) * 0.42f * expandProgress * 255).toInt().coerceIn(0, 255)
            glowPaint.color = withAlpha(activeAccent, waveAlpha)
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = dp(2.2f)
            val waveRect = RectF(
                pillBounds.left - waveSpread,
                pillBounds.top - waveSpread,
                pillBounds.right + waveSpread,
                pillBounds.bottom + waveSpread
            )
            val wr = (curW / 2f) + waveSpread
            canvas.drawRoundRect(waveRect, wr, wr, glowPaint)
        }

        // Fondo de la cápsula con degradado translúcido del tema
        val cornerRadius = if (expandProgress > 0.5f) minOf(curW, curH) / 2f else curW / 2f
        if (expandProgress > 0.08f) {
            val topColor = blendColors(surfaceArgb, activeAccent, 0.34f * expandProgress)
            val botColor = blendColors(surfaceArgb, secondaryArgb, 0.22f * expandProgress)
            fillPaint.shader = LinearGradient(
                pillBounds.left, pillBounds.top, pillBounds.right, pillBounds.bottom,
                intArrayOf(withAlpha(topColor, 236), withAlpha(botColor, 224)),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            val baseAlpha = (Prefs.activatorOpacity(context) * 235f).toInt().coerceIn(60, 255)
            fillPaint.shader = LinearGradient(
                pillBounds.left, pillBounds.top, pillBounds.left, pillBounds.bottom,
                intArrayOf(withAlpha(primaryArgb, baseAlpha), withAlpha(secondaryArgb, baseAlpha)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, fillPaint)

        // Borde especular luminoso
        rimPaint.strokeWidth = dp(if (expandProgress > 0.2f) 1.4f else 0.9f)
        rimPaint.shader = LinearGradient(
            pillBounds.left, pillBounds.top, pillBounds.right, pillBounds.bottom,
            intArrayOf(
                withAlpha(Color.WHITE, if (expandProgress > 0.2f) 175 else 110),
                withAlpha(activeAccent, if (expandProgress > 0.2f) 210 else 90)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, rimPaint)

        val cx = pillBounds.centerX()
        val cy = pillBounds.centerY()

        // Detalle de reposo (puntos o flecha) que se desvanece al expandir
        if (expandProgress < 0.7f) {
            val idleAlpha = ((1f - expandProgress / 0.7f) * 235f).toInt().coerceIn(0, 255)
            dotPaint.color = withAlpha(Color.WHITE, idleAlpha)
            if (style == ActivatorStyle.PILL) {
                val gap = dp(9f).coerceAtMost(idleH / 7f)
                val dotR = (idleW * 0.12f).coerceAtLeast(dp(1.1f))
                for (index in -1..1) {
                    canvas.drawCircle(cx, cy + index * gap, dotR, dotPaint)
                }
            } else if (style == ActivatorStyle.ICON) {
                iconPaint.color = withAlpha(Color.WHITE, idleAlpha)
                iconPaint.strokeWidth = dp(1.7f)
                val dir = if (side == PanelSide.START) 1f else -1f
                val path = Path().apply {
                    moveTo(cx - dir * idleW * .12f, cy - idleH * .22f)
                    lineTo(cx + dir * idleW * .14f, cy)
                    lineTo(cx - dir * idleW * .12f, cy + idleH * .22f)
                }
                canvas.drawPath(path, iconPaint)
            }
        }

        // Contenido expandido (icono de evento o vista previa de última app)
        if (expandProgress > 0.35f) {
            val contentAlpha = (((expandProgress - 0.35f) / 0.65f).coerceIn(0f, 1f) * 255f).toInt()
            when (liveMode) {
                EdgeLiveMode.CLIPBOARD -> drawClipboardGlyph(canvas, cx, cy, dp(11f), contentAlpha)
                EdgeLiveMode.SCREENSHOT -> drawScreenshotGlyph(canvas, cx, cy, dp(11f), contentAlpha)
                EdgeLiveMode.NOTIFICATION -> {
                    val bmp = iconBitmap
                    if (bmp != null) {
                        drawCircularBitmap(canvas, bmp, cx, cy, dp(13.5f), contentAlpha)
                    } else {
                        drawBellGlyph(canvas, cx, cy, dp(10.5f), contentAlpha)
                    }
                }
                EdgeLiveMode.LAST_APP_PREVIEW -> {
                    val bmp = iconBitmap
                    if (bmp != null) {
                        drawCircularBitmap(canvas, bmp, cx, cy, dp(14.5f), contentAlpha)
                    } else {
                        drawSwapGlyph(canvas, cx, cy, dp(10f), contentAlpha)
                    }
                    // Anillo de progreso que indica cuándo salta a la última app
                    val ringRadius = dp(20f)
                    val ringRect = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
                    progressPaint.strokeWidth = dp(2.4f)
                    progressPaint.color = withAlpha(activeAccent, contentAlpha)
                    canvas.drawArc(ringRect, -90f, 360f * holdProgress, false, progressPaint)
                }
                EdgeLiveMode.IDLE -> Unit
            }
        }
    }

    private fun drawCircularBitmap(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, radius: Float, alpha: Int) {
        bitmapPaint.alpha = alpha
        iconRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bmp, null, iconRect, bitmapPaint)
        canvas.restore()
    }

    private fun drawClipboardGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
        iconPaint.color = withAlpha(Color.WHITE, alpha)
        iconPaint.strokeWidth = dp(1.9f)
        val body = RectF(cx - r * 0.72f, cy - r * 0.75f, cx + r * 0.72f, cy + r * 0.92f)
        canvas.drawRoundRect(body, dp(3f), dp(3f), iconPaint)
        val clip = RectF(cx - r * 0.34f, cy - r * 1.02f, cx + r * 0.34f, cy - r * 0.56f)
        canvas.drawRoundRect(clip, dp(2f), dp(2f), iconPaint)
        canvas.drawLine(cx - r * 0.36f, cy + r * 0.02f, cx + r * 0.36f, cy + r * 0.02f, iconPaint)
        canvas.drawLine(cx - r * 0.36f, cy + r * 0.42f, cx + r * 0.22f, cy + r * 0.42f, iconPaint)
    }

    private fun drawScreenshotGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
        iconPaint.color = withAlpha(Color.WHITE, alpha)
        iconPaint.strokeWidth = dp(2f)
        val b = r * 0.85f
        val arm = r * 0.42f
        val path = Path().apply {
            moveTo(cx - b, cy - b + arm); lineTo(cx - b, cy - b); lineTo(cx - b + arm, cy - b)
            moveTo(cx + b - arm, cy - b); lineTo(cx + b, cy - b); lineTo(cx + b, cy - b + arm)
            moveTo(cx + b, cy + b - arm); lineTo(cx + b, cy + b); lineTo(cx + b - arm, cy + b)
            moveTo(cx - b + arm, cy + b); lineTo(cx - b, cy + b); lineTo(cx - b, cy + b - arm)
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawCircle(cx, cy, r * 0.26f, iconPaint)
    }

    private fun drawBellGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
        iconPaint.color = withAlpha(Color.WHITE, alpha)
        iconPaint.strokeWidth = dp(1.9f)
        val path = Path().apply {
            moveTo(cx - r * 0.65f, cy + r * 0.45f)
            lineTo(cx - r * 0.52f, cy - r * 0.25f)
            quadTo(cx, cy - r * 1.05f, cx + r * 0.52f, cy - r * 0.25f)
            lineTo(cx + r * 0.65f, cy + r * 0.45f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawCircle(cx, cy + r * 0.78f, dp(1.6f), dotPaint.apply { color = withAlpha(Color.WHITE, alpha) })
    }

    private fun drawSwapGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
        iconPaint.color = withAlpha(Color.WHITE, alpha)
        iconPaint.strokeWidth = dp(2f)
        canvas.drawLine(cx - r * 0.6f, cy - r * 0.25f, cx + r * 0.6f, cy - r * 0.25f, iconPaint)
        canvas.drawLine(cx + r * 0.2f, cy - r * 0.6f, cx + r * 0.6f, cy - r * 0.25f, iconPaint)
        canvas.drawLine(cx - r * 0.6f, cy + r * 0.35f, cx + r * 0.6f, cy + r * 0.35f, iconPaint)
        canvas.drawLine(cx - r * 0.2f, cy + r * 0.7f, cx - r * 0.6f, cy + r * 0.35f, iconPaint)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.rgb(
            (Color.red(base) * inv + Color.red(overlay) * ratio).toInt().coerceIn(0, 255),
            (Color.green(base) * inv + Color.green(overlay) * ratio).toInt().coerceIn(0, 255),
            (Color.blue(base) * inv + Color.blue(overlay) * ratio).toInt().coerceIn(0, 255)
        )
    }

    private fun shiftColorBrighter(color: Int): Int = blendColors(color, Color.WHITE, 0.28f)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
