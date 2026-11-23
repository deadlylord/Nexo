package com.stree.nexo.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.stree.nexo.data.Haptics
import kotlin.math.abs

/**
 * AppWidgetHostView con detección limpia de pulsación larga (mantener pulsado)
 * que respeta los toques normales y el desplazamiento interno del widget.
 */
class NexoWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPressListener: (() -> Unit)? = null
    var interceptAllTouchesForEdit: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong().coerceIn(420L, 620L)

    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var waitingForLongPress = false

    private val longPressRunnable = Runnable {
        if (waitingForLongPress && onLongPressListener != null) {
            waitingForLongPress = false
            longPressTriggered = true
            Haptics.perform(context, strong = true)
            // Cancela cualquier estado presionado dentro del widget hijo
            val now = android.os.SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            onLongPressListener?.invoke()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (interceptAllTouchesForEdit) return true
        if (onLongPressListener == null) return super.onInterceptTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressTriggered = false
                waitingForLongPress = true
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, longPressTimeoutMs)
            }
            MotionEvent.ACTION_MOVE -> {
                if (waitingForLongPress &&
                    (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop)
                ) {
                    cancelPendingLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                if (longPressTriggered) {
                    longPressTriggered = false
                    return true
                }
            }
        }
        return longPressTriggered || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (interceptAllTouchesForEdit) return false
        if (longPressTriggered) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressTriggered = false
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        cancelPendingLongPress()
        super.onDetachedFromWindow()
    }

    private fun cancelPendingLongPress() {
        waitingForLongPress = false
        handler.removeCallbacks(longPressRunnable)
    }
}

class NexoAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView = NexoWidgetHostView(context)
}
