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
 * Panel frame. The Cristal style imitates dark frosted glass: a deep smoky
 * translucent base, a soft light sheen from the top corner and a thin
 * bevelled edge, keeping contrast over bright wallpapers.
 */
@Composable
internal fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = Prefs.panelStyle(LocalContext.current) == PanelStyle.GLASS
    val shape = RoundedCornerShape(28.dp)
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = .38f),
            Color.White.copy(alpha = .08f),
            Color.White.copy(alpha = .03f),
            Color.White.copy(alpha = .20f)
        )
    )

    Surface(
        modifier = modifier.then(
            if (glass) Modifier.border(1.dp, edge, shape)
            else Modifier
        ),
        shape = shape,
        color = if (glass) Color(0xFF0A121A).copy(alpha = 0.80f)
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
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = .10f),
                            Color.White.copy(alpha = .02f),
                            Color.Black.copy(alpha = .14f)
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
