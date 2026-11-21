package com.stree.nexo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.PanelStyle
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.theme.visualSpec

internal data class PanelSize(val width: Dp, val height: Dp)

/**
 * Calcula las dimensiones unificadas de todos los paneles a partir del tamaño
 * configurado para el panel principal en Ajustes.
 */
internal fun computePanelSize(
    context: Context,
    maxWidth: Dp,
    maxHeight: Dp,
    maxAllowedHeight: Dp = (maxHeight - 48.dp).coerceAtLeast(180.dp)
): PanelSize {
    val widthFraction = Prefs.panelWidth(context)
    val heightFraction = Prefs.panelHeight(context)
    val maxAllowedWidth = minOf(maxWidth - 8.dp, 520.dp).coerceAtLeast(196.dp)
    val width = (maxWidth * widthFraction).coerceIn(196.dp, maxAllowedWidth)
    val safeMaxHeight = maxAllowedHeight.coerceAtLeast(180.dp)
    val height = (maxHeight * heightFraction).coerceIn(180.dp, safeMaxHeight)
    return PanelSize(width = width, height = height)
}

/**
 * Marco del panel lateral: cada tema aplica su propio acabado multicapa (base
 * translúcida, velo de luz direccional y borde biselado o luminoso).
 */
@Composable
internal fun PanelSurface(
    modifier: Modifier = Modifier,
    styleOverride: PanelStyle? = null,
    lightOverride: Boolean? = null,
    wrapHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val style = styleOverride ?: Prefs.panelStyle(context)
    val light = lightOverride ?: Prefs.panelLightBackground(context)
    val spec = style.visualSpec(light = light)
    val shape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier.border(spec.borderWidthDp.dp, spec.borderBrush, shape),
        shape = shape,
        color = spec.surface.copy(alpha = spec.panelAlpha),
        contentColor = spec.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = if (style == PanelStyle.GLASS || style == PanelStyle.NEON) 26.dp else 22.dp
    ) {
        Column(
            (if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .background(spec.sheenBrush),
            content = content
        )
    }
}

@Composable
internal fun PanelSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, content = content)
}
