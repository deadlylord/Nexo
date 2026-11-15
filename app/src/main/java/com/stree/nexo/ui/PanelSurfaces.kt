package com.stree.nexo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

/** Classic translucent frame: no native blur, floating theme, or window-background changes. */
@Composable
internal fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = Prefs.panelStyle(LocalContext.current) == PanelStyle.GLASS
    Surface(
        modifier = modifier, shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (glass) .76f else .94f),
        border = BorderStroke(1.dp, (if (glass) Color.White else MaterialTheme.colorScheme.outlineVariant)
            .copy(alpha = if (glass) .45f else .35f)),
        tonalElevation = 0.dp, shadowElevation = 20.dp
    ) {
        Column(Modifier.fillMaxSize().then(if (glass) Modifier.background(Brush.linearGradient(listOf(
            Color.White.copy(alpha = .21f), Color.Transparent,
            MaterialTheme.colorScheme.primary.copy(alpha = .1f)
        ))) else Modifier), content = content)
    }
}

@Composable
internal fun PanelSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, content = content)
}
