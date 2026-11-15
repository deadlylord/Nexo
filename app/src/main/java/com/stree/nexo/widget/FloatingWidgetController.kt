package com.stree.nexo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.*
import android.widget.*

/** Ventana overlay real: el widget sigue sobre otras aplicaciones hasta cerrarlo. */
class FloatingWidgetController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var listening = false

    fun show(item: String) {
        close()
        if (!Settings.canDrawOverlays(context)) return
        var binding = WidgetStore.get(context, item) ?: return
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(binding.id) ?: run {
            Toast.makeText(context, "El widget ya no está disponible. Asígnalo de nuevo en Gestos.", Toast.LENGTH_LONG).show()
            return
        }
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()
        val maxWidth = (context.resources.displayMetrics.widthPixels / density).toInt() - 16
        val maxHeight = (context.resources.displayMetrics.heightPixels / density).toInt() - 120
        binding = binding.copy(width = binding.width.coerceIn(120, maxWidth.coerceAtLeast(120)),
            height = binding.height.coerceIn(80, maxHeight.coerceAtLeast(80)))
        val layout = WindowManager.LayoutParams(px(binding.width), px(binding.height + 40),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px(8); y = px(100)
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEEEEF4.toInt())
            elevation = px(12).toFloat()
        }
        val header = LinearLayout(context)
        val title = TextView(context).apply {
            text = "⋮⋮ ${info.loadLabel(context.packageManager)}"
            setTextColor(0xFF20202A.toInt()); gravity = Gravity.CENTER_VERTICAL
            setPadding(px(8), 0, 0, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, px(40), 1f))
        val closeButton = Button(context).apply { text = "×"; setOnClickListener { close() } }
        header.addView(closeButton, LinearLayout.LayoutParams(px(48), px(40)))
        column.addView(header)
        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f
        title.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = layout.x; startY = layout.y; downX = event.rawX; downY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    layout.x = (startX + event.rawX - downX).toInt().coerceIn(0, (context.resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
                    layout.y = (startY + event.rawY - downY).toInt().coerceIn(0,
                        (context.resources.displayMetrics.heightPixels - layout.height).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(column, layout) }
                }
            }
            true
        }
        val host = WidgetStore.host(context)
        val widget = host.createView(context, binding.id, info)
        val content = FrameLayout(context)
        content.addView(widget, FrameLayout.LayoutParams(-1, -1))
        column.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        fun saveSize(commit: Boolean = true) {
            layout.width = px(binding.width); layout.height = px(binding.height + 40)
            layout.x = layout.x.coerceAtMost((context.resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
            layout.y = layout.y.coerceAtMost((context.resources.displayMetrics.heightPixels - layout.height).coerceAtLeast(0))
            if (commit && android.os.Build.VERSION.SDK_INT >= 31) {
                widget.updateAppWidgetSize(android.os.Bundle(), listOf(android.util.SizeF(binding.width.toFloat(), binding.height.toFloat())))
            } else if (commit) {
                @Suppress("DEPRECATION")
                widget.updateAppWidgetSize(null, binding.width, binding.height, binding.width, binding.height)
            }
            if (commit) WidgetStore.put(context, item, binding)
            wm.updateViewLayout(column, layout)
        }
        val corner = TextView(context).apply {
            text = "◢"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(0xFF5144B5.toInt()); setBackgroundColor(0xCCEEEEF4.toInt())
            contentDescription = "Arrastrar esquina para cambiar tamaño"
        }
        content.addView(corner, FrameLayout.LayoutParams(px(32), px(32), Gravity.BOTTOM or Gravity.END))
        var resizeX = 0f; var resizeY = 0f; var startWidth = 0; var startHeight = 0
        corner.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeX = event.rawX; resizeY = event.rawY
                    startWidth = binding.width; startHeight = binding.height
                }
                MotionEvent.ACTION_MOVE -> {
                    binding = binding.copy(
                        width = (startWidth + (event.rawX - resizeX) / density).toInt().coerceIn(120, maxWidth),
                        height = (startHeight + (event.rawY - resizeY) / density).toInt().coerceIn(80, maxHeight)
                    )
                    runCatching { saveSize(commit = false) }
                }
                MotionEvent.ACTION_UP -> runCatching { saveSize() }
                MotionEvent.ACTION_CANCEL -> runCatching { saveSize() }
            }
            true
        }
        runCatching {
            wm.addView(column, layout)
            root = column
            WidgetStore.startListening(context)
            listening = true
            saveSize()
        }.onFailure { close(); Toast.makeText(context, "No se pudo mostrar el widget", Toast.LENGTH_SHORT).show() }
    }

    fun close() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        if (listening) WidgetStore.stopListening(context)
        listening = false
    }
}
