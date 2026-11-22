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
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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
    CLIPBOARD,
    SCREENSHOT,
    SILENT_TIMER
}

interface IslandInteractionListener {
    fun onTapMain(mode: IslandMode)
    fun onMediaPrev()
    fun onMediaPlayPause()
    fun onMediaNext()
    fun onMediaOpenApp()
    fun onSwipeUpDismiss()
}

/**
 * Isla Dinámica de Nexo: cápsula superior sincronizada con el tema activo
 * que muestra música, notificaciones (tocar abre en ventana flotante),
 * texto copiado, capturas y temporizador de silencio.
 */
class DynamicIslandView(context: Context) : View(context) {

    var listener: IslandInteractionListener? = null

    var mode: IslandMode = IslandMode.HIDDEN
        private set

    private var primaryText: String = ""
    private var secondaryText: String = ""
    private var badgeText: String = ""
    private var iconBitmap: Bitmap? = null
    private var customAccent: Int? = null
    private var isPlaying: Boolean = false

    private var morphProgress = 1f
    private var pulsePhase = 0f
    private var eqPhase = 0f

    private var morphAnimator: ValueAnimator? = null
    private var loopAnimator: ValueAnimator? = null

    private var downX = 0f
    private var downY = 0f
    private var swipedUp = false

    private val prevRect = RectF()
    private val playRect = RectF()
    private val nextRect = RectF()
    private val mediaHeaderRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
        color = Color.argb(195, 232, 236, 245)
        typeface = Typeface.DEFAULT
    }
    private val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val pillRect = RectF()
    private val clipPath = Path()
    private val iconRect = RectF()

    fun desiredWidthDp(targetMode: IslandMode = mode): Int = when (targetMode) {
        IslandMode.HIDDEN -> 1
        IslandMode.IDLE_PILL -> 114
        IslandMode.SILENT_TIMER -> 178
        IslandMode.COMPACT_MEDIA -> 214
        IslandMode.CLIPBOARD -> 216
        IslandMode.SCREENSHOT -> 226
        IslandMode.NOTIFICATION -> 294
        IslandMode.EXPANDED_MEDIA -> 318
    }

    fun desiredHeightDp(targetMode: IslandMode = mode): Int = when (targetMode) {
        IslandMode.HIDDEN -> 1
        IslandMode.IDLE_PILL -> 28
        IslandMode.SILENT_TIMER -> 34
        IslandMode.COMPACT_MEDIA -> 38
        IslandMode.CLIPBOARD -> 40
        IslandMode.SCREENSHOT -> 40
        IslandMode.NOTIFICATION -> 56
        IslandMode.EXPANDED_MEDIA -> 110
    }

    fun showState(
        newMode: IslandMode,
        title: String = "",
        subtitle: String = "",
        badge: String = "",
        icon: Bitmap? = null,
        accentColor: Int? = null,
        playing: Boolean = false
    ) {
        val modeChanged = mode != newMode
        mode = newMode
        primaryText = title
        secondaryText = subtitle
        badgeText = badge
        iconBitmap = icon
        customAccent = accentColor
        isPlaying = playing

        if (modeChanged) {
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(0.72f, 1f).apply {
                duration = if (newMode == IslandMode.EXPANDED_MEDIA) 280L else 240L
                interpolator = OvershootInterpolator(0.85f)
                addUpdateListener {
                    morphProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            morphProgress = 1f
        }

        val needsLoop = newMode == IslandMode.NOTIFICATION ||
            newMode == IslandMode.CLIPBOARD ||
            newMode == IslandMode.SCREENSHOT ||
            ((newMode == IslandMode.COMPACT_MEDIA || newMode == IslandMode.EXPANDED_MEDIA) && playing)
        if (needsLoop) startLoopAnimation() else stopLoopAnimation()
        invalidate()
    }

    fun hideImmediate() {
        mode = IslandMode.HIDDEN
        stopLoopAnimation()
        morphAnimator?.cancel()
        invalidate()
    }

    private fun startLoopAnimation() {
        if (loopAnimator?.isRunning == true) return
        loopAnimator?.cancel()
        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400L
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
        morphAnimator?.cancel()
        stopLoopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == IslandMode.HIDDEN) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swipedUp = false
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (dy < -dp(18f) && abs(dy) > abs(event.x - downX)) {
                    swipedUp = true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                if (swipedUp) {
                    listener?.onSwipeUpDismiss()
                    return true
                }
                if (mode == IslandMode.EXPANDED_MEDIA) {
                    when {
                        prevRect.contains(event.x, event.y) -> listener?.onMediaPrev()
                        playRect.contains(event.x, event.y) -> listener?.onMediaPlayPause()
                        nextRect.contains(event.x, event.y) -> listener?.onMediaNext()
                        mediaHeaderRect.contains(event.x, event.y) -> listener?.onMediaOpenApp()
                        else -> listener?.onTapMain(mode)
                    }
                } else {
                    listener?.onTapMain(mode)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == IslandMode.HIDDEN || width <= 2 || height <= 2) return

        val spec = Prefs.panelStyle(context).visualSpec(light = false)
        val primaryArgb = spec.primary.toArgb()
        val secondaryArgb = spec.secondary.toArgb()
        val accentArgb = customAccent ?: primaryArgb

        val pad = dp(4f)
        val availW = (width - pad * 2f).coerceAtLeast(dp(24f))
        val availH = (height - pad * 2f).coerceAtLeast(dp(20f))

        val scale = morphProgress.coerceIn(0.65f, 1.04f)
        val drawW = (availW * scale).coerceAtMost(availW)
        val drawH = (availH * (0.85f + 0.15f * scale)).coerceAtMost(availH)
        val left = (width - drawW) / 2f
        val top = pad
        pillRect.set(left, top, left + drawW, top + drawH)

        val cornerRadius = if (mode == IslandMode.EXPANDED_MEDIA) dp(28f) else drawH / 2f

        // Onda luminosa suave en avisos vivos (notificación / copia / captura)
        if (mode == IslandMode.NOTIFICATION || mode == IslandMode.CLIPBOARD || mode == IslandMode.SCREENSHOT) {
            val spread = dp(3.5f) * pulsePhase
            val glowAlpha = ((1f - pulsePhase) * 0.45f * 255).toInt().coerceIn(0, 255)
            glowPaint.color = withAlpha(accentArgb, glowAlpha)
            glowPaint.strokeWidth = dp(1.8f)
            val glowRect = RectF(
                pillRect.left - spread,
                pillRect.top - spread * 0.5f,
                pillRect.right + spread,
                pillRect.bottom + spread * 0.5f
            )
            canvas.drawRoundRect(glowRect, cornerRadius + spread, cornerRadius + spread, glowPaint)
        }

        // Fondo negro profundo tintado con el tema activo de Nexo
        val topTint = blendColors(Color.rgb(10, 11, 16), spec.surface.toArgb(), 0.36f)
        val botTint = blendColors(Color.rgb(8, 9, 13), accentArgb, 0.14f)
        bgPaint.shader = LinearGradient(
            pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
            intArrayOf(withAlpha(topTint, 245), withAlpha(botTint, 245)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)

        // Borde especular luminoso sincronizado con el tema
        rimPaint.strokeWidth = dp(1.1f)
        rimPaint.shader = LinearGradient(
            pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
            intArrayOf(
                withAlpha(primaryArgb, 190),
                withAlpha(Color.WHITE, 90),
                withAlpha(secondaryArgb, 180)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, rimPaint)

        when (mode) {
            IslandMode.IDLE_PILL -> drawIdlePill(canvas, primaryArgb, secondaryArgb)
            IslandMode.COMPACT_MEDIA -> drawCompactMedia(canvas, primaryArgb, secondaryArgb)
            IslandMode.EXPANDED_MEDIA -> drawExpandedMedia(canvas, primaryArgb, secondaryArgb)
            IslandMode.NOTIFICATION -> drawNotification(canvas, accentArgb, primaryArgb)
            IslandMode.CLIPBOARD -> drawActionAlert(canvas, primaryArgb, isScreenshot = false)
            IslandMode.SCREENSHOT -> drawActionAlert(canvas, secondaryArgb, isScreenshot = true)
            IslandMode.SILENT_TIMER -> drawSilentTimer(canvas, primaryArgb)
            IslandMode.HIDDEN -> Unit
        }
    }

    private fun drawIdlePill(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val cy = pillRect.centerY()
        val dotR = dp(3.2f)
        chipPaint.shader = LinearGradient(
            pillRect.left, cy, pillRect.right, cy,
            intArrayOf(primaryArgb, secondaryArgb),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(pillRect.left + dp(14f), cy, dotR, chipPaint)
        chipPaint.shader = null
        titlePaint.textSize = dp(10.5f)
        titlePaint.color = Color.argb(215, 242, 245, 255)
        val text = "Nexo"
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(text, pillRect.centerX() - titlePaint.measureText(text) / 2f + dp(4f), baseline, titlePaint)
    }

    private fun drawCompactMedia(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val cy = pillRect.centerY()
        val iconR = dp(11.5f)
        val iconCx = pillRect.left + dp(18f)
        val bmp = iconBitmap
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, cy, iconR)
        } else {
            chipPaint.color = withAlpha(primaryArgb, 55)
            canvas.drawCircle(iconCx, cy, iconR, chipPaint)
            drawMusicNoteGlyph(canvas, iconCx, cy, dp(6.5f), Color.WHITE)
        }

        // Ecualizador a la derecha
        val eqRight = pillRect.right - dp(14f)
        drawEqualizer(canvas, eqRight - dp(14f), cy, primaryArgb, secondaryArgb)

        // Título de la canción al centro
        val textLeft = iconCx + iconR + dp(8f)
        val textMaxW = (eqRight - dp(22f) - textLeft).coerceAtLeast(dp(24f))
        titlePaint.textSize = dp(12f)
        titlePaint.color = Color.WHITE
        val ellipsized = TextUtils.ellipsize(
            primaryText.ifBlank { "Reproduciendo" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(ellipsized, textLeft, baseline, titlePaint)
    }

    private fun drawExpandedMedia(canvas: Canvas, primaryArgb: Int, secondaryArgb: Int) {
        val padH = dp(16f)
        val headerTop = pillRect.top + dp(12f)
        val headerBottom = pillRect.top + dp(56f)
        mediaHeaderRect.set(pillRect.left, pillRect.top, pillRect.right, headerBottom)

        val iconR = dp(17f)
        val iconCx = pillRect.left + padH + iconR
        val iconCy = (headerTop + headerBottom) / 2f
        val bmp = iconBitmap
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, iconCy, iconR)
        } else {
            chipPaint.color = withAlpha(primaryArgb, 60)
            canvas.drawCircle(iconCx, iconCy, iconR, chipPaint)
            drawMusicNoteGlyph(canvas, iconCx, iconCy, dp(9f), Color.WHITE)
        }

        val eqStart = pillRect.right - padH - dp(16f)
        drawEqualizer(canvas, eqStart, iconCy, primaryArgb, secondaryArgb)

        val textLeft = iconCx + iconR + dp(10f)
        val textMaxW = (eqStart - dp(10f) - textLeft).coerceAtLeast(dp(40f))

        titlePaint.textSize = dp(13.5f)
        titlePaint.color = Color.WHITE
        val title = TextUtils.ellipsize(
            primaryText.ifBlank { "Reproducción multimedia" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(title, textLeft, iconCy - dp(2f), titlePaint)

        subtitlePaint.textSize = dp(11.5f)
        subtitlePaint.color = Color.argb(185, 222, 228, 240)
        val artist = TextUtils.ellipsize(
            secondaryText.ifBlank { "Toca arriba para abrir la app" },
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(artist, textLeft, iconCy + dp(13f), subtitlePaint)

        // Fila inferior de controles multimedia
        val controlsCy = pillRect.bottom - dp(26f)
        val btnH = dp(34f)
        val btnW = dp(64f)
        val gap = dp(12f)
        val totalW = btnW * 3f + gap * 2f
        val startX = pillRect.centerX() - totalW / 2f

        prevRect.set(startX, controlsCy - btnH / 2f, startX + btnW, controlsCy + btnH / 2f)
        playRect.set(prevRect.right + gap, controlsCy - btnH / 2f, prevRect.right + gap + btnW, controlsCy + btnH / 2f)
        nextRect.set(playRect.right + gap, controlsCy - btnH / 2f, playRect.right + gap + btnW, controlsCy + btnH / 2f)

        // Botón Anterior
        chipPaint.color = Color.argb(30, 255, 255, 255)
        canvas.drawRoundRect(prevRect, btnH / 2f, btnH / 2f, chipPaint)
        drawSkipGlyph(canvas, prevRect.centerX(), prevRect.centerY(), dp(7.5f), next = false)

        // Botón Play / Pausa con degradado del tema
        chipPaint.shader = LinearGradient(
            playRect.left, playRect.top, playRect.right, playRect.bottom,
            intArrayOf(primaryArgb, secondaryArgb),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(playRect, btnH / 2f, btnH / 2f, chipPaint)
        chipPaint.shader = null
        if (isPlaying) {
            drawPauseGlyph(canvas, playRect.centerX(), playRect.centerY(), dp(7.5f))
        } else {
            drawPlayGlyph(canvas, playRect.centerX(), playRect.centerY(), dp(7.5f))
        }

        // Botón Siguiente
        chipPaint.color = Color.argb(30, 255, 255, 255)
        canvas.drawRoundRect(nextRect, btnH / 2f, btnH / 2f, chipPaint)
        drawSkipGlyph(canvas, nextRect.centerX(), nextRect.centerY(), dp(7.5f), next = true)
    }

    private fun drawNotification(canvas: Canvas, accentArgb: Int, primaryArgb: Int) {
        val cy = pillRect.centerY()
        val iconR = dp(16f)
        val iconCx = pillRect.left + dp(12f) + iconR
        val bmp = iconBitmap
        if (bmp != null) {
            drawCircularBitmap(canvas, bmp, iconCx, cy, iconR)
        } else {
            chipPaint.color = withAlpha(accentArgb, 75)
            canvas.drawCircle(iconCx, cy, iconR, chipPaint)
        }

        // Insignia de ventana flotante a la derecha
        val pipR = dp(13.5f)
        val pipCx = pillRect.right - dp(12f) - pipR
        chipPaint.color = withAlpha(primaryArgb, 56)
        canvas.drawCircle(pipCx, cy, pipR, chipPaint)
        drawFloatingWindowGlyph(canvas, pipCx, cy, dp(6.8f), Color.WHITE)

        val textLeft = iconCx + iconR + dp(10f)
        val textMaxW = (pipCx - pipR - dp(8f) - textLeft).coerceAtLeast(dp(40f))

        titlePaint.textSize = dp(12.5f)
        titlePaint.color = Color.WHITE
        val topLine = TextUtils.ellipsize(
            primaryText.ifBlank { "Notificación" },
            titlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(topLine, textLeft, cy - dp(2.5f), titlePaint)

        subtitlePaint.textSize = dp(11f)
        subtitlePaint.color = Color.argb(195, 224, 232, 245)
        val bottomLine = TextUtils.ellipsize(
            secondaryText.ifBlank { "Toca para abrir en ventana flotante" },
            subtitlePaint,
            textMaxW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(bottomLine, textLeft, cy + dp(12.5f), subtitlePaint)
    }

    private fun drawActionAlert(canvas: Canvas, accentArgb: Int, isScreenshot: Boolean) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(19f)
        chipPaint.color = withAlpha(accentArgb, 62)
        canvas.drawCircle(iconCx, cy, dp(12f), chipPaint)
        if (isScreenshot) {
            drawScreenshotGlyph(canvas, iconCx, cy, dp(6.5f))
        } else {
            drawClipboardGlyph(canvas, iconCx, cy, dp(6.5f))
        }

        val chipW = dp(44f)
        val chipH = dp(22f)
        val chipRect = RectF(
            pillRect.right - dp(9f) - chipW,
            cy - chipH / 2f,
            pillRect.right - dp(9f),
            cy + chipH / 2f
        )
        chipPaint.color = withAlpha(accentArgb, 84)
        canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, chipPaint)
        badgePaint.textSize = dp(10.5f)
        val chipBaseline = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(badgeText.ifBlank { "Abrir" }, chipRect.centerX(), chipBaseline, badgePaint)

        val textLeft = iconCx + dp(18f)
        val textMaxW = (chipRect.left - dp(6f) - textLeft).coerceAtLeast(dp(30f))
        titlePaint.textSize = dp(12f)
        titlePaint.color = Color.WHITE
        val label = TextUtils.ellipsize(primaryText, titlePaint, textMaxW, TextUtils.TruncateAt.END).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(label, textLeft, baseline, titlePaint)
    }

    private fun drawSilentTimer(canvas: Canvas, accentArgb: Int) {
        val cy = pillRect.centerY()
        val iconCx = pillRect.left + dp(17f)
        chipPaint.color = withAlpha(accentArgb, 58)
        canvas.drawCircle(iconCx, cy, dp(10.5f), chipPaint)
        // Reloj simple
        iconPaint.color = Color.WHITE
        iconPaint.strokeWidth = dp(1.5f)
        canvas.drawCircle(iconCx, cy, dp(5.5f), iconPaint)
        canvas.drawLine(iconCx, cy, iconCx, cy - dp(3f), iconPaint)
        canvas.drawLine(iconCx, cy, iconCx + dp(2.4f), cy + dp(1f), iconPaint)

        titlePaint.textSize = dp(11.5f)
        titlePaint.color = Color.WHITE
        val textLeft = iconCx + dp(16f)
        val maxW = (pillRect.right - dp(12f) - textLeft).coerceAtLeast(dp(24f))
        val text = TextUtils.ellipsize(primaryText, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
        val baseline = cy - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(text, textLeft, baseline, titlePaint)
    }

    private fun drawEqualizer(canvas: Canvas, startX: Float, cy: Float, primaryArgb: Int, secondaryArgb: Int) {
        val barW = dp(2.8f)
        val gap = dp(2.6f)
        val maxH = dp(12f)
        val minH = dp(4f)
        for (i in 0..2) {
            val factor = if (isPlaying) {
                0.5f + 0.5f * sin(eqPhase + i * 1.9f)
            } else {
                0.28f + (i % 2) * 0.22f
            }
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

    private fun drawFloatingWindowGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(cx - r, cy - r * 0.78f, cx + r, cy + r * 0.78f, dp(2.2f), dp(2.2f), iconPaint)
        fillGlyphPaint.color = color
        canvas.drawRoundRect(cx - r * 0.05f, cy - r * 0.05f, cx + r * 0.72f, cy + r * 0.52f, dp(1.4f), dp(1.4f), fillGlyphPaint)
    }

    private fun drawClipboardGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        iconPaint.color = Color.WHITE
        iconPaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(cx - r * 0.7f, cy - r * 0.72f, cx + r * 0.7f, cy + r * 0.88f, dp(2f), dp(2f), iconPaint)
        canvas.drawRoundRect(cx - r * 0.34f, cy - r * 0.98f, cx + r * 0.34f, cy - r * 0.54f, dp(1.2f), dp(1.2f), iconPaint)
    }

    private fun drawScreenshotGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        iconPaint.color = Color.WHITE
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

    private fun drawMusicNoteGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.6f)
        canvas.drawLine(cx + r * 0.25f, cy - r * 0.7f, cx + r * 0.25f, cy + r * 0.45f, iconPaint)
        canvas.drawLine(cx + r * 0.25f, cy - r * 0.7f, cx + r * 0.75f, cy - r * 0.45f, iconPaint)
        fillGlyphPaint.color = color
        canvas.drawCircle(cx - r * 0.12f, cy + r * 0.45f, r * 0.36f, fillGlyphPaint)
    }

    private fun drawPlayGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fillGlyphPaint.color = Color.WHITE
        val path = Path().apply {
            moveTo(cx - r * 0.48f, cy - r * 0.78f)
            lineTo(cx + r * 0.78f, cy)
            lineTo(cx - r * 0.48f, cy + r * 0.78f)
            close()
        }
        canvas.drawPath(path, fillGlyphPaint)
    }

    private fun drawPauseGlyph(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fillGlyphPaint.color = Color.WHITE
        val w = r * 0.34f
        canvas.drawRoundRect(cx - r * 0.58f, cy - r * 0.72f, cx - r * 0.58f + w, cy + r * 0.72f, dp(1.5f), dp(1.5f), fillGlyphPaint)
        canvas.drawRoundRect(cx + r * 0.24f, cy - r * 0.72f, cx + r * 0.24f + w, cy + r * 0.72f, dp(1.5f), dp(1.5f), fillGlyphPaint)
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

    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.rgb(
            (Color.red(base) * inv + Color.red(overlay) * ratio).toInt().coerceIn(0, 255),
            (Color.green(base) * inv + Color.green(overlay) * ratio).toInt().coerceIn(0, 255),
            (Color.blue(base) * inv + Color.blue(overlay) * ratio).toInt().coerceIn(0, 255)
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
