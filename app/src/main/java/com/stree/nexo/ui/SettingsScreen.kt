package com.stree.nexo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.PanelStyle
import com.stree.nexo.data.ActivatorStyle
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.theme.PanelTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    panelEnabled: Boolean,
    panelSide: PanelSide,
    contactsEnabled: Boolean,
    hapticsEnabled: Boolean,
    shortcutsBlocked: Boolean,
    onTogglePanel: (Boolean) -> Unit,
    onSideChange: (PanelSide) -> Unit,
    onToggleContacts: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Personaliza el panel y la búsqueda",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }

        item {
            SettingsCard("PANEL LATERAL") {
                SwitchRow(
                    title = "Activar panel lateral",
                    subtitle = "Un tirador en el borde para abrir Nexo desde cualquier app",
                    checked = panelEnabled,
                    onCheckedChange = onTogglePanel
                )

                Text(
                    text = "Posición",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp)
                )

                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = panelSide == PanelSide.START,
                        onClick = { onSideChange(PanelSide.START) },
                        label = { Text("Izquierda") }
                    )
                    FilterChip(
                        selected = panelSide == PanelSide.END,
                        onClick = { onSideChange(PanelSide.END) },
                        label = { Text("Derecha") }
                    )
                }

                PanelAppearanceSettings()
                PanelOrderSettings()
                ActivationZoneSettings(panelSide)

                Text(
                    text = "La barra necesita el permiso \"Mostrar sobre otras apps\". Si desaparece sola, es Android cerrando el servicio en segundo plano: quita las restricciones de batería y vuelve a activar el panel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                OutlinedButton(
                    onClick = onOpenOverlayPermission,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Abrir permiso de superposición")
                }

                OutlinedButton(
                    onClick = onRequestBatteryExemption,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Text("Quitar restricciones de batería")
                }
            }
        }

        item {
            SettingsCard("BÚSQUEDA") {
                SwitchRow(
                    title = "Buscar contactos",
                    subtitle = "Toca un contacto para llamar. El permiso de llamadas se solicita al usarlo; si lo deniegas se abre el marcador.",
                    checked = contactsEnabled,
                    onCheckedChange = onToggleContacts
                )
                SwitchRow(
                    title = "Vibración al abrir",
                    subtitle = "Respuesta háptica al elegir un resultado",
                    checked = hapticsEnabled,
                    onCheckedChange = onToggleHaptics
                )
            }
        }

        if (shortcutsBlocked) {
            item {
                SettingsCard("ATAJOS DE APPS") {
                    Text(
                        text = "Android restringe la lectura de atajos internos de otras apps. Para verlos, Nexo debe ser el lanzador predeterminado. Mientras tanto, usa la búsqueda de apps y los atajos personalizados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                    OutlinedButton(
                        onClick = onOpenHomeSettings,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text("Abrir ajustes de pantalla de inicio")
                    }
                }
            }
        }

        item {
            val context = LocalContext.current
            SettingsCard("SUGERENCIAS POR USO Y HORA") {
                Text(
                    "Nexo aprende de los accesos que abres y de la hora. Para incluir aplicaciones abiertas fuera de Nexo, autoriza el acceso al uso de Android. El historial se procesa solo en este dispositivo.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp)
                )
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }, modifier = Modifier.padding(horizontal = 14.dp)) { Text("Configurar acceso al uso") }
                Text("Al volver, el panel actualiza Recientes, Frecuentes y Para esta hora.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
            }
        }

        item {
            SettingsCard("DATOS") {
                Text(
                    text = "Todo el índice se procesa en tu dispositivo. Nexo no envía aplicaciones, contactos ni búsquedas a ningún servidor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("Volver a indexar")
                }
            }
        }

        item {
            val context = LocalContext.current
            SettingsCard("TECLADO") {
                Text("El modo de una mano se activa en el propio teclado (Gboard, Samsung u otro). Android no permite que Nexo lo fuerce al abrir el panel.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                }, modifier = Modifier.padding(horizontal = 14.dp)) { Text("Ajustes del teclado") }
            }
        }

        item {
            Text(
                text = "Nexo 0.1.0 · Búsqueda universal y atajos\nInspirado en la experiencia de Sesame, con implementación propia y APIs modernas de Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PanelAppearanceSettings() {
    val context = LocalContext.current
    var style by remember { mutableStateOf(Prefs.panelStyle(context)) }
    var height by remember { mutableStateOf(Prefs.panelHeight(context)) }
    var iconScale by remember { mutableStateOf(Prefs.panelIconScale(context)) }
    var columns by remember { mutableStateOf(Prefs.panelColumns(context)) }
    var spacing by remember { mutableStateOf(Prefs.panelItemSpacing(context).toFloat()) }
    var showLabels by remember { mutableStateOf(Prefs.panelShowLabels(context)) }
    var lightBackground by remember { mutableStateOf(Prefs.panelLightBackground(context)) }
    var animateIcons by remember { mutableStateOf(Prefs.animatePanelIcons(context)) }

    val preview = PanelPreviewConfig(
        style = style,
        columns = columns,
        spacing = spacing.roundToInt(),
        iconScale = iconScale,
        labels = showLabels
    )

    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text("APARIENCIA", style = MaterialTheme.typography.labelMedium)
        SwitchRow(
            title = "Fondo claro",
            subtitle = "Fondo claro y texto oscuro en paneles y controles",
            checked = lightBackground,
            onCheckedChange = { lightBackground = it; Prefs.setPanelLightBackground(context, it) }
        )
        PanelStyle.entries.chunked(3).forEach { styles ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                styles.forEach { option ->
                    FilterChip(
                        selected = style == option,
                        onClick = {
                            style = option
                            Prefs.setPanelStyle(context, option)
                        },
                        label = { Text(option.label) }
                    )
                }
            }
        }
        androidx.compose.runtime.key(lightBackground) { PanelPreviewCard(preview) }
        SwitchRow(
            title = "Animar iconos al abrir",
            subtitle = "Entrada breve con escala y opacidad, sin retrasar los accesos",
            checked = animateIcons,
            onCheckedChange = { animateIcons = it; Prefs.setAnimatePanelIcons(context, it) }
        )
        if (style == PanelStyle.GLASS) Text(
            "Cristal clásico, algo más claro, con transparencia y reflejos suaves. Sin desenfoque ni ventanas especiales.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Altura: ${(height * 100).roundToInt()} %",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = height,
            onValueChange = { height = it },
            onValueChangeFinished = { Prefs.setPanelHeight(context, height) },
            valueRange = 0.55f..0.85f,
            steps = 5
        )
        Text(
            "Tamaño de iconos: ${(iconScale * 100).roundToInt()} %",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = iconScale,
            onValueChange = { iconScale = it },
            onValueChangeFinished = { Prefs.setPanelIconScale(context, iconScale) },
            valueRange = 0.75f..1.35f,
            steps = 11
        )
        Text("Columnas", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (3..6).forEach { option ->
                FilterChip(
                    selected = columns == option,
                    onClick = {
                        columns = option
                        Prefs.setPanelColumns(context, option)
                    },
                    label = { Text(option.toString()) }
                )
            }
        }
        Text(
            "Espaciado: ${spacing.roundToInt()} dp",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = spacing,
            onValueChange = { spacing = it },
            onValueChangeFinished = {
                Prefs.setPanelItemSpacing(context, spacing.roundToInt())
            },
            valueRange = 0f..12f,
            steps = 11
        )
        SwitchRow(
            title = "Mostrar etiquetas",
            subtitle = "Muestra el nombre debajo de cada icono del panel",
            checked = showLabels,
            onCheckedChange = {
                showLabels = it
                Prefs.setPanelShowLabels(context, it)
            }
        )
    }
}

@Composable
private fun PanelOrderSettings() {
    val context = LocalContext.current
    var pages by remember { mutableStateOf(Prefs.panelPageOrder(context)) }
    var hidden by remember { mutableStateOf(Prefs.hiddenPanelPages(context)) }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("ORDEN DE LOS PANELES", style = MaterialTheme.typography.labelMedium)
        Text(
            "El primer panel visible se abre al tocar el borde. Ocultar lo quita del carrusel sin borrar sus accesos ni widgets. Puedes restaurarlo aquí.",
            style = MaterialTheme.typography.bodySmall
        )
        pages.forEachIndexed { index, page ->
            fun move(to: Int) {
                val reordered = pages.toMutableList()
                reordered.add(to, reordered.removeAt(index))
                pages = reordered
                Prefs.setPanelPageOrder(context, reordered)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${page.label}", modifier = Modifier.weight(1f))
                Switch(
                    checked = page.name !in hidden,
                    enabled = page.name in hidden || pages.count { it.name !in hidden } > 1,
                    onCheckedChange = { visible ->
                        Prefs.setPanelPageVisible(context, page, visible)
                        hidden = Prefs.hiddenPanelPages(context)
                    }
                )
                IconButton(enabled = index > 0, onClick = { move(index - 1) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Subir ${page.label}")
                }
                IconButton(enabled = index < pages.lastIndex, onClick = { move(index + 1) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Bajar ${page.label}")
                }
            }
        }
    }
}

private data class PanelPreviewConfig(
    val style: PanelStyle,
    val columns: Int,
    val spacing: Int,
    val iconScale: Float,
    val labels: Boolean
)

/** Vista previa del panel: se anima con cada cambio de estilo o cuadrícula. */
@Composable
private fun PanelPreviewCard(config: PanelPreviewConfig) {
    val animatedScale by animateFloatAsState(
        targetValue = config.iconScale,
        animationSpec = tween(180),
        label = "preview-icon-scale"
    )
    val animatedSpacing by animateDpAsState(
        targetValue = config.spacing.dp,
        animationSpec = tween(180),
        label = "preview-spacing"
    )
    AnimatedContent(
        targetState = Triple(config.style, config.columns, config.labels),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "panel-preview"
    ) { target ->
        val previewStyle = target.first
        val previewColumns = target.second
        val previewLabels = target.third
        PanelTheme(previewStyle) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(150.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                shadowElevation = 8.dp
            ) {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(52.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                )
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(previewColumns),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        userScrollEnabled = false,
                        horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                        verticalArrangement = Arrangement.spacedBy(animatedSpacing)
                    ) {
                        repeat(previewColumns * 2) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((30f * animatedScale).dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(
                                                MaterialTheme.colorScheme.primary
                                                    .copy(alpha = 0.26f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size((16f * animatedScale).dp)
                                        )
                                    }
                                    if (previewLabels) {
                                        Text(
                                            text = "App",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivationZoneSettings(side: PanelSide) {
    val context = LocalContext.current
    var width by remember { mutableStateOf(Prefs.activationWidth(context).toFloat()) }
    var height by remember { mutableStateOf(Prefs.activationHeight(context).toFloat()) }
    var position by remember { mutableStateOf(Prefs.panelYFraction(context)) }
    var inset by remember { mutableStateOf(Prefs.activationInset(context).toFloat()) }
    var indicatorWidth by remember { mutableStateOf(Prefs.indicatorWidth(context).toFloat()) }
    var indicatorHeight by remember { mutableStateOf(Prefs.indicatorHeight(context).toFloat()) }
    var showArea by remember { mutableStateOf(Prefs.showActivationArea(context)) }
    val screenHeightDp = context.resources.displayMetrics.run { heightPixels / density }
    val screenWidthDp = context.resources.displayMetrics.run { widthPixels / density }
    val color = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceVariant
    val save = {
        Prefs.setActivationSize(context, width.roundToInt(), height.roundToInt())
        Prefs.setActivationInset(context, inset.roundToInt())
        Prefs.setPanelYFraction(context, position)
        Prefs.setIndicator(context, indicatorWidth.roundToInt(), indicatorHeight.roundToInt(), showArea)
        EdgePanelService.refresh(context)
    }

    Column(Modifier.padding(horizontal = 14.dp)) {
        ActivatorStyleSettings()
        Text("ZONA DE ACTIVACIÓN", style = MaterialTheme.typography.labelMedium)
        Text(
            "Toque: panel. Arriba: brillo. Abajo: volumen. Doble toque: Silencio, Normal y ChatGPT. Jala hacia dentro y sostén quieto hasta la segunda vibración: última app (requiere acceso al uso). Un arrastre horizontal sin sostener no abre Nexo. Azul: área táctil; indicador visible independiente.",
            style = MaterialTheme.typography.bodySmall
        )
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp)) {
            drawRect(background)
            val zoneWidth = width / screenWidthDp * size.width
            val zoneHeight = (height / screenHeightDp * size.height).coerceAtMost(size.height)
            val offset = (size.height - zoneHeight) / 2f
            val edgeGap = inset / screenWidthDp * size.width
            val zoneX = if (side == PanelSide.START) edgeGap else size.width - edgeGap - zoneWidth
            val zoneY = (offset + position * offset).coerceIn(0f, size.height - zoneHeight)
            drawRect(
                color.copy(alpha = 0.3f),
                topLeft = Offset(
                    zoneX,
                    zoneY
                ),
                size = Size(zoneWidth, zoneHeight)
            )
            val markerWidth = indicatorWidth.coerceAtMost(width) / screenWidthDp * size.width
            val markerHeight = indicatorHeight.coerceAtMost(height) / screenHeightDp * size.height
            drawRect(
                color,
                topLeft = Offset(
                    if (side == PanelSide.START) zoneX else zoneX + zoneWidth - markerWidth,
                    zoneY + (zoneHeight - markerHeight) / 2f
                ),
                size = Size(markerWidth, markerHeight)
            )
        }
        Text("ÁREA DE ACCIÓN", style = MaterialTheme.typography.labelMedium)
        SwitchRow(
            title = "Mostrar área de acción",
            subtitle = "Colorea toda la zona táctil para comprobar sus límites. No cambia el indicador.",
            checked = showArea,
            onCheckedChange = { showArea = it; save() }
        )
        Text("Ancho del área: ${width.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = width,
            onValueChange = { width = it },
            onValueChangeFinished = save,
            valueRange = 12f..48f,
            steps = 35
        )
        Text("Separación del borde: ${inset.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = inset,
            onValueChange = { inset = it },
            onValueChangeFinished = save,
            valueRange = 0f..64f,
            steps = 15
        )
        Text("Deja un espacio junto al borde para usar Atrás fuera del indicador.", style = MaterialTheme.typography.bodySmall)
        Text("Alto del área: ${height.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = height,
            onValueChange = { height = it },
            onValueChangeFinished = save,
            valueRange = 48f..200f,
            steps = 37
        )
        Text("Posición vertical: ${((position + 1f) * 50f).roundToInt()} % (arriba → abajo)", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = save,
            valueRange = -1f..1f
        )
        Text(
            "El alto se limita a 200 dp para reducir conflictos con el gesto Atrás. También puedes mover el tirador arrastrándolo verticalmente.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Text("INDICADOR VISUAL", style = MaterialTheme.typography.labelMedium)
        Text("Solo cambia la marca visible, no la zona táctil. El dibujo se limita al tamaño del área de acción.", style = MaterialTheme.typography.bodySmall)
        Text("Ancho del indicador: ${indicatorWidth.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
        Slider(value = indicatorWidth, onValueChange = { indicatorWidth = it },
            onValueChangeFinished = save, valueRange = 3f..24f, steps = 20)
        Text("Alto del indicador: ${indicatorHeight.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
        Slider(value = indicatorHeight, onValueChange = { indicatorHeight = it },
            onValueChangeFinished = save, valueRange = 16f..120f, steps = 25)
    }
}

@Composable
private fun ActivatorStyleSettings() {
    val context = LocalContext.current
    var style by remember { mutableStateOf(Prefs.activatorStyle(context)) }
    var selectedColor by remember { mutableStateOf(Prefs.activatorColor(context)) }
    var opacity by remember { mutableStateOf(Prefs.activatorOpacity(context)) }
    fun refresh() { EdgePanelService.refresh(context) }
    Text("ESTILO DEL ACTIVADOR", style = MaterialTheme.typography.labelMedium)
    ActivatorStyle.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { option ->
                FilterChip(selected = style == option, onClick = {
                    style = option; Prefs.setActivatorStyle(context, option); refresh()
                }, label = { Text(option.label) })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        listOf(0xFF8B80FF, 0xFF21BFA0, 0xFF319BDD, 0xFFE4A139, 0xFFEEEEEE, 0xFF333333).forEach { value ->
            val color = value.toInt()
            Surface(
                modifier = Modifier.size(28.dp).clickable {
                    selectedColor = color; Prefs.setActivatorColor(context, color); refresh()
                },
                shape = CircleShape, color = Color(color),
                border = BorderStroke(if (selectedColor == color) 3.dp else 1.dp,
                    if (selectedColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {}
        }
    }
    Text("Opacidad: ${(opacity * 100).roundToInt()} %", style = MaterialTheme.typography.bodySmall)
    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = .25f..1f,
        onValueChangeFinished = { Prefs.setActivatorOpacity(context, opacity); refresh() })
    Text("Brillo requiere «Modificar ajustes del sistema». Silencio puede requerir acceso a «No molestar»; los controles ofrecen los permisos al usarlos.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
