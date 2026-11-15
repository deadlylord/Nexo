package com.stree.nexo.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.stree.nexo.R
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.ActivatorStyle

/**
 * El indicador visual es independiente del área táctil de la ventana.
 */
class EdgeHandleView(context: Context) : View(context) {

    var side: PanelSide = PanelSide.END
        set(value) {
            field = value
            invalidate()
        }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nexo_primary)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val pillBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (Prefs.showActivationArea(context)) {
            canvas.drawColor(Color.argb(65, 80, 160, 255))
        }
        val style = Prefs.activatorStyle(context)
        val compact = style == ActivatorStyle.CIRCLE || style == ActivatorStyle.ICON
        val pillWidth = if (compact) dp(24f).coerceAtMost(width.toFloat())
            else dp(Prefs.indicatorWidth(context).toFloat()).coerceAtMost(width.toFloat())
        val pillHeight = if (compact) pillWidth.coerceAtMost(height.toFloat())
            else dp(Prefs.indicatorHeight(context).toFloat()).coerceAtMost(height.toFloat())
        val left = if (side == PanelSide.START) 0f else width - pillWidth
        val top = (height - pillHeight) / 2f

        pillBounds.set(left, top, left + pillWidth, top + pillHeight)

        pillPaint.shader = null
        pillPaint.color = Prefs.activatorColor(context)

        val radius = pillWidth / 2f
        canvas.drawRoundRect(pillBounds, radius, radius, pillPaint)

        val centerX = pillBounds.centerX()
        val centerY = pillBounds.centerY()
        val gap = dp(10f).coerceAtMost(pillHeight / 7f)
        val dotRadius = (pillWidth * 0.11f).coerceAtLeast(dp(1.1f))

        if (style == ActivatorStyle.PILL) {
            for (index in -1..1) {
                canvas.drawCircle(centerX, centerY + index * gap, dotRadius, dotPaint)
            }
        } else if (style == ActivatorStyle.ICON) {
            val oldStyle = dotPaint.style
            dotPaint.style = Paint.Style.STROKE
            dotPaint.strokeWidth = dp(1.7f)
            val direction = if (side == PanelSide.START) 1f else -1f
            val path = android.graphics.Path().apply {
                moveTo(centerX - direction * pillWidth * .12f, centerY - pillHeight * .22f)
                lineTo(centerX + direction * pillWidth * .14f, centerY)
                lineTo(centerX - direction * pillWidth * .12f, centerY + pillHeight * .22f)
            }
            canvas.drawPath(path, dotPaint)
            dotPaint.style = oldStyle
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

}
