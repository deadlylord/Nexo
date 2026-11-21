package com.stree.nexo.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.stree.nexo.data.PanelSide

/**
 * Curvas y duraciones compartidas para que todos los paneles laterales entren
 * y salgan siempre desde el borde configurado, sin mezclar animaciones verticales.
 */
object PanelMotion {
    const val ENTER_DURATION_MS = 230
    const val EXIT_DURATION_MS = 175
    const val SCRIM_ENTER_MS = 210
    const val SCRIM_EXIT_MS = 165
    const val LAYOUT_DURATION_MS = 190
    const val TAB_DURATION_MS = 210

    val EnterEasing = CubicBezierEasing(0.18f, 0.90f, 0.22f, 1.0f)
    val ExitEasing = CubicBezierEasing(0.40f, 0.0f, 0.85f, 0.15f)
    val StandardEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

    fun sideEnter(side: PanelSide): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(ENTER_DURATION_MS, easing = EnterEasing),
            initialOffsetX = { width -> if (side == PanelSide.END) width else -width }
        ) + fadeIn(tween(150, easing = EnterEasing))

    fun sideExit(side: PanelSide): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(EXIT_DURATION_MS, easing = ExitEasing),
            targetOffsetX = { width -> if (side == PanelSide.END) width else -width }
        ) + fadeOut(tween(130, easing = ExitEasing))
}

/** Evita que Android aplique su animación vertical/zoom por defecto sobre el panel lateral. */
fun Activity.disableSystemTransitions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

fun Activity.finishNoSystemAnimation(removeTask: Boolean = false) {
    if (removeTask) finishAndRemoveTask() else finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
