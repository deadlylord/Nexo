package com.stree.nexo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.stree.nexo.data.PanelStyle
import com.stree.nexo.data.Prefs

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF7F7FC),
    surface = Color(0xFFFDFDFF),
    surfaceVariant = Color(0xFFEAEAF3),
    onSurfaceVariant = Color(0xFF4B4B5C),
    outline = Color(0xFF77778A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4B0FF),
    onPrimary = Color(0xFF1B1464),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0D0D14),
    surface = Color(0xFF15151F),
    surfaceVariant = Color(0xFF252535),
    onSurfaceVariant = Color(0xFFC7C7D6),
    outline = Color(0xFF9191A6)
)

@Composable
fun NexoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun PanelTheme(style: PanelStyle, content: @Composable () -> Unit) {
    if (Prefs.panelLightBackground(LocalContext.current)) {
        val accent = when (style) {
            PanelStyle.MINT -> Color(0xFF00735A)
            PanelStyle.OCEAN -> Color(0xFF00649C)
            PanelStyle.AMBER -> Color(0xFF865500)
            PanelStyle.MONO -> Color(0xFF303030)
            PanelStyle.NEXO -> Color(0xFF5144B5)
            PanelStyle.GLASS -> Color(0xFF285778)
        }
        MaterialTheme(colorScheme = lightColorScheme(
            primary = accent, onPrimary = Color.White,
            surface = Color(0xFFFAFAFD), surfaceVariant = Color(0xFFE8E8EF),
            background = Color(0xFFF5F5FA), onSurface = Color(0xFF20202A),
            onSurfaceVariant = Color(0xFF50505D)
        ), content = content)
        return
    }
    if (style == PanelStyle.NEXO) {
        content()
        return
    }

    val colors = when (style) {
        PanelStyle.MINT -> darkColorScheme(
            primary = Color(0xFF7FE7C4), onPrimary = Color(0xFF06251C),
            surface = Color(0xFF111A17), surfaceVariant = Color(0xFF24352F),
            background = Color(0xFF0B110F), onSurface = Color(0xFFE8FFF6),
            onSurfaceVariant = Color(0xFFB9D2C9)
        )
        PanelStyle.OCEAN -> darkColorScheme(
            primary = Color(0xFF79C9FF), onPrimary = Color(0xFF06263A),
            surface = Color(0xFF101820), surfaceVariant = Color(0xFF203342),
            background = Color(0xFF091017), onSurface = Color(0xFFEAF6FF),
            onSurfaceVariant = Color(0xFFB7CBD9)
        )
        PanelStyle.AMBER -> darkColorScheme(
            primary = Color(0xFFFFC56B), onPrimary = Color(0xFF392300),
            surface = Color(0xFF1C1710), surfaceVariant = Color(0xFF3A3021),
            background = Color(0xFF120E09), onSurface = Color(0xFFFFF5E5),
            onSurfaceVariant = Color(0xFFD9C7A9)
        )
        PanelStyle.MONO -> darkColorScheme(
            primary = Color(0xFFE5E5E5), onPrimary = Color(0xFF202020),
            surface = Color(0xFF151515), surfaceVariant = Color(0xFF303030),
            background = Color(0xFF090909), onSurface = Color.White,
            onSurfaceVariant = Color(0xFFC7C7C7)
        )
        PanelStyle.NEXO -> MaterialTheme.colorScheme
        PanelStyle.GLASS -> darkColorScheme(
            primary = Color(0xFFB5E5FF), onPrimary = Color(0xFF102A39),
            surface = Color(0xFF192A3A), surfaceVariant = Color(0xFF30475D),
            background = Color(0xFF0E1925), onSurface = Color.White,
            onSurfaceVariant = Color(0xFFD3E3F0), outlineVariant = Color(0xFF96B2C9)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
