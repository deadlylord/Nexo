package com.stree.nexo.data

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Dibuja los iconos de una pareja superpuestos, manteniendo ambos reconocibles. */
class AppPairDrawable(first: Drawable, second: Drawable) : Drawable() {
    private val firstIcon = first.constantState?.newDrawable()?.mutate() ?: first.mutate()
    private val secondIcon = second.constantState?.newDrawable()?.mutate() ?: second.mutate()

    override fun draw(canvas: Canvas) {
        val size = (min(bounds.width(), bounds.height()) * 0.72f).toInt()
        val top = bounds.top + (bounds.height() - size) / 2
        val firstLeft = bounds.left
        val secondLeft = bounds.right - size

        firstIcon.bounds = Rect(firstLeft, top, firstLeft + size, top + size)
        secondIcon.bounds = Rect(secondLeft, top, secondLeft + size, top + size)
        firstIcon.draw(canvas)
        secondIcon.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        firstIcon.alpha = alpha
        secondIcon.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        firstIcon.colorFilter = colorFilter
        secondIcon.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = 96
    override fun getIntrinsicHeight(): Int = 96
}
