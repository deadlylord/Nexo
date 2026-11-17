package com.stree.nexo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.stree.nexo.data.PanelStyle
import com.stree.nexo.data.Prefs

/**
 * Especificación visual multicapa de cada tema (paleta, brillos de superficie y
 * bisel especular/degradado) compartida entre la app principal, la vista previa
 * y el panel lateral.
 */
data class ThemeVisualSpec(
    val style: PanelStyle,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color,
    val panelAlpha: Float,
    val borderWidthDp: Float,
    val borderColors: List<Color>,
    val sheenColors: List<Color>,
    val swatchColors: List<Color>
) {
    val borderBrush: Brush
        get() = Brush.linearGradient(borderColors)

    val sheenBrush: Brush
        get() = Brush.linearGradient(sheenColors)
}

fun PanelStyle.visualSpec(light: Boolean = false): ThemeVisualSpec {
    if (light) {
        val (primary, secondary, tertiary) = when (this) {
            PanelStyle.NEXO -> Triple(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF06B6D4))
            PanelStyle.GLASS -> Triple(Color(0xFF1D5B79), Color(0xFF38BDF8), Color(0xFF64748B))
            PanelStyle.MINT -> Triple(Color(0xFF047857), Color(0xFF10B981), Color(0xFF0D9488))
            PanelStyle.OCEAN -> Triple(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF38BDF8))
            PanelStyle.AMBER -> Triple(Color(0xFFB45309), Color(0xFFD97706), Color(0xFFF59E0B))
            PanelStyle.NEON -> Triple(Color(0xFF9333EA), Color(0xFF06B6D4), Color(0xFFEC4899))
            PanelStyle.SUNSET -> Triple(Color(0xFFBE123C), Color(0xFFE11D48), Color(0xFFF97316))
            PanelStyle.ROSE -> Triple(Color(0xFF9D174D), Color(0xFFDB2777), Color(0xFFA855F7))
            PanelStyle.MONO -> Triple(Color(0xFF18181B), Color(0xFF52525B), Color(0xFF71717A))
        }
        return ThemeVisualSpec(
            style = this,
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            tertiary = tertiary,
            background = Color(0xFFF4F5FA),
            surface = Color(0xFFFCFCFF),
            surfaceVariant = Color(0xFFE6E8F2),
            onSurface = Color(0xFF141622),
            onSurfaceVariant = Color(0xFF4B5063),
            outlineVariant = primary.copy(alpha = 0.28f),
            panelAlpha = if (this == PanelStyle.GLASS) 0.82f else 0.95f,
            borderWidthDp = 1.2f,
            borderColors = listOf(
                Color.White.copy(alpha = 0.85f),
                primary.copy(alpha = 0.35f),
                secondary.copy(alpha = 0.25f)
            ),
            sheenColors = listOf(
                Color.White.copy(alpha = 0.65f),
                primary.copy(alpha = 0.06f),
                secondary.copy(alpha = 0.08f)
            ),
            swatchColors = listOf(Color(0xFFFCFCFF), primary, secondary)
        )
    }

    return when (this) {
        PanelStyle.NEXO -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFF9B95FF),
            onPrimary = Color(0xFF151136),
            secondary = Color(0xFF5EEAD4),
            tertiary = Color(0xFFF472B6),
            background = Color(0xFF090A14),
            surface = Color(0xFF121426),
            surfaceVariant = Color(0xFF222642),
            onSurface = Color(0xFFF3F4FF),
            onSurfaceVariant = Color(0xFFBDC2E2),
            outlineVariant = Color(0xFF5C6299),
            panelAlpha = 0.90f,
            borderWidthDp = 1.2f,
            borderColors = listOf(
                Color(0xFF9B95FF).copy(alpha = 0.55f),
                Color(0xFF5EEAD4).copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.08f),
                Color(0xFFF472B6).copy(alpha = 0.32f)
            ),
            sheenColors = listOf(
                Color(0xFF9B95FF).copy(alpha = 0.16f),
                Color.Transparent,
                Color(0xFF5EEAD4).copy(alpha = 0.07f)
            ),
            swatchColors = listOf(Color(0xFF121426), Color(0xFF9B95FF), Color(0xFF5EEAD4))
        )

        PanelStyle.GLASS -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFFB5E5FF),
            onPrimary = Color(0xFF0D2533),
            secondary = Color(0xFF93C5FD),
            tertiary = Color(0xFFE0F2FE),
            background = Color(0xFF060C12),
            surface = Color(0xFF0A1118),
            surfaceVariant = Color(0xFF1E2D3B),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFD6E4F0),
            outlineVariant = Color(0xFF8AA3B8),
            panelAlpha = 0.86f,
            borderWidthDp = 1.15f,
            borderColors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.03f),
                Color(0xFFB5E5FF).copy(alpha = 0.24f)
            ),
            sheenColors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.02f),
                Color.Black.copy(alpha = 0.22f)
            ),
            swatchColors = listOf(Color(0xFF0A1118), Color(0xFFB5E5FF), Color(0xFF64748B))
        )

        PanelStyle.MINT -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFF6EE7B7),
            onPrimary = Color(0xFF04261C),
            secondary = Color(0xFF2DD4BF),
            tertiary = Color(0xFF38BDF8),
            background = Color(0xFF07110E),
            surface = Color(0xFF0E1D18),
            surfaceVariant = Color(0xFF1D352D),
            onSurface = Color(0xFFECFDF5),
            onSurfaceVariant = Color(0xFFB2D8C9),
            outlineVariant = Color(0xFF3F7A66),
            panelAlpha = 0.90f,
            borderWidthDp = 1.15f,
            borderColors = listOf(
                Color(0xFF6EE7B7).copy(alpha = 0.50f),
                Color(0xFF2DD4BF).copy(alpha = 0.16f),
                Color(0xFF38BDF8).copy(alpha = 0.28f)
            ),
            sheenColors = listOf(
                Color(0xFF6EE7B7).copy(alpha = 0.15f),
                Color.Transparent,
                Color(0xFF0D9488).copy(alpha = 0.10f)
            ),
            swatchColors = listOf(Color(0xFF0E1D18), Color(0xFF6EE7B7), Color(0xFF2DD4BF))
        )

        PanelStyle.OCEAN -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFF7DD3FC),
            onPrimary = Color(0xFF062436),
            secondary = Color(0xFF38BDF8),
            tertiary = Color(0xFF818CF8),
            background = Color(0xFF060D16),
            surface = Color(0xFF0C1826),
            surfaceVariant = Color(0xFF1A2E44),
            onSurface = Color(0xFFF0F9FF),
            onSurfaceVariant = Color(0xFFB5CDE0),
            outlineVariant = Color(0xFF3B6994),
            panelAlpha = 0.90f,
            borderWidthDp = 1.15f,
            borderColors = listOf(
                Color(0xFF7DD3FC).copy(alpha = 0.48f),
                Color(0xFF0284C7).copy(alpha = 0.14f),
                Color(0xFF818CF8).copy(alpha = 0.28f)
            ),
            sheenColors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.15f),
                Color.Transparent,
                Color(0xFF1E3A8A).copy(alpha = 0.14f)
            ),
            swatchColors = listOf(Color(0xFF0C1826), Color(0xFF7DD3FC), Color(0xFF38BDF8))
        )

        PanelStyle.AMBER -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFFFCD34D),
            onPrimary = Color(0xFF331E00),
            secondary = Color(0xFFF59E0B),
            tertiary = Color(0xFFFB923C),
            background = Color(0xFF100C07),
            surface = Color(0xFF1B140C),
            surfaceVariant = Color(0xFF332617),
            onSurface = Color(0xFFFFFBEB),
            onSurfaceVariant = Color(0xFFDCC7A6),
            outlineVariant = Color(0xFF856235),
            panelAlpha = 0.91f,
            borderWidthDp = 1.15f,
            borderColors = listOf(
                Color(0xFFFCD34D).copy(alpha = 0.50f),
                Color(0xFFD97706).copy(alpha = 0.16f),
                Color(0xFFFB923C).copy(alpha = 0.28f)
            ),
            sheenColors = listOf(
                Color(0xFFF59E0B).copy(alpha = 0.14f),
                Color.Transparent,
                Color(0xFF78350F).copy(alpha = 0.12f)
            ),
            swatchColors = listOf(Color(0xFF1B140C), Color(0xFFFCD34D), Color(0xFFFB923C))
        )

        PanelStyle.NEON -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFF22D3EE),
            onPrimary = Color(0xFF04242C),
            secondary = Color(0xFFE879F9),
            tertiary = Color(0xFFA855F7),
            background = Color(0xFF080512),
            surface = Color(0xFF110B22),
            surfaceVariant = Color(0xFF251A42),
            onSurface = Color(0xFFF8F5FF),
            onSurfaceVariant = Color(0xFFCBC0E8),
            outlineVariant = Color(0xFF7E42B3),
            panelAlpha = 0.89f,
            borderWidthDp = 1.4f,
            borderColors = listOf(
                Color(0xFF22D3EE).copy(alpha = 0.72f),
                Color(0xFFA855F7).copy(alpha = 0.35f),
                Color(0xFFE879F9).copy(alpha = 0.65f)
            ),
            sheenColors = listOf(
                Color(0xFF22D3EE).copy(alpha = 0.16f),
                Color(0xFFA855F7).copy(alpha = 0.05f),
                Color(0xFFE879F9).copy(alpha = 0.14f)
            ),
            swatchColors = listOf(Color(0xFF110B22), Color(0xFF22D3EE), Color(0xFFE879F9))
        )

        PanelStyle.SUNSET -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFFFB7185),
            onPrimary = Color(0xFF330611),
            secondary = Color(0xFFFBBF24),
            tertiary = Color(0xFFC084FC),
            background = Color(0xFF12080E),
            surface = Color(0xFF200F19),
            surfaceVariant = Color(0xFF391D2E),
            onSurface = Color(0xFFFFF1F3),
            onSurfaceVariant = Color(0xFFE5BFD0),
            outlineVariant = Color(0xFF8C4666),
            panelAlpha = 0.90f,
            borderWidthDp = 1.2f,
            borderColors = listOf(
                Color(0xFFFB7185).copy(alpha = 0.55f),
                Color(0xFFC084FC).copy(alpha = 0.24f),
                Color(0xFFFBBF24).copy(alpha = 0.38f)
            ),
            sheenColors = listOf(
                Color(0xFFFB7185).copy(alpha = 0.16f),
                Color.Transparent,
                Color(0xFFF59E0B).copy(alpha = 0.09f)
            ),
            swatchColors = listOf(Color(0xFF200F19), Color(0xFFFB7185), Color(0xFFFBBF24))
        )

        PanelStyle.ROSE -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFFF4A8C8),
            onPrimary = Color(0xFF2E0D1E),
            secondary = Color(0xFFD8B4FE),
            tertiary = Color(0xFFFBCFE8),
            background = Color(0xFF0E0910),
            surface = Color(0xFF19111C),
            surfaceVariant = Color(0xFF2F2135),
            onSurface = Color(0xFFFCF5F8),
            onSurfaceVariant = Color(0xFFD9C5D2),
            outlineVariant = Color(0xFF7A5972),
            panelAlpha = 0.90f,
            borderWidthDp = 1.15f,
            borderColors = listOf(
                Color(0xFFF4A8C8).copy(alpha = 0.48f),
                Color.White.copy(alpha = 0.08f),
                Color(0xFFD8B4FE).copy(alpha = 0.32f)
            ),
            sheenColors = listOf(
                Color(0xFFF4A8C8).copy(alpha = 0.13f),
                Color.Transparent,
                Color(0xFFD8B4FE).copy(alpha = 0.08f)
            ),
            swatchColors = listOf(Color(0xFF19111C), Color(0xFFF4A8C8), Color(0xFFD8B4FE))
        )

        PanelStyle.MONO -> ThemeVisualSpec(
            style = this,
            primary = Color(0xFFF4F4F5),
            onPrimary = Color(0xFF111113),
            secondary = Color(0xFFA1A1AA),
            tertiary = Color(0xFFD4D4D8),
            background = Color(0xFF000000),
            surface = Color(0xFF09090B),
            surfaceVariant = Color(0xFF1F1F23),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB4B4BE),
            outlineVariant = Color(0xFF52525B),
            panelAlpha = 0.96f,
            borderWidthDp = 1.0f,
            borderColors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.20f)
            ),
            sheenColors = listOf(
                Color.White.copy(alpha = 0.06f),
                Color.Transparent,
                Color.White.copy(alpha = 0.02f)
            ),
            swatchColors = listOf(Color(0xFF050505), Color(0xFFF4F4F5), Color(0xFF71717A))
        )
    }
}

private fun ThemeVisualSpec.toColorScheme(light: Boolean): ColorScheme =
    if (light) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.copy(alpha = 0.16f),
            onSecondaryContainer = secondary,
            tertiary = tertiary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = onSurface,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            outline = outlineVariant,
            outlineVariant = outlineVariant
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = onSurface,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = secondary.copy(alpha = 0.20f),
            onSecondaryContainer = onSurface,
            tertiary = tertiary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = onSurface,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            outline = outlineVariant,
            outlineVariant = outlineVariant
        )
    }

@Composable
fun NexoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Se suscribe a cambios en vivo de tema, modo claro y color dinámico.
    val revision = Prefs.themeRevision
    val style = rememberStyle(context, revision)
    val lightPref = Prefs.panelLightBackground(context)
    val dynamic = Prefs.appDynamicColor(context)

    val colorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (lightPref) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
        }
        else -> style.visualSpec(light = lightPref).toColorScheme(light = lightPref)
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface, content = content)
    }
}

@Composable
private fun rememberStyle(context: android.content.Context, revision: Int): PanelStyle =
    Prefs.panelStyle(context)

@Composable
fun PanelTheme(
    style: PanelStyle,
    lightOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val revision = Prefs.themeRevision
    val light = lightOverride ?: Prefs.panelLightBackground(context)
    val spec = style.visualSpec(light = light)
    val scheme = spec.toColorScheme(light = light)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}
