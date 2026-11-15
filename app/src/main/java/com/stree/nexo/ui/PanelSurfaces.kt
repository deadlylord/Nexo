package com.stree.nexo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.PanelStyle
import com.stree.nexo.data.Prefs

/**
 * Panel frame. The Cristal style draws a coloured gradient border and a denser
 * dark base so icons and labels keep contrast over bright wallpapers.
 */
@Composable
internal fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = Prefs.panelStyle(LocalContext.current) == PanelStyle.GLASS
    val shape = RoundedCornerShape(28.dp)
    val gradientBorder = Brush.sweepGradient(
        listOf(
            Color(0xFF6FE3FF),
            Color(0xFF9C8CFF),
            Color(0xFFFF8FC7),
            Color(0xFFFFC97F),
            Color(0xFF6FE3FF)
        )
    )

    Surface(
        modifier = modifier.then(
            if (glass) Modifier.border(1.6.dp, gradientBorder, shape)
            else Modifier
        ),
        shape = shape,
        color = if (glass) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        border = if (glass) null else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (glass) 24.dp else 20.dp
    ) {
        Column(
            Modifier.fillMaxSize().then(
                if (glass) Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = .16f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                        )
                    )
                ) else Modifier
            ),
            content = content
        )
    }
}

@Composable
internal fun PanelSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, content = content)
}
