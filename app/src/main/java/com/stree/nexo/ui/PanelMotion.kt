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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.stree.nexo.data.PanelSide
import kotlin.math.abs

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
    const val TAB_DURATION_MS = 230
    const val SPOTLIGHT_ENTER_DURATION_MS = 310
    const val SPOTLIGHT_EXIT_DURATION_MS = 210

    val EnterEasing = CubicBezierEasing(0.18f, 0.90f, 0.22f, 1.0f)
    val ExitEasing = CubicBezierEasing(0.40f, 0.0f, 0.85f, 0.15f)
    val StandardEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
    val IslandEmergeEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)

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

/**
 * Anima cada página del carrusel como un panel completo e independiente:
 * el nuevo panel emerge desde el borde configurado mientras el panel anterior
 * retrocede en profundidad y se desvanece suavemente.
 */
@Composable
internal fun Modifier.panelCardTransition(
    pagerState: PagerState,
    page: Int,
    side: PanelSide
): Modifier {
    val isEmerging by remember(pagerState, page, side) {
        derivedStateOf {
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            if (side == PanelSide.END) offset <= 0f else offset >= 0f
        }
    }
    return this
        .zIndex(if (isEmerging) 1f else 0f)
        .graphicsLayer {
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val emerging = if (side == PanelSide.END) offset <= 0f else offset >= 0f
            val fraction = abs(offset).coerceIn(0f, 1f)
            if (emerging) {
                translationX = 0f
                val scale = 1f - fraction * 0.05f
                scaleX = scale
                scaleY = scale
                alpha = (1f - fraction * 0.35f).coerceIn(0f, 1f)
            } else {
                translationX = offset * size.width * 0.88f
                val scale = 1f - fraction * 0.12f
                scaleX = scale
                scaleY = scale
                alpha = (1f - fraction * 1.30f).coerceIn(0f, 1f)
            }
        }
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
