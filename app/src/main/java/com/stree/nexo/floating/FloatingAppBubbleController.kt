package com.stree.nexo.floating

import android.animation.ValueAnimator
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.UsageStore
import com.stree.nexo.panel.SplitScreenService
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.ui.theme.visualSpec
import kotlin.math.hypot

/**
 * Controlador de la burbuja flotante lateral para ventanas minimizadas:
 * - Detecta automáticamente cuando el usuario pulsa minimizar ("—") en la barra nativa
 *   de una ventana flotante (vía UsageStatsManager y/o SplitScreenService) y muestra
 *   la app como burbuja apilada en el borde.
 * - Un toque en la burbuja reabre la app directamente en ventana flotante.
 * - Doble toque en la burbuja abre el menú de pantallas divididas y ventanas flotantes.
 * - Incluye un pequeño indicador visual (icono 2×2) encima de la burbuja para señalar
 *   que el menú de ventanas / pantalla dividida está disponible.
 * - Arrastrar hacia la ✕ inferior limpia todas las burbujas.
 */
class FloatingAppBubbleController(
    private val context: Context,
    private val onOpenMenu: (startSplit: Boolean) -> Unit
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val usageStore = UsageStore(context)

    // Burbuja lateral (apps minimizadas)
    private var bubbleRoot: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var dismissTarget: FrameLayout? = null
    private var dismissCircle: TextView? = null
    private var backStackView: View? = null
    private var mainRingView: FrameLayout? = null
    private var appIconView: ImageView? = null
    private var menuIndicatorView: View? = null
    private var countBadge: TextView? = null

    private var snapRight: Boolean = Prefs.panelSide(context) == PanelSide.END
    private var savedYRatio: Float = 0.36f
    private var snapAnimator: ValueAnimator? = null
    private var suppressed = false

    // Vigilante ligero de ventanas flotantes abiertas (detecta minimizar nativo "—")
    private var watcherRunning = false
    private var wasScreenOff = false
    private var screenWakeGraceUntilMs = 0L

    // Gestión de toque simple vs doble toque
    private var pendingSingleTap: Runnable? = null

    private val activeWatcherRunnable = object : Runnable {
        override fun run() {
            if (!watcherRunning) return
            checkActiveWindowsForNativeMinimize()
            if (watcherRunning) {
                handler.postDelayed(this, WATCHER_POLL_MS)
            }
        }
    }

    fun setSuppressed(hidden: Boolean) {
        suppressed = hidden
        sync()
    }

    fun sync() {
        val entries = FloatingWindowRegistry.entries.value
        val enabled = Prefs.floatingBubbleEnabled(context) && Settings.canDrawOverlays(context)
        if (!enabled || entries.isEmpty() || suppressed) {
            hide()
            return
        }

        val activeEntries = entries.filter { !it.minimized }
        val minimizedEntries = entries.filter { it.minimized }
        val hasUsageAccess = usageStore.hasUsageAccess()
        val canDetectMinimize = SplitScreenService.isRunning() || hasUsageAccess

        // Vigila ventanas flotantes activas para convertirlas en burbuja al pulsar "—" nativo
        if (activeEntries.isNotEmpty() && hasUsageAccess) {
            startActiveWindowWatcher()
        } else {
            stopActiveWindowWatcher()
        }

        // Si podemos detectar el minimizar nativo, la burbuja solo aparece cuando hay apps minimizadas;
        // si no hay ni Accesibilidad ni Acceso de Uso, mostramos la burbuja como respaldo inmediato.
        val bubbleEntries = if (canDetectMinimize) minimizedEntries else entries
        if (bubbleEntries.isNotEmpty()) {
            val isNewBubble = bubbleRoot == null
            if (isNewBubble) {
                attachBubble()
            }
            renderBubble(bubbleEntries)
            if (isNewBubble) {
                animateBubbleEntrance()
            }
        } else {
            hideBubbleOnly()
        }
    }

    fun hide() {
        snapAnimator?.cancel()
        snapAnimator = null
        stopActiveWindowWatcher()
        cancelPendingSingleTap()
        handler.removeCallbacksAndMessages(null)
        hideBubbleOnly()
    }

    private fun hideBubbleOnly() {
        cancelPendingSingleTap()
        hideDismissTarget()
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        bubbleRoot = null
        bubbleParams = null
        backStackView = null
        mainRingView = null
        appIconView = null
        menuIndicatorView = null
        countBadge = null
    }

    private fun startActiveWindowWatcher() {
        if (watcherRunning) return
        watcherRunning = true
        handler.removeCallbacks(activeWatcherRunnable)
        handler.postDelayed(activeWatcherRunnable, WATCHER_POLL_MS)
    }

    private fun stopActiveWindowWatcher() {
        watcherRunning = false
        handler.removeCallbacks(activeWatcherRunnable)
    }

    /**
     * Consulta eventos de ciclo de vida recientes para detectar cuando una ventana flotante
     * pasa a segundo plano / oculta (p. ej. al pulsar "—" en la barra nativa de Android/One UI).
     */
    private fun checkActiveWindowsForNativeMinimize() {
        val activeEntries = FloatingWindowRegistry.entries.value.filter { !it.minimized }
        if (activeEntries.isEmpty() || suppressed) {
            stopActiveWindowWatcher()
            return
        }
        val pm = context.getSystemService(PowerManager::class.java)
        val interactive = pm?.isInteractive ?: true
        val now = System.currentTimeMillis()
        if (!interactive) {
            wasScreenOff = true
            return
        }
        if (wasScreenOff) {
            wasScreenOff = false
            screenWakeGraceUntilMs = now + 1_500L
            return
        }
        if (now < screenWakeGraceUntilMs) return
        if (!usageStore.hasUsageAccess()) {
            stopActiveWindowWatcher()
            return
        }

        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val oldestActiveMs = activeEntries.minOf { it.lastActiveMs }
        val queryStart = maxOf(oldestActiveMs - 400L, now - 15_000L)
        val events = runCatching { usm.queryEvents(queryStart, now) }.getOrNull() ?: return

        val activePkgs = activeEntries.mapTo(mutableSetOf()) { it.packageName }
        val lastResumed = mutableMapOf<String, Long>()
        val lastStopped = mutableMapOf<String, Long>()
        val lastPaused = mutableMapOf<String, Long>()

        val resumedType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            1
        }
        val pausedType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            2
        }
        val stoppedType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_STOPPED
        } else {
            -1
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg !in activePkgs) continue
            val ts = event.timeStamp
            when (event.eventType) {
                resumedType -> if (ts >= (lastResumed[pkg] ?: 0L)) lastResumed[pkg] = ts
                pausedType -> if (ts >= (lastPaused[pkg] ?: 0L)) lastPaused[pkg] = ts
                stoppedType -> if (stoppedType != -1 && ts >= (lastStopped[pkg] ?: 0L)) lastStopped[pkg] = ts
            }
        }

        for (entry in activeEntries) {
            if (now - entry.lastActiveMs < LAUNCH_GRACE_MS) continue
            val pkg = entry.packageName
            val resumedTs = lastResumed[pkg] ?: 0L
            val minimizedDetected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val stoppedTs = lastStopped[pkg] ?: 0L
                stoppedTs >= entry.lastActiveMs &&
                    stoppedTs >= resumedTs &&
                    (now - stoppedTs) >= STOP_CONFIRM_MS
            } else {
                val pausedTs = lastPaused[pkg] ?: 0L
                pausedTs >= entry.lastActiveMs &&
                    pausedTs >= resumedTs &&
                    (now - pausedTs) >= STOP_CONFIRM_MS
            }
            if (minimizedDetected) {
                FloatingWindowRegistry.markMinimized(context, pkg)
            }
        }
    }

    private fun px(dp: Int): Int = (dp * context.resources.displayMetrics.density.coerceAtLeast(1f)).toInt()

    private fun accentColor(): Int =
        Prefs.panelStyle(context).visualSpec(light = false).primary.toArgb()

    // -------------------------------------------------------------------------
    // Burbuja flotante apilada en el borde con indicador superior de menú (2×2)
    // -------------------------------------------------------------------------

    private fun attachBubble() {
        val dm = context.resources.displayMetrics
        val boxW = px(70)
        val boxH = px(74)
        val marginPx = px(8)
        val startX = if (snapRight) dm.widthPixels - boxW - marginPx else marginPx
        val startY = (dm.heightPixels * savedYRatio).toInt()
            .coerceIn(px(64), (dm.heightPixels - boxH - px(110)).coerceAtLeast(px(64)))

        val params = WindowManager.LayoutParams(
            boxW,
            boxH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            contentDescription = "App minimizada de Nexo. Toca para abrir en flotante, doble toque para menú de ventanas y pantalla dividida."
        }

        // Tarjeta trasera cuando hay varias apps apiladas en la burbuja
        val backCard = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(204, 34, 38, 54))
                setStroke(px(1), Color.argb(110, 255, 255, 255))
            }
            visibility = View.GONE
        }
        root.addView(
            backCard,
            FrameLayout.LayoutParams(px(46), px(46), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = px(6)
                bottomMargin = px(3)
            }
        )

        // Burbuja circular principal con el icono de la app minimizada
        val ring = FrameLayout(context).apply {
            elevation = px(7).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(238, 18, 20, 30))
                setStroke(px(2), accentColor())
            }
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        ring.addView(iconView, FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER))
        root.addView(
            ring,
            FrameLayout.LayoutParams(px(54), px(54), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = px(6)
            }
        )

        // Indicador pequeño encima de la burbuja: icono 2×2 de ventanas flotantes / pantalla dividida
        val indicator = View(context).apply {
            elevation = px(10).toFloat()
            background = MenuIndicatorDrawable(
                accentColor = accentColor(),
                density = context.resources.displayMetrics.density.coerceAtLeast(1f)
            )
        }
        root.addView(
            indicator,
            FrameLayout.LayoutParams(px(24), px(16), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = px(3)
            }
        )

        // Contador de apps minimizadas (abajo a la derecha para nunca chocar con el indicador superior)
        val badge = TextView(context).apply {
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            elevation = px(10).toFloat()
            visibility = View.GONE
        }
        root.addView(
            badge,
            FrameLayout.LayoutParams(px(20), px(20), Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = px(3)
                marginEnd = px(4)
            }
        )

        attachBubbleTouchLogic(root, params)

        runCatching {
            wm.addView(root, params)
            bubbleRoot = root
            bubbleParams = params
            backStackView = backCard
            mainRingView = ring
            appIconView = iconView
            menuIndicatorView = indicator
            countBadge = badge
        }
    }

    private fun renderBubble(minimized: List<FloatingAppEntry>) {
        val top = minimized.firstOrNull() ?: return
        val accent = accentColor()

        (mainRingView?.background as? GradientDrawable)?.setStroke(px(2), accent)

        val drawable = runCatching {
            context.packageManager.getApplicationIcon(top.packageName)
        }.getOrNull()
        appIconView?.setImageDrawable(drawable)

        (menuIndicatorView?.background as? MenuIndicatorDrawable)?.updateAccent(accent)

        backStackView?.visibility = if (minimized.size > 1) View.VISIBLE else View.GONE

        countBadge?.apply {
            if (minimized.size > 1) {
                visibility = View.VISIBLE
                text = minimized.size.toString()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                    setStroke(px(1), Color.argb(225, 14, 16, 24))
                }
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun animateBubbleEntrance() {
        val root = bubbleRoot ?: return
        root.alpha = 0f
        root.scaleX = 0.72f
        root.scaleY = 0.72f
        root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun cancelPendingSingleTap() {
        pendingSingleTap?.let { handler.removeCallbacks(it) }
        pendingSingleTap = null
    }

    private fun attachBubbleTouchLogic(root: FrameLayout, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressFired = false
        var hoveringDismiss = false
        var isSecondTap = false

        val longPressRunnable = Runnable {
            if (!dragging && !isSecondTap) {
                longPressFired = true
                cancelPendingSingleTap()
                Haptics.perform(context, strong = true)
                onOpenMenu(false)
            }
        }

        root.setOnTouchListener { _, event ->
            val dm = context.resources.displayMetrics
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressFired = false
                    hoveringDismiss = false

                    // Si había un toque simple pendiente, este segundo toque es un DOBLE TOQUE
                    if (pendingSingleTap != null) {
                        cancelPendingSingleTap()
                        isSecondTap = true
                        handler.removeCallbacks(longPressRunnable)
                    } else {
                        isSecondTap = false
                        handler.removeCallbacks(longPressRunnable)
                        handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
                        isSecondTap = false
                        cancelPendingSingleTap()
                        handler.removeCallbacks(longPressRunnable)
                        showDismissTarget()
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                            .coerceIn(-px(8), dm.widthPixels - params.width + px(8))
                        params.y = (startY + dy).toInt()
                            .coerceIn(px(36), dm.heightPixels - params.height - px(24))
                        runCatching { wm.updateViewLayout(root, params) }

                        val inside = isOverDismissZone(event.rawX, event.rawY)
                        if (inside != hoveringDismiss) {
                            hoveringDismiss = inside
                            updateDismissTargetHighlight(inside)
                            if (inside) Haptics.perform(context)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragging) {
                        isSecondTap = false
                        val shouldDismiss = hoveringDismiss && event.actionMasked == MotionEvent.ACTION_UP
                        hideDismissTarget()
                        if (shouldDismiss) {
                            Haptics.perform(context, strong = true)
                            FloatingWindowRegistry.clearAll(context)
                        } else {
                            snapToEdge(root, params)
                        }
                    } else if (!longPressFired && event.actionMasked == MotionEvent.ACTION_UP) {
                        if (isSecondTap) {
                            // Doble toque confirmado: abrir menú de pantallas divididas y ventanas flotantes
                            isSecondTap = false
                            handleBubbleDoubleTap(root)
                        } else {
                            // Esperar ventana de doble toque; si no llega segundo toque, reabrir en flotante
                            val singleTapAction = Runnable {
                                pendingSingleTap = null
                                handleBubbleSingleTap()
                            }
                            pendingSingleTap = singleTapAction
                            handler.postDelayed(singleTapAction, DOUBLE_TAP_TIMEOUT_MS)
                        }
                    } else {
                        isSecondTap = false
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun handleBubbleSingleTap() {
        Haptics.perform(context)
        val entries = FloatingWindowRegistry.entries.value
        val target = entries.firstOrNull { it.minimized } ?: entries.firstOrNull() ?: return
        ItemLauncher.launchPackageFloatingInBounds(
            context = context,
            packageName = target.packageName,
            bounds = target.lastBounds,
            registerInDock = true
        )
    }

    private fun handleBubbleDoubleTap(root: FrameLayout) {
        Haptics.perform(context, strong = true)
        root.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(90L)
            .withEndAction {
                root.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
            }
            .start()
        onOpenMenu(false)
    }

    private fun snapToEdge(root: FrameLayout, params: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val marginPx = px(8)
        val centerX = params.x + params.width / 2
        snapRight = centerX >= dm.widthPixels / 2
        savedYRatio = (params.y.toFloat() / dm.heightPixels.coerceAtLeast(1)).coerceIn(0.08f, 0.82f)
        val targetX = if (snapRight) dm.widthPixels - params.width - marginPx else marginPx

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 210L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                runCatching { wm.updateViewLayout(root, params) }
            }
            start()
        }
    }

    private fun isOverDismissZone(rawX: Float, rawY: Float): Boolean {
        val dm = context.resources.displayMetrics
        val targetCenterX = dm.widthPixels / 2f
        val targetCenterY = dm.heightPixels - px(82).toFloat()
        return hypot((rawX - targetCenterX).toDouble(), (rawY - targetCenterY).toDouble()) <= px(64)
    }

    private fun showDismissTarget() {
        if (dismissTarget != null) return
        val sizePx = px(60)
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = px(52)
        }
        val circle = TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 24, 26, 36))
                setStroke(px(2), Color.argb(145, 255, 255, 255))
            }
            elevation = px(6).toFloat()
        }
        val holder = FrameLayout(context).apply {
            addView(circle, FrameLayout.LayoutParams(-1, -1))
            alpha = 0f
            scaleX = 0.75f
            scaleY = 0.75f
        }
        runCatching {
            wm.addView(holder, params)
            dismissTarget = holder
            dismissCircle = circle
            holder.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
        }
    }

    private fun updateDismissTargetHighlight(active: Boolean) {
        val circle = dismissCircle ?: return
        val holder = dismissTarget ?: return
        (circle.background as? GradientDrawable)?.apply {
            setColor(if (active) Color.argb(235, 225, 29, 72) else Color.argb(200, 24, 26, 36))
            setStroke(px(2), if (active) Color.WHITE else Color.argb(145, 255, 255, 255))
        }
        val scale = if (active) 1.16f else 1f
        holder.animate().scaleX(scale).scaleY(scale).setDuration(120L).start()
    }

    private fun hideDismissTarget() {
        dismissTarget?.let { runCatching { wm.removeView(it) } }
        dismissTarget = null
        dismissCircle = null
    }

    /**
     * Píldora diminuta (24×16 dp) con un icono vectorial 2×2 nítido que se coloca justo encima
     * de la burbuja para indicar que el doble toque abre el menú de ventanas / pantalla dividida.
     */
    private class MenuIndicatorDrawable(
        private var accentColor: Int,
        private val density: Float
    ) : Drawable() {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(242, 16, 18, 28)
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.25f * density
            color = accentColor
        }
        private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        private val accentGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
        }
        private val rect = RectF()
        private val cell = RectF()

        fun updateAccent(newAccent: Int) {
            if (accentColor == newAccent) return
            accentColor = newAccent
            strokePaint.color = newAccent
            accentGlyphPaint.color = newAccent
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val inset = strokePaint.strokeWidth * 0.5f
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            val radius = rect.height() * 0.5f
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)

            // Icono compacto 2×2 centrado (representa pantalla dividida y acoples flotantes)
            val cellW = 4.2f * density
            val cellH = 3.2f * density
            val gap = 1.6f * density
            val totalW = cellW * 2f + gap
            val totalH = cellH * 2f + gap
            val startX = rect.centerX() - totalW * 0.5f
            val startY = rect.centerY() - totalH * 0.5f
            val cellR = 0.9f * density

            // Celda superior izquierda (acento)
            cell.set(startX, startY, startX + cellW, startY + cellH)
            canvas.drawRoundRect(cell, cellR, cellR, accentGlyphPaint)
            // Celda superior derecha (blanco)
            cell.set(startX + cellW + gap, startY, startX + totalW, startY + cellH)
            canvas.drawRoundRect(cell, cellR, cellR, glyphPaint)
            // Celda inferior izquierda (blanco)
            cell.set(startX, startY + cellH + gap, startX + cellW, startY + totalH)
            canvas.drawRoundRect(cell, cellR, cellR, glyphPaint)
            // Celda inferior derecha (acento)
            cell.set(startX + cellW + gap, startY + cellH + gap, startX + totalW, startY + totalH)
            canvas.drawRoundRect(cell, cellR, cellR, accentGlyphPaint)
        }

        override fun setAlpha(alpha: Int) {
            bgPaint.alpha = alpha
            strokePaint.alpha = alpha
            glyphPaint.alpha = alpha
            accentGlyphPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bgPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
            glyphPaint.colorFilter = colorFilter
            accentGlyphPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val WATCHER_POLL_MS = 450L
        private const val LAUNCH_GRACE_MS = 1_000L
        private const val STOP_CONFIRM_MS = 400L
        private const val DOUBLE_TAP_TIMEOUT_MS = 260L
        private const val LONG_PRESS_TIMEOUT_MS = 520L
    }
}
