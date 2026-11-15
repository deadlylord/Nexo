package com.stree.nexo.data

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class FolderPreviewDrawable(icons: List<Drawable>) : Drawable() {
    private val children = icons.take(4).map { it.constantState?.newDrawable()?.mutate() ?: it }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34465A.toInt() }
    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(),
            bounds.bottom.toFloat(), width * .22f, height * .22f, paint)
        children.forEachIndexed { index, icon ->
            val x = bounds.left + width * (.1f + (index % 2) * .45f)
            val y = bounds.top + height * (.1f + (index / 2) * .45f)
            icon.setBounds(x.toInt(), y.toInt(), (x + width * .35f).toInt(), (y + height * .35f).toInt())
            icon.draw(canvas)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; children.forEach { it.alpha = alpha } }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96
}
