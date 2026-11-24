package com.stree.nexo.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.ui.theme.visualSpec
import kotlin.math.hypot

/**
 * Controlador de overlays para ventanas flotantes:
 * 1. Barra superior profesional sobre la ventana flotante activa (Menú flotante,
 *    Pantalla dividida, Minimizar a burbuja, Maximizar y ✕ Cerrar definitivamente).
 * 2. Burbuja flotante apilada en el borde cuando la app está minimizada (un toque la
 *    reabre directo en ventana flotante; arrastrar a ✕ inferior limpia las burbujas).
 */
class FloatingAppBubbleController(
    private val context: Context,
    private val onOpenMenu: (startSplit: Boolean) -> Unit
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    // Burbuja lateral (apps minimizadas)
    private var bubbleRoot: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var dismissTarget: FrameLayout? = null
    private var dismissCircle: TextView? = null
    private var backStackView: View? = null
    private var mainRingView: FrameLayout? = null
    private var appIconView: ImageView? = null
    private var countBadge: TextView? = null
    private var statusDot: View? = null

    // Barra superior de la ventana flotante activa
    private var topBarRoot: LinearLayout? = null
    private var topBarParams: WindowManager.LayoutParams? = null
    private var topBarIcon: ImageView? = null
    private var topBarTitle: TextView? = null
    private var topBarMenuBtn: TextView? = null
    private var topBarActivePkg: String? = null
    private var topBarCustomPos = false

    private var snapRight: Boolean = Prefs.panelSide(context) == PanelSide.END
    private var savedYRatio: Float = 0.36f
    private var snapAnimator: ValueAnimator? = null
    private var suppressed = false

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

        val activeEntry = entries.firstOrNull { !it.minimized }
        val minimizedEntries = entries.filter { it.minimized }

        // Barra superior visible siempre que haya una app abierta en ventana flotante
        if (activeEntry != null) {
            if (topBarActivePkg != activeEntry.packageName) {
                topBarActivePkg = activeEntry.packageName
                topBarCustomPos = false
            }
            if (topBarRoot == null) {
                attachTopBar(activeEntry)
            }
            renderTopBar(activeEntry, entries.size)
        } else {
            hideTopBar()
        }

        // Burbuja circular visible para apps minimizadas
        if (minimizedEntries.isNotEmpty()) {
            if (bubbleRoot == null) {
                attachBubble()
            }
            renderBubble(minimizedEntries)
        } else {
            hideBubbleOnly()
        }
    }

    fun hide() {
        snapAnimator?.cancel()
        snapAnimator = null
        handler.removeCallbacksAndMessages(null)
        hideTopBar()
        hideBubbleOnly()
    }

    private fun hideBubbleOnly() {
        hideDismissTarget()
        bubbleRoot?.let { runCatching { wm.removeView(it) } }
        bubbleRoot = null
        bubbleParams = null
        backStackView = null
        mainRingView = null
        appIconView = null
        countBadge = null
        statusDot = null
    }

    private fun hideTopBar() {
        topBarRoot?.let { runCatching { wm.removeView(it) } }
        topBarRoot = null
        topBarParams = null
        topBarIcon = null
        topBarTitle = null
        topBarMenuBtn = null
        topBarActivePkg = null
        topBarCustomPos = false
    }

    private fun px(dp: Int): Int = (dp * context.resources.displayMetrics.density.coerceAtLeast(1f)).toInt()

    private fun accentColor(): Int =
        Prefs.panelStyle(context).visualSpec(light = false).primary.toArgb()

    // -------------------------------------------------------------------------
    // 1. Barra superior profesional para la app en ventana flotante
    // -------------------------------------------------------------------------

    private fun attachTopBar(active: FloatingAppEntry) {
        val dm = context.resources.displayMetrics
        val barWidth = (dm.widthPixels * 0.86f).toInt().coerceIn(px(268), px(380))
        val barHeight = px(40)
        val statusRes = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusPx = if (statusRes > 0) context.resources.getDimensionPixelSize(statusRes) else px(32)

        val defaultY = active.lastBounds?.let { (it.top - barHeight - px(6)).coerceAtLeast(statusPx + px(4)) }
            ?: (statusPx + px(8))
        val defaultX = ((dm.widthPixels - barWidth) / 2).coerceAtLeast(px(8))

        val params = WindowManager.LayoutParams(
            barWidth,
            barHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultX
            y = defaultY
        }

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(8), px(4), px(6), px(4))
            elevation = px(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = px(20).toFloat()
                setColor(Color.argb(238, 18, 20, 30))
                setStroke(px(1), accentColor())
            }
        }

        // Zona izquierda arrastrable: icono + nombre de la app flotante
        val dragZone = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(2), 0, px(6), 0)
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, px(6).toFloat())
                }
            }
        }
        dragZone.addView(iconView, LinearLayout.LayoutParams(px(22), px(22)).apply {
            marginEnd = px(6)
        })

        val titleView = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        dragZone.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        attachTopBarDrag(dragZone, bar, params)
        bar.addView(dragZone, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        fun makeActionButton(
            symbol: String,
            desc: String,
            tint: Int = Color.WHITE,
            bgColor: Int = Color.TRANSPARENT,
            onClick: () -> Unit
        ): TextView = TextView(context).apply {
            text = symbol
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tint)
            contentDescription = desc
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            setOnClickListener {
                Haptics.perform(context)
                onClick()
            }
        }

        val btnLp = {
            LinearLayout.LayoutParams(px(30), px(30)).apply {
                marginStart = px(3)
            }
        }

        // 1. Menú de ventanas flotantes y layouts
        val menuBtn = makeActionButton(
            symbol = "⊞",
            desc = "Menú de ventanas flotantes",
            tint = accentColor(),
            bgColor = Color.argb(46, Color.red(accentColor()), Color.green(accentColor()), Color.blue(accentColor()))
        ) {
            onOpenMenu(false)
        }
        bar.addView(menuBtn, btnLp())

        // 2. Pantalla dividida
        val splitBtn = makeActionButton(
            symbol = "◫",
            desc = "Pantalla dividida",
            tint = Color.WHITE,
            bgColor = Color.argb(30, 255, 255, 255)
        ) {
            val all = FloatingWindowRegistry.entries.value
            val current = all.firstOrNull { !it.minimized } ?: all.firstOrNull() ?: return@makeActionButton
            val second = all.firstOrNull { it.packageName != current.packageName }
            if (second != null) {
                FloatingWindowRegistry.openSplitScreen(context, current.packageName, second.packageName)
            } else {
                onOpenMenu(true)
            }
        }
        bar.addView(splitBtn, btnLp())

        // 3. Minimizar a burbuja
        val minBtn = makeActionButton(
            symbol = "—",
            desc = "Minimizar a burbuja",
            tint = Color.WHITE,
            bgColor = Color.argb(30, 255, 255, 255)
        ) {
            val current = FloatingWindowRegistry.entries.value.firstOrNull { !it.minimized }
                ?: FloatingWindowRegistry.entries.value.firstOrNull()
                ?: return@makeActionButton
            FloatingWindowRegistry.markMinimized(context, current.packageName)
            EdgePanelService.minimizeToBackground(context)
        }
        bar.addView(minBtn, btnLp())

        // 4. Maximizar a pantalla completa
        val maxBtn = makeActionButton(
            symbol = "⤢",
            desc = "Maximizar",
            tint = Color.WHITE,
            bgColor = Color.argb(30, 255, 255, 255)
        ) {
            val current = FloatingWindowRegistry.entries.value.firstOrNull { !it.minimized }
                ?: FloatingWindowRegistry.entries.value.firstOrNull()
                ?: return@makeActionButton
            FloatingWindowRegistry.remove(context, current.packageName)
            ItemLauncher.launchPackageFullscreen(context, current.packageName)
        }
        bar.addView(maxBtn, btnLp())

        // 5. Cerrar definitivamente (X)
        val closeBtn = makeActionButton(
            symbol = "✕",
            desc = "Cerrar ventana definitivamente",
            tint = Color.rgb(251, 113, 133),
            bgColor = Color.argb(45, 251, 113, 133)
        ) {
            Haptics.perform(context, strong = true)
            val current = FloatingWindowRegistry.entries.value.firstOrNull { !it.minimized }
                ?: FloatingWindowRegistry.entries.value.firstOrNull()
                ?: return@makeActionButton
            FloatingWindowRegistry.closeAppDefinitively(context, current.packageName)
        }
        bar.addView(closeBtn, btnLp())

        runCatching {
            wm.addView(bar, params)
            topBarRoot = bar
            topBarParams = params
            topBarIcon = iconView
            topBarTitle = titleView
            topBarMenuBtn = menuBtn
        }
    }

    private fun renderTopBar(active: FloatingAppEntry, totalCount: Int) {
        val accent = accentColor()
        (topBarRoot?.background as? GradientDrawable)?.setStroke(px(1), accent)
        topBarMenuBtn?.setTextColor(accent)
        (topBarMenuBtn?.background as? GradientDrawable)?.setColor(
            Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent))
        )

        val drawable = runCatching {
            context.packageManager.getApplicationIcon(active.packageName)
        }.getOrNull()
        topBarIcon?.setImageDrawable(drawable)
        topBarTitle?.text = if (totalCount > 1) "${active.label} ($totalCount)" else active.label

        if (!topBarCustomPos) {
            val root = topBarRoot ?: return
            val params = topBarParams ?: return
            val dm = context.resources.displayMetrics
            val statusRes = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusPx = if (statusRes > 0) context.resources.getDimensionPixelSize(statusRes) else px(32)
            val targetY = active.lastBounds?.let { (it.top - params.height - px(6)).coerceAtLeast(statusPx + px(4)) }
                ?: (statusPx + px(8))
            val targetX = ((dm.widthPixels - params.width) / 2).coerceAtLeast(px(8))
            if (params.x != targetX || params.y != targetY) {
                params.x = targetX
                params.y = targetY
                runCatching { wm.updateViewLayout(root, params) }
            }
        }
    }

    private fun attachTopBarDrag(handle: View, root: View, params: WindowManager.LayoutParams) {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            val dm = context.resources.displayMetrics
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    topBarCustomPos = true
                    params.x = (startX + event.rawX - startRawX).toInt()
                        .coerceIn(px(4), (dm.widthPixels - params.width - px(4)).coerceAtLeast(px(4)))
                    params.y = (startY + event.rawY - startRawY).toInt()
                        .coerceIn(px(28), (dm.heightPixels - params.height - px(24)).coerceAtLeast(px(28)))
                    runCatching { wm.updateViewLayout(root, params) }
                    true
                }
                else -> true
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. Burbuja flotante apilada en el borde (apps minimizadas)
    // -------------------------------------------------------------------------

    private fun attachBubble() {
        val dm = context.resources.displayMetrics
        val boxPx = px(66)
        val marginPx = px(8)
        val startX = if (snapRight) dm.widthPixels - boxPx - marginPx else marginPx
        val startY = (dm.heightPixels * savedYRatio).toInt()
            .coerceIn(px(64), (dm.heightPixels - boxPx - px(110)).coerceAtLeast(px(64)))

        val params = WindowManager.LayoutParams(
            boxPx,
            boxPx,
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
        }

        val backCard = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 34, 38, 54))
                setStroke(px(1), Color.argb(100, 255, 255, 255))
            }
            visibility = View.GONE
        }
        root.addView(
            backCard,
            FrameLayout.LayoutParams(px(46), px(46), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = px(4)
                bottomMargin = px(4)
            }
        )

        val ring = FrameLayout(context).apply {
            elevation = px(7).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(236, 18, 20, 30))
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
        root.addView(ring, FrameLayout.LayoutParams(px(54), px(54), Gravity.CENTER))

        val badge = TextView(context).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            elevation = px(9).toFloat()
            visibility = View.GONE
        }
        root.addView(
            badge,
            FrameLayout.LayoutParams(px(21), px(21), Gravity.TOP or Gravity.END).apply {
                topMargin = px(2)
                marginEnd = px(2)
            }
        )

        val dot = View(context).apply {
            elevation = px(9).toFloat()
        }
        root.addView(
            dot,
            FrameLayout.LayoutParams(px(12), px(12), Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = px(5)
                marginEnd = px(5)
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
            countBadge = badge
            statusDot = dot
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

        backStackView?.visibility = if (minimized.size > 1) View.VISIBLE else View.GONE

        countBadge?.apply {
            if (minimized.size > 1) {
                visibility = View.VISIBLE
                text = minimized.size.toString()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                    setStroke(px(1), Color.argb(220, 14, 16, 24))
                }
            } else {
                visibility = View.GONE
            }
        }

        statusDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(230, 36, 42, 58))
            setStroke(px(2), accent)
        }
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

        val longPressRunnable = Runnable {
            if (!dragging) {
                longPressFired = true
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
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, 460L)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
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
                        val shouldDismiss = hoveringDismiss && event.actionMasked == MotionEvent.ACTION_UP
                        hideDismissTarget()
                        if (shouldDismiss) {
                            Haptics.perform(context, strong = true)
                            FloatingWindowRegistry.clearAll(context)
                        } else {
                            snapToEdge(root, params)
                        }
                    } else if (!longPressFired && event.actionMasked == MotionEvent.ACTION_UP) {
                        handleBubbleTap()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun handleBubbleTap() {
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
}
