package com.stree.nexo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.stree.nexo.WidgetPickerActivity
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.theme.visualSpec

/**
 * Ventana overlay real para widgets flotantes:
 * - En reposo muestra el widget puro sin recuadro gris, sin barra fija ni esquina.
 * - Al mantener pulsado entra en modo edición: permite arrastrar para moverlo,
 *   redimensionarlo con puntos circulares, reconfigurarlo o cerrarlo.
 */
class FloatingWidgetController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var listening = false

    fun show(item: String) {
        close()
        if (!Settings.canDrawOverlays(context)) return
        var binding = WidgetStore.get(context, item) ?: return
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(binding.id) ?: run {
            Toast.makeText(context, "El widget ya no está disponible. Asígnalo de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        fun px(dp: Int) = (dp * density).toInt()

        val spec = Prefs.panelStyle(context).visualSpec(light = false)
        val accentColor = spec.primary.toArgb()
        val barBgColor = Color.argb(238, 18, 20, 28)

        val maxWidth = ((context.resources.displayMetrics.widthPixels / density).toInt() - 24).coerceAtLeast(140)
        val maxHeight = ((context.resources.displayMetrics.heightPixels / density).toInt() - 120).coerceAtLeast(100)
        binding = binding.copy(
            width = binding.width.coerceIn(120, maxWidth),
            height = binding.height.coerceIn(80, maxHeight)
        )

        var editing = false
        val editPadPx = px(10)
        val barHeightPx = px(44)

        val layout = WindowManager.LayoutParams(
            px(binding.width),
            px(binding.height),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px(12)
            y = px(108)
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        // Contenedor redondeado del widget (sin fondo ni borde en reposo)
        val widgetWrapper = FrameLayout(context).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, px(18).toFloat())
                }
            }
        }

        val host = WidgetStore.host(context)
        val widgetView = host.createView(context, binding.id, info)
        widgetWrapper.addView(widgetView, FrameLayout.LayoutParams(-1, -1))

        // Borde sutil solo en modo edición
        val editBorderOverlay = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = px(18).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(px(2), accentColor)
            }
            visibility = View.GONE
        }

        // Capa transparente para arrastrar y mover el widget mientras está en modo edición
        val moveOverlay = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }

        val widgetArea = FrameLayout(context).apply {
            addView(widgetWrapper, FrameLayout.LayoutParams(-1, -1))
            addView(moveOverlay, FrameLayout.LayoutParams(-1, -1))
            addView(editBorderOverlay, FrameLayout.LayoutParams(-1, -1))
        }
        container.addView(widgetArea, FrameLayout.LayoutParams(-1, -1))

        // Mini barra flotante superior (solo en modo edición)
        val editBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(8), px(4), px(8), px(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = px(20).toFloat()
                setColor(barBgColor)
                setStroke(px(1), Color.argb(95, 255, 255, 255))
            }
            visibility = View.GONE
        }

        fun makeBarChip(text: String, textColor: Int, bg: Int = Color.TRANSPARENT, onClick: () -> Unit): TextView =
            TextView(context).apply {
                this.text = text
                textSize = 12f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                setPadding(px(10), px(6), px(10), px(6))
                if (bg != Color.TRANSPARENT) {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = px(14).toFloat()
                        setColor(bg)
                    }
                }
                setOnClickListener {
                    Haptics.perform(context)
                    onClick()
                }
            }

        // Puntos circulares de redimensionado (solo visibles en modo edición)
        fun makeResizeDot(): FrameLayout {
            val hit = FrameLayout(context).apply { visibility = View.GONE }
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(px(2), accentColor)
                }
                elevation = px(3).toFloat()
            }
            hit.addView(dot, FrameLayout.LayoutParams(px(14), px(14), Gravity.CENTER))
            return hit
        }

        val dotRight = makeResizeDot()
        val dotBottom = makeResizeDot()
        val dotCorner = makeResizeDot()

        fun updateLayout(commit: Boolean = true) {
            val extraTop = if (editing) barHeightPx else 0
            val pad = if (editing) editPadPx else 0
            val wPx = px(binding.width)
            val hPx = px(binding.height)

            layout.width = wPx + pad * 2
            layout.height = hPx + extraTop + pad * 2
            layout.x = layout.x.coerceIn(0, (context.resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
            layout.y = layout.y.coerceIn(0, (context.resources.displayMetrics.heightPixels - layout.height).coerceAtLeast(0))

            (widgetArea.layoutParams as FrameLayout.LayoutParams).apply {
                width = wPx
                height = hPx
                leftMargin = pad
                topMargin = extraTop + pad
            }
            widgetArea.requestLayout()

            if (commit) {
                WidgetStore.applyWidgetSize(widgetView, binding.width, binding.height)
                WidgetStore.put(context, item, binding)
            }
            runCatching { wm.updateViewLayout(container, layout) }
        }

        fun setEditMode(active: Boolean) {
            if (editing == active) return
            editing = active
            if (widgetView is NexoWidgetHostView) {
                widgetView.interceptAllTouchesForEdit = active
            }
            val vis = if (active) View.VISIBLE else View.GONE
            editBar.visibility = vis
            editBorderOverlay.visibility = vis
            moveOverlay.visibility = vis
            dotRight.visibility = vis
            dotBottom.visibility = vis
            dotCorner.visibility = vis
            updateLayout(commit = !active)
        }

        if (info.configure != null) {
            editBar.addView(
                makeBarChip("Ajustar", Color.WHITE) {
                    setEditMode(false)
                    runCatching {
                        context.startActivity(
                            WidgetPickerActivity.reconfigureIntent(context, item, binding.id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }
        editBar.addView(
            makeBarChip("Cerrar", Color.rgb(251, 113, 133)) {
                close()
            }
        )
        editBar.addView(
            makeBarChip("Listo", accentColor, Color.argb(52, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))) {
                setEditMode(false)
            }
        )

        container.addView(
            editBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )
        container.addView(dotRight, FrameLayout.LayoutParams(px(34), px(34), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            topMargin = barHeightPx / 2
        })
        container.addView(dotBottom, FrameLayout.LayoutParams(px(34), px(34), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        container.addView(dotCorner, FrameLayout.LayoutParams(px(34), px(34), Gravity.BOTTOM or Gravity.END))

        if (widgetView is NexoWidgetHostView) {
            widgetView.onLongPressListener = { setEditMode(true) }
        }

        // Arrastrar cuerpo en modo edición para mover la ventana flotante
        var moveStartX = 0
        var moveStartY = 0
        var moveDownX = 0f
        var moveDownY = 0f
        moveOverlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moveStartX = layout.x
                    moveStartY = layout.y
                    moveDownX = event.rawX
                    moveDownY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    layout.x = (moveStartX + event.rawX - moveDownX).toInt()
                        .coerceIn(0, (context.resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
                    layout.y = (moveStartY + event.rawY - moveDownY).toInt()
                        .coerceIn(0, (context.resources.displayMetrics.heightPixels - layout.height).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(container, layout) }
                }
            }
            true
        }

        fun attachResizeTouch(view: View, allowX: Boolean, allowY: Boolean) {
            var startRawX = 0f
            var startRawY = 0f
            var startW = 0
            var startH = 0
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startW = binding.width
                        startH = binding.height
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nextW = if (allowX) (startW + (event.rawX - startRawX) / density).toInt().coerceIn(120, maxWidth) else startW
                        val nextH = if (allowY) (startH + (event.rawY - startRawY) / density).toInt().coerceIn(80, maxHeight) else startH
                        binding = binding.copy(width = nextW, height = nextH)
                        updateLayout(commit = false)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> updateLayout(commit = true)
                }
                true
            }
        }

        attachResizeTouch(dotRight, allowX = true, allowY = false)
        attachResizeTouch(dotBottom, allowX = false, allowY = true)
        attachResizeTouch(dotCorner, allowX = true, allowY = true)

        runCatching {
            wm.addView(container, layout)
            root = container
            WidgetStore.startListening(context)
            listening = true
            updateLayout(commit = true)
        }.onFailure {
            close()
            Toast.makeText(context, "No se pudo mostrar el widget", Toast.LENGTH_SHORT).show()
        }
    }

    fun close() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        if (listening) WidgetStore.stopListening(context)
        listening = false
    }
}
