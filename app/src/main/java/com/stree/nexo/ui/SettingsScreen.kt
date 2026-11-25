package com.stree.nexo.ui

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stree.nexo.data.ActivatorStyle
import com.stree.nexo.data.EdgeGesture
import com.stree.nexo.data.EdgeGestureAction
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelPage
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.PanelStyle
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.UsageStore
import com.stree.nexo.media.MediaControlService
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.theme.PanelTheme
import com.stree.nexo.ui.theme.visualSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class SettingsSectionFilter(val label: String, val subtitle: String, val icon: ImageVector) {
    OVERVIEW("Resumen", "Accesos directos a cada apartado", Icons.Filled.Home),
    ISLAND("Isla", "Cámara perforada, música, avisos y Hub", Icons.Filled.Layers),
    GESTURES("Gestos", "Elige qué hace cada uno de los 6 gestos", Icons.Filled.Gesture),
    TOUCH("Tirador", "Barra viva, color y zona táctil lateral", Icons.Filled.TouchApp),
    THEMES("Tema y Panel", "Estilos, modo claro y tamaño del panel", Icons.Filled.ColorLens),
    PAGES("Páginas", "Orden del carrusel y desplazamiento infinito", Icons.Filled.ViewCarousel),
    SYSTEM("Sistema", "Permisos, contactos, uso y batería", Icons.Filled.Settings)
}

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
    modifier: Modifier = Modifier,
    initialSection: SettingsSectionFilter = SettingsSectionFilter.OVERVIEW
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayGranted = remember(resumeTick, panelEnabled) { Settings.canDrawOverlays(context) }
    val batteryUnrestricted = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val usageGranted = remember(resumeTick) { UsageStore(context).hasUsageAccess() }
    val mediaAccessGranted = remember(resumeTick) { MediaControlService.isAuthorized(context) }

    var activeFilter by remember(initialSection) { mutableStateOf(initialSection) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabecera y barra de categorías
        item {
            SettingsHeader(
                activeFilter = activeFilter,
                onSelectFilter = { activeFilter = it }
            )
        }

        // En Resumen mostramos el estado general + tarjetas limpias por apartado
        if (activeFilter == SettingsSectionFilter.OVERVIEW) {
            item {
                PanelHeroCard(
                    panelEnabled = panelEnabled,
                    panelSide = panelSide,
                    overlayGranted = overlayGranted,
                    batteryUnrestricted = batteryUnrestricted,
                    onTogglePanel = onTogglePanel,
                    onSideChange = onSideChange,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                    onRequestBatteryExemption = onRequestBatteryExemption
                )
            }
            item {
                SettingsCategoryHub(onSelect = { activeFilter = it })
            }
        }

        // Apartado 1: Isla Dinámica OLED y calibración de cámara
        if (activeFilter == SettingsSectionFilter.ISLAND) {
            item {
                DynamicIslandCard()
            }
        }

        // Apartado 2: Gestos personalizables del borde
        if (activeFilter == SettingsSectionFilter.GESTURES) {
            item {
                EdgeGesturesCard()
            }
        }

        // Apartado 3: Barra viva lateral y calibrador del tirador
        if (activeFilter == SettingsSectionFilter.TOUCH) {
            item {
                EdgeLiveAlertsCard()
            }
            item {
                ActivationZoneCard(side = panelSide)
            }
        }

        // Apartado 4: Temas y apariencia del panel
        if (activeFilter == SettingsSectionFilter.THEMES) {
            item {
                PanelAppearanceCard()
            }
        }

        // Apartado 5: Orden y visibilidad de páginas del panel
        if (activeFilter == SettingsSectionFilter.PAGES) {
            item {
                PanelOrderCard()
            }
        }

        // Apartado 6: Sistema, permisos y diagnóstico
        if (activeFilter == SettingsSectionFilter.SYSTEM) {
            item {
                SettingsSectionCard(
                    title = "Búsqueda y respuesta",
                    subtitle = "Contactos directos y respuesta háptica",
                    icon = Icons.Filled.Search
                ) {
                    SwitchRow(
                        title = "Buscar contactos",
                        subtitle = "Llama o abre WhatsApp desde los resultados. El permiso se solicita al usarlo.",
                        checked = contactsEnabled,
                        onCheckedChange = onToggleContacts
                    )
                    SectionDivider()
                    SwitchRow(
                        title = "Respuesta háptica",
                        subtitle = "Vibración sutil al abrir resultados, fijar iconos o usar gestos del borde",
                        checked = hapticsEnabled,
                        onCheckedChange = onToggleHaptics
                    )
                }
            }

            item {
                SettingsSectionCard(
                    title = "Aprendizaje por uso y hora",
                    subtitle = "Recientes, frecuentes y sugerencias horarias",
                    icon = Icons.Filled.History,
                    badgeText = if (usageGranted) "Activo" else "Opcional",
                    badgeActive = usageGranted
                ) {
                    Text(
                        text = "Nexo aprende de los accesos que abres y de la franja horaria. Si autorizas el acceso al uso de Android, también detecta las apps abiertas fuera de Nexo y habilita el gesto de volver a la última app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (usageGranted) Icons.Filled.CheckCircle else Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (usageGranted) "Acceso al uso concedido" else "Autorizar acceso al uso")
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Control multimedia",
                    subtitle = "Doble toque para abrir la app de música en reproducción",
                    icon = Icons.Filled.MusicNote,
                    badgeText = if (mediaAccessGranted) "Activo" else "Opcional",
                    badgeActive = mediaAccessGranted
                ) {
                    Text(
                        text = "Autoriza el acceso a notificaciones para detectar qué app está reproduciendo música y abrirla directamente al dar doble toque en el botón Reproducir/Pausar del panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (mediaAccessGranted) Icons.Filled.CheckCircle else Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (mediaAccessGranted) "Acceso multimedia concedido" else "Autorizar acceso multimedia")
                        }
                    }
                }
            }

            if (shortcutsBlocked) {
                item {
                    SettingsSectionCard(
                        title = "Atajos internos de apps",
                        subtitle = "Restricción del lanzador de Android",
                        icon = Icons.Filled.Home
                    ) {
                        Text(
                            text = "Android solo permite leer los atajos internos de otras aplicaciones al lanzador predeterminado. Mientras tanto, puedes crear todos los atajos personalizados que quieras en la pestaña Atajos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        OutlinedButton(
                            onClick = onOpenHomeSettings,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ajustes de pantalla de inicio")
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Índice local y teclado",
                    subtitle = "Privacidad 100 % en el dispositivo",
                    icon = Icons.Filled.Storage
                ) {
                    Text(
                        text = "Todo el índice de aplicaciones, contactos y sugerencias se procesa localmente en tu teléfono. Nexo no envía datos a ningún servidor externo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reindexar ahora")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Teclado")
                        }
                    }
                }
            }
        }

        // Pie informativo
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Nexo · Centro de control y lanzador lateral",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Búsqueda instantánea, gestos en el borde y temas multicapa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    activeFilter: SettingsSectionFilter,
    onSelectFilter: (SettingsSectionFilter) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Personaliza el estilo visual, el área táctil y el comportamiento de Nexo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSectionFilter.entries.forEach { filter ->
                val selected = filter == activeFilter
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    border = if (selected) null else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.clickable { onSelectFilter(filter) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = filter.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeroCard(
    panelEnabled: Boolean,
    panelSide: PanelSide,
    overlayGranted: Boolean,
    batteryUnrestricted: Boolean,
    onTogglePanel: (Boolean) -> Unit,
    onSideChange: (PanelSide) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    val context = LocalContext.current
    val style = Prefs.panelStyle(context)
    val spec = style.visualSpec(light = Prefs.panelLightBackground(context))
    val shape = RoundedCornerShape(24.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, spec.borderBrush, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(spec.sheenBrush)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (panelEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = if (panelEnabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Panel lateral flotante",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            text = if (panelEnabled) "ACTIVO" else "PAUSADO",
                            active = panelEnabled
                        )
                    }
                    Text(
                        text = if (panelEnabled) {
                            "Tirador activo en el borde ${if (panelSide == PanelSide.START) "izquierdo" else "derecho"}"
                        } else {
                            "Actívalo para abrir Nexo desde cualquier aplicación"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = panelEnabled,
                    onCheckedChange = onTogglePanel
                )
            }

            Spacer(Modifier.height(14.dp))

            // Selector segmentado de borde
            Text(
                text = "Borde de activación",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    PanelSide.START to "Borde izquierdo",
                    PanelSide.END to "Borde derecho"
                ).forEach { (option, label) ->
                    val selected = panelSide == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { onSideChange(option) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Desplegar automáticamente al desbloquear el celular
            var autoOpenUnlock by remember { mutableStateOf(Prefs.autoOpenOnUnlock(context)) }
            var autoOpenSpotlight by remember { mutableStateOf(Prefs.autoOpenOnUnlockSpotlight(context)) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Desplegar al desbloquear",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (autoOpenUnlock) {
                                    if (autoOpenSpotlight) "Abre Spotlight automáticamente al desbloquear"
                                    else "Abre el panel lateral automáticamente al desbloquear"
                                } else {
                                    "Abre Nexo solo en cuanto desbloquees el teléfono"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoOpenUnlock,
                            onCheckedChange = { enabled ->
                                Haptics.perform(context)
                                autoOpenUnlock = enabled
                                Prefs.setAutoOpenOnUnlock(context, enabled)
                                if (enabled && !panelEnabled) {
                                    onTogglePanel(true)
                                }
                            }
                        )
                    }

                    if (autoOpenUnlock) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                false to "Panel lateral",
                                true to "Spotlight"
                            ).forEach { (isSpotlight, label) ->
                                val selected = autoOpenSpotlight == isSpotlight
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            Haptics.perform(context)
                                            autoOpenSpotlight = isSpotlight
                                            Prefs.setAutoOpenOnUnlockSpotlight(context, isSpotlight)
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Burbuja y menú profesional de ventanas flotantes
            var floatingBubbleEnabled by remember { mutableStateOf(Prefs.floatingBubbleEnabled(context)) }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Burbuja y menú flotante",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Al minimizar una ventana flotante queda en burbuja: 1 toque reabre en flotante, doble toque abre menú y pantalla dividida",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = floatingBubbleEnabled,
                        onCheckedChange = { enabled ->
                            Haptics.perform(context)
                            floatingBubbleEnabled = enabled
                            Prefs.setFloatingBubbleEnabled(context, enabled)
                            if (!enabled) {
                                com.stree.nexo.floating.FloatingWindowRegistry.clearAll(context)
                            } else {
                                EdgePanelService.syncFloatingBubble(context)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Apertura automática en Spotlight al coincidir app o contacto
            var spotlightAutoOpen by remember { mutableStateOf(Prefs.spotlightAutoOpen(context)) }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Apertura automática en Spotlight",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Abre automáticamente la app o la ficha del contacto al coincidir por voz o teclado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = spotlightAutoOpen,
                        onCheckedChange = { enabled ->
                            Haptics.perform(context)
                            spotlightAutoOpen = enabled
                            Prefs.setSpotlightAutoOpen(context, enabled)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Estado de permisos clave del servicio en segundo plano
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionStatusChip(
                    modifier = Modifier.weight(1f),
                    title = "Superposición",
                    status = if (overlayGranted) "Concedida" else "Permitir",
                    granted = overlayGranted,
                    icon = Icons.Filled.Layers,
                    onClick = onOpenOverlayPermission
                )
                PermissionStatusChip(
                    modifier = Modifier.weight(1f),
                    title = "Batería",
                    status = if (batteryUnrestricted) "Sin límites" else "Quitar límite",
                    granted = batteryUnrestricted,
                    icon = Icons.Filled.BatteryChargingFull,
                    onClick = onRequestBatteryExemption
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusChip(
    modifier: Modifier,
    title: String,
    status: String,
    granted: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val accent = if (granted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.Check else icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PanelAppearanceCard() {
    val context = LocalContext.current
    var style by remember { mutableStateOf(Prefs.panelStyle(context)) }
    var width by remember { mutableFloatStateOf(Prefs.panelWidth(context)) }
    var height by remember { mutableFloatStateOf(Prefs.panelHeight(context)) }
    var iconScale by remember { mutableFloatStateOf(Prefs.panelIconScale(context)) }
    var columns by remember { mutableIntStateOf(Prefs.panelColumns(context)) }
    var spacing by remember { mutableFloatStateOf(Prefs.panelItemSpacing(context).toFloat()) }
    var showLabels by remember { mutableStateOf(Prefs.panelShowLabels(context)) }
    var lightBackground by remember { mutableStateOf(Prefs.panelLightBackground(context)) }
    var dynamicColor by remember { mutableStateOf(Prefs.appDynamicColor(context)) }
    var animateIcons by remember { mutableStateOf(Prefs.animatePanelIcons(context)) }

    val preview = PanelPreviewConfig(
        style = style,
        columns = columns,
        spacing = spacing.roundToInt(),
        iconScale = iconScale,
        labels = showLabels,
        light = lightBackground,
        width = width,
        height = height
    )

    SettingsSectionCard(
        title = "Temas y apariencia",
        subtitle = "Acabados multicapa para la aplicación y el panel lateral",
        icon = Icons.Filled.ColorLens,
        badgeText = style.label,
        badgeActive = true
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                text = "GALERÍA DE TEMAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(8.dp))

            // Cuadrícula de tarjetas de tema con muestra real de degradado y borde
            PanelStyle.entries.chunked(3).forEach { rowStyles ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowStyles.forEach { option ->
                        ThemeSwatchCard(
                            modifier = Modifier.weight(1f),
                            option = option,
                            light = lightBackground,
                            selected = style == option,
                            onClick = {
                                style = option
                                Prefs.setPanelStyle(context, option)
                                EdgePanelService.refresh(context)
                            }
                        )
                    }
                    repeat(3 - rowStyles.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Descripción del tema activo
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ColorLens,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${style.label}: ${style.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Vista previa interactiva del panel con el tema elegido
            PanelPreviewCard(preview)
        }

        Spacer(Modifier.height(6.dp))
        SectionDivider()

        SwitchRow(
            title = "Modo claro",
            subtitle = "Usa superficies claras en los temas de la app y del panel",
            checked = lightBackground,
            onCheckedChange = {
                lightBackground = it
                Prefs.setPanelLightBackground(context, it)
                EdgePanelService.refresh(context)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SectionDivider()
            SwitchRow(
                title = "Color dinámico en la app (Material You)",
                subtitle = "Usa los colores de tu fondo de pantalla en este menú de ajustes (el panel conserva su tema)",
                checked = dynamicColor,
                onCheckedChange = {
                    dynamicColor = it
                    Prefs.setAppDynamicColor(context, it)
                }
            )
        }

        SectionDivider()
        SwitchRow(
            title = "Mostrar nombres de apps",
            subtitle = "Muestra la etiqueta de texto debajo de cada icono del panel",
            checked = showLabels,
            onCheckedChange = {
                showLabels = it
                Prefs.setPanelShowLabels(context, it)
            }
        )

        SectionDivider()
        SwitchRow(
            title = "Animar iconos al abrir",
            subtitle = "Entrada suave de escala y opacidad sin retrasar la pulsación",
            checked = animateIcons,
            onCheckedChange = {
                animateIcons = it
                Prefs.setAnimatePanelIcons(context, it)
            }
        )

        SectionDivider()

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Columnas de la cuadrícula",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                ValuePill("${columns} col")
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (3..6).forEach { option ->
                    val active = columns == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .clickable {
                                columns = option
                                Prefs.setPanelColumns(context, option)
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$option",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "TAMAÑO DEL PANEL (APLICADO A TODOS LOS PANELES)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))

            SteppedSlider(
                label = "Ancho del panel",
                valueText = "${(width * 100).roundToInt()} %",
                value = width,
                valueRange = 0.45f..0.90f,
                stepAmount = 0.02f,
                onValueChange = { width = it },
                onValueChangeFinished = { Prefs.setPanelWidth(context, width) }
            )

            SteppedSlider(
                label = "Altura del panel",
                valueText = "${(height * 100).roundToInt()} %",
                value = height,
                valueRange = 0.30f..0.85f,
                stepAmount = 0.02f,
                onValueChange = { height = it },
                onValueChangeFinished = { Prefs.setPanelHeight(context, height) }
            )

            LabeledSlider(
                label = "Tamaño de los iconos",
                valueText = "${(iconScale * 100).roundToInt()} %",
                value = iconScale,
                valueRange = 0.75f..1.35f,
                steps = 11,
                onValueChange = { iconScale = it },
                onValueChangeFinished = { Prefs.setPanelIconScale(context, iconScale) }
            )

            LabeledSlider(
                label = "Espaciado entre iconos",
                valueText = "${spacing.roundToInt()} dp",
                value = spacing,
                valueRange = 0f..12f,
                steps = 11,
                onValueChange = { spacing = it },
                onValueChangeFinished = { Prefs.setPanelItemSpacing(context, spacing.roundToInt()) }
            )
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    modifier: Modifier,
    option: PanelStyle,
    light: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val spec = option.visualSpec(light = light)
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                brush = if (selected) {
                    Brush.linearGradient(listOf(spec.primary, spec.secondary))
                } else {
                    spec.borderBrush
                },
                shape = shape
            )
            .clickable(onClick = onClick),
        shape = shape,
        color = spec.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(spec.sheenBrush)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Esferas de color de la paleta del tema
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    spec.swatchColors.drop(1).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, spec.surface, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(spec.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Seleccionado",
                            tint = spec.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = spec.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class PanelPreviewConfig(
    val style: PanelStyle,
    val columns: Int,
    val spacing: Int,
    val iconScale: Float,
    val labels: Boolean,
    val light: Boolean,
    val width: Float,
    val height: Float
)

/** Vista previa en vivo del panel lateral con su acabado real multicapa. */
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
    val animatedWidthFraction by animateFloatAsState(
        targetValue = (0.58f + ((config.width - 0.45f) / 0.45f) * 0.42f).coerceIn(0.58f, 1f),
        animationSpec = tween(180),
        label = "preview-width"
    )
    val animatedHeight by animateDpAsState(
        targetValue = (146f + ((config.height - 0.30f) / 0.55f) * 96f).coerceIn(146f, 242f).dp,
        animationSpec = tween(180),
        label = "preview-height"
    )
    AnimatedContent(
        targetState = Triple(config.style, config.columns, config.labels to config.light),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "panel-preview"
    ) { target ->
        val previewStyle = target.first
        val previewColumns = target.second
        val (previewLabels, previewLight) = target.third
        val spec = previewStyle.visualSpec(light = previewLight)
        PanelTheme(previewStyle, lightOverride = previewLight) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                PanelSurface(
                    styleOverride = previewStyle,
                    lightOverride = previewLight,
                    modifier = Modifier
                        .fillMaxWidth(animatedWidthFraction)
                        .height(animatedHeight)
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        // Cabecera simulada del panel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(54.dp)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(spec.primary.copy(alpha = 0.55f))
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${(config.width * 100).roundToInt()}% × ${(config.height * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = spec.onSurfaceVariant.copy(alpha = 0.78f),
                                fontSize = 10.sp
                            )
                        }
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(previewColumns),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        userScrollEnabled = false,
                        horizontalArrangement = Arrangement.spacedBy(animatedSpacing),
                        verticalArrangement = Arrangement.spacedBy(animatedSpacing)
                    ) {
                        val accents = listOf(spec.primary, spec.secondary, spec.tertiary)
                        repeat(previewColumns * 2) { idx ->
                            item {
                                val tileColor = accents[idx % accents.size]
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((30f * animatedScale).dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(tileColor.copy(alpha = 0.24f))
                                            .border(
                                                0.8.dp,
                                                tileColor.copy(alpha = 0.45f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = tileColor,
                                            modifier = Modifier.size((15f * animatedScale).dp)
                                        )
                                    }
                                    if (previewLabels) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "App ${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = spec.onSurface,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        thickness = 0.5.dp,
                        color = spec.outlineVariant.copy(alpha = 0.28f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previewBrush = remember(spec) { spec.actionIconBrush }
                        listOf(
                            Icons.Filled.PlayArrow,
                            Icons.Filled.SkipNext,
                            Icons.Filled.Keyboard,
                            Icons.Filled.Settings
                        ).forEach { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).gradientTint(previewBrush),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SettingsCategoryHub(onSelect: (SettingsSectionFilter) -> Unit) {
    val sections = remember {
        SettingsSectionFilter.entries.filter { it != SettingsSectionFilter.OVERVIEW }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "APARTADOS DE CONFIGURACIÓN",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        sections.forEach { section ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(section) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = section.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EdgeGesturesCard() {
    SettingsSectionCard(
        title = "Gestos personalizables del borde",
        subtitle = "Elige exactamente qué hace cada gesto o abre cualquier app",
        icon = Icons.Filled.Gesture,
        badgeText = "6 gestos",
        badgeActive = true
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            GestureCustomizationSection()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun EdgeLiveAlertsCard() {
    val context = LocalContext.current
    var pulseCopy by remember { mutableStateOf(Prefs.edgePulseOnCopy(context)) }
    var pulseScreenshot by remember { mutableStateOf(Prefs.edgePulseOnScreenshot(context)) }
    var pulseNotification by remember { mutableStateOf(Prefs.edgePulseOnNotification(context)) }
    var notificationPreview by remember { mutableStateOf(Prefs.edgeNotificationDoubleTapPreview(context)) }

    SettingsSectionCard(
        title = "Barra viva (avisos laterales)",
        subtitle = "La barra del borde se ilumina 4 segundos con onda luminosa; tócala para actuar",
        icon = Icons.Filled.Layers,
        badgeText = if (pulseCopy || pulseScreenshot || pulseNotification) "Activa" else "Inactiva",
        badgeActive = pulseCopy || pulseScreenshot || pulseNotification
    ) {
        SwitchRow(
            title = "Animar al copiar texto",
            subtitle = "Pulso lateral con icono de portapapeles; tocar abre acciones rápidas",
            checked = pulseCopy,
            onCheckedChange = {
                pulseCopy = it
                Prefs.setEdgePulseOnCopy(context, it)
            }
        )
        SectionDivider()
        SwitchRow(
            title = "Animar con capturas de pantalla",
            subtitle = "Se ilumina al tomar una captura para verla, compartirla o borrarla",
            checked = pulseScreenshot,
            onCheckedChange = {
                pulseScreenshot = it
                Prefs.setEdgePulseOnScreenshot(context, it)
            }
        )
        SectionDivider()
        SwitchRow(
            title = "Animar con notificaciones",
            subtitle = "Muestra el icono de la app que notifica y abre en ventana flotante al tocar",
            checked = pulseNotification,
            onCheckedChange = {
                pulseNotification = it
                Prefs.setEdgePulseOnNotification(context, it)
            }
        )
        if (pulseNotification) {
            SectionDivider()
            SwitchRow(
                title = "Vista previa al doble toque",
                subtitle = "Al dar doble click en el icono de notificación del borde, despliega el mensaje junto a la barra; toca el icono o ✕ para cerrarla",
                checked = notificationPreview,
                onCheckedChange = {
                    notificationPreview = it
                    Prefs.setEdgeNotificationDoubleTapPreview(context, it)
                }
            )
        }
    }
}

@Composable
private fun DynamicIslandCard() {
    val context = LocalContext.current
    val hostView = LocalView.current
    var enabled by remember { mutableStateOf(Prefs.dynamicIslandEnabled(context)) }
    var idlePill by remember { mutableStateOf(Prefs.dynamicIslandIdlePill(context)) }
    var hideBehindCamera by remember { mutableStateOf(Prefs.dynamicIslandHideBehindCamera(context)) }
    var media by remember { mutableStateOf(Prefs.dynamicIslandMedia(context)) }
    var mediaKeepPaused by remember { mutableStateOf(Prefs.dynamicIslandMediaKeepPaused(context)) }
    var notifications by remember { mutableStateOf(Prefs.dynamicIslandNotifications(context)) }
    var hideLandscape by remember { mutableStateOf(Prefs.dynamicIslandHideLandscape(context)) }
    var previewMode by remember { mutableStateOf(com.stree.nexo.panel.IslandCalibrationMode.OFF) }

    var topOffset by remember { mutableIntStateOf(Prefs.dynamicIslandTopOffset(context)) }
    var xOffset by remember { mutableIntStateOf(Prefs.dynamicIslandHorizontalOffset(context)) }
    var cameraGap by remember { mutableIntStateOf(Prefs.dynamicIslandCameraGap(context)) }
    var idleWidth by remember { mutableIntStateOf(Prefs.dynamicIslandIdleWidth(context)) }
    var idleHeight by remember { mutableIntStateOf(Prefs.dynamicIslandIdleHeight(context)) }
    var compactHeight by remember { mutableIntStateOf(Prefs.dynamicIslandCompactHeight(context)) }
    var expandedWidth by remember { mutableIntStateOf(Prefs.dynamicIslandExpandedWidth(context)) }
    var expandedHeight by remember { mutableIntStateOf(Prefs.dynamicIslandExpandedHeight(context)) }

    DisposableEffect(Unit) {
        onDispose {
            EdgePanelService.setIslandCalibrationMode(com.stree.nexo.panel.IslandCalibrationMode.OFF)
        }
    }

    val themeSpec = remember(Prefs.themeRevision) {
        Prefs.panelStyle(context).visualSpec(light = false)
    }

    fun ensureLivePreview(preferred: com.stree.nexo.panel.IslandCalibrationMode) {
        if (previewMode != preferred) {
            previewMode = preferred
            EdgePanelService.setIslandCalibrationMode(preferred)
        } else {
            EdgePanelService.refreshIslandLive()
        }
    }

    val detectCameraCutout: () -> Unit = {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val screenW = context.resources.displayMetrics.widthPixels
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hostView.rootWindowInsets?.displayCutout
        } else null
        val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cutout?.boundingRects?.firstOrNull { it.top < (120 * density) }
        } else null

        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            val topDp = (rect.top / density).roundToInt().coerceIn(-6, 64)
            val centerOffsetDp = ((rect.centerX() - screenW / 2f) / density).roundToInt().coerceIn(-120, 120)
            val holeWidthDp = (rect.width() / density).roundToInt()
            val holeHeightDp = (rect.height() / density).roundToInt()
            val safeGapDp = (holeWidthDp + 8).coerceIn(18, 72)
            val pillW = holeWidthDp.coerceIn(16, 48)
            val pillH = holeHeightDp.coerceIn(16, 42)

            topOffset = topDp
            xOffset = centerOffsetDp
            cameraGap = safeGapDp
            idleWidth = pillW
            idleHeight = pillH

            Prefs.setDynamicIslandTopOffset(context, topDp)
            Prefs.setDynamicIslandHorizontalOffset(context, centerOffsetDp)
            Prefs.setDynamicIslandCameraGap(context, safeGapDp)
            Prefs.setDynamicIslandIdleWidth(context, pillW)
            Prefs.setDynamicIslandIdleHeight(context, pillH)
            previewMode = com.stree.nexo.panel.IslandCalibrationMode.IDLE
            EdgePanelService.setIslandCalibrationMode(com.stree.nexo.panel.IslandCalibrationMode.IDLE)
            Toast.makeText(context, "Cámara detectada (${holeWidthDp}×${holeHeightDp} dp)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No se detectó recorte de cámara; ajusta manualmente con la vista previa en vivo", Toast.LENGTH_LONG).show()
        }
    }

    SettingsSectionCard(
        title = "Isla Dinámica OLED",
        subtitle = "Oculta tras la cámara en reposo · Reproductor interactivo, notificaciones y ajuste exacto",
        icon = Icons.Filled.Layers,
        badgeText = if (enabled) "Activa" else "Inactiva",
        badgeActive = enabled
    ) {
        // Vista previa OLED mostrando la zona segura de la cámara (izquierda | cámara | derecha)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black,
                modifier = Modifier
                    .width(214.dp)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(themeSpec.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = themeSpec.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1E28))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), CircleShape)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(2.5.dp).height(7.dp).clip(CircleShape).background(themeSpec.primary))
                        Box(Modifier.width(2.5.dp).height(13.dp).clip(CircleShape).background(themeSpec.secondary))
                        Box(Modifier.width(2.5.dp).height(9.dp).clip(CircleShape).background(themeSpec.primary))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = Color.Black,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        SwitchRow(
            title = "Activar Isla Dinámica OLED",
            subtitle = "Funciona sobre todas las apps: música interactiva, silencio, llamadas, carga, Bluetooth y Hub",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                Prefs.setDynamicIslandEnabled(context, it)
                if (!it) {
                    previewMode = com.stree.nexo.panel.IslandCalibrationMode.OFF
                    EdgePanelService.setIslandCalibrationMode(com.stree.nexo.panel.IslandCalibrationMode.OFF)
                }
                EdgePanelService.refresh(context)
            }
        )

        if (enabled) {
            SectionDivider()
            val accActive = com.stree.nexo.panel.SplitScreenService.isRunning()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (accActive) Color(0xFF10B981).copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(
                    0.8.dp,
                    if (accActive) Color(0xFF10B981).copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (accActive) "Capa sobre barra de estado activa"
                            else "Activa Accesibilidad para abrir en todas las apps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (accActive) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (accActive) {
                                "La isla se dibuja por encima de la barra de estado y responde al toque dentro de cualquier app."
                            } else {
                                "En Android 12+, activar Nexo en Accesibilidad permite tocar la cámara incluso dentro de otras apps."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!accActive) {
                        FilledTonalButton(
                            onClick = {
                                Haptics.perform(context)
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Activar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            SwitchRow(
                title = "Acceso táctil en la cámara en reposo",
                subtitle = "Permite tocar la zona de la cámara en reposo para desplegar el Hub rápido",
                checked = idlePill,
                onCheckedChange = {
                    idlePill = it
                    Prefs.setDynamicIslandIdlePill(context, it)
                    EdgePanelService.refreshIslandLive()
                }
            )
            if (idlePill) {
                SwitchRow(
                    title = "Ocultar detrás de la cámara en reposo",
                    subtitle = "No muestra ninguna barra negra extra al estar inactiva, pero tocar la cámara sigue abriendo el Hub",
                    checked = hideBehindCamera,
                    onCheckedChange = {
                        hideBehindCamera = it
                        Prefs.setDynamicIslandHideBehindCamera(context, it)
                        EdgePanelService.refreshIslandLive()
                    }
                )
            }
            SwitchRow(
                title = "Notificaciones interactivas",
                subtitle = "Toque corto abre en ventana flotante; mantener pulsado o deslizar abajo lee y muestra acciones",
                checked = notifications,
                onCheckedChange = {
                    notifications = it
                    Prefs.setDynamicIslandNotifications(context, it)
                    EdgePanelService.refreshIslandLive()
                }
            )
            SwitchRow(
                title = "Reproductor de música interactivo",
                subtitle = "Barra táctil para adelantar/retroceder, color dinámico según carátula, -10s/+10s, deslizar para cambiar y botón flotante",
                checked = media,
                onCheckedChange = {
                    media = it
                    Prefs.setDynamicIslandMedia(context, it)
                    EdgePanelService.refreshIslandLive()
                }
            )
            if (media) {
                SwitchRow(
                    title = "Mantener reproductor al pausar",
                    subtitle = "Conserva la cápsula de música accesible un momento tras pausar en lugar de ocultarla de inmediato",
                    checked = mediaKeepPaused,
                    onCheckedChange = {
                        mediaKeepPaused = it
                        Prefs.setDynamicIslandMediaKeepPaused(context, it)
                    }
                )
            }
            SwitchRow(
                title = "Ocultar automáticamente en horizontal",
                subtitle = "Desaparece al ver videos o jugar en pantalla horizontal",
                checked = hideLandscape,
                onCheckedChange = {
                    hideLandscape = it
                    Prefs.setDynamicIslandHideLandscape(context, it)
                    EdgePanelService.refreshIslandLive()
                }
            )

            SectionDivider()

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "VISTA PREVIA EN VIVO SOBRE TU CÁMARA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        com.stree.nexo.panel.IslandCalibrationMode.OFF to "Off",
                        com.stree.nexo.panel.IslandCalibrationMode.IDLE to "Reposo",
                        com.stree.nexo.panel.IslandCalibrationMode.COMPACT to "Compacta",
                        com.stree.nexo.panel.IslandCalibrationMode.EXPANDED to "Expandida"
                    )
                    modes.forEach { (m, label) ->
                        val selected = previewMode == m
                        Surface(
                            onClick = {
                                Haptics.perform(context)
                                previewMode = m
                                EdgePanelService.setIslandCalibrationMode(m)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = detectCameraCutout,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Detectar cámara", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { EdgePanelService.triggerIslandTestAlert(context) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Probar aviso", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "POSICIÓN Y TAMAÑO EXACTO (EN TIEMPO REAL)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.6.sp
                    )
                    TextButton(
                        onClick = {
                            Haptics.perform(context)
                            topOffset = 8
                            xOffset = 0
                            cameraGap = 32
                            idleWidth = 26
                            idleHeight = 24
                            compactHeight = 36
                            expandedWidth = 336
                            expandedHeight = 138
                            Prefs.setDynamicIslandTopOffset(context, 8)
                            Prefs.setDynamicIslandHorizontalOffset(context, 0)
                            Prefs.setDynamicIslandCameraGap(context, 32)
                            Prefs.setDynamicIslandIdleWidth(context, 26)
                            Prefs.setDynamicIslandIdleHeight(context, 24)
                            Prefs.setDynamicIslandCompactHeight(context, 36)
                            Prefs.setDynamicIslandExpandedWidth(context, 336)
                            Prefs.setDynamicIslandExpandedHeight(context, 138)
                            EdgePanelService.refreshIslandLive()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Restablecer", style = MaterialTheme.typography.labelSmall)
                    }
                }

                FineTuneSlider(
                    label = "Posición vertical (Y)",
                    value = topOffset,
                    min = -6,
                    max = 64,
                    step = 1
                ) { v ->
                    topOffset = v
                    Prefs.setDynamicIslandTopOffset(context, v)
                    ensureLivePreview(if (previewMode == com.stree.nexo.panel.IslandCalibrationMode.OFF) com.stree.nexo.panel.IslandCalibrationMode.IDLE else previewMode)
                }

                FineTuneSlider(
                    label = "Posición horizontal (X)",
                    value = xOffset,
                    min = -120,
                    max = 120,
                    step = 1
                ) { v ->
                    xOffset = v
                    Prefs.setDynamicIslandHorizontalOffset(context, v)
                    ensureLivePreview(if (previewMode == com.stree.nexo.panel.IslandCalibrationMode.OFF) com.stree.nexo.panel.IslandCalibrationMode.IDLE else previewMode)
                }

                FineTuneSlider(
                    label = "Espacio central para la cámara",
                    value = cameraGap,
                    min = 0,
                    max = 72,
                    step = 1
                ) { v ->
                    cameraGap = v
                    Prefs.setDynamicIslandCameraGap(context, v)
                    ensureLivePreview(com.stree.nexo.panel.IslandCalibrationMode.COMPACT)
                }

                FineTuneSlider(
                    label = "Ancho en reposo",
                    value = idleWidth,
                    min = 16,
                    max = 180,
                    step = 1
                ) { v ->
                    idleWidth = v
                    Prefs.setDynamicIslandIdleWidth(context, v)
                    ensureLivePreview(com.stree.nexo.panel.IslandCalibrationMode.IDLE)
                }

                FineTuneSlider(
                    label = "Alto en reposo",
                    value = idleHeight,
                    min = 16,
                    max = 48,
                    step = 1
                ) { v ->
                    idleHeight = v
                    Prefs.setDynamicIslandIdleHeight(context, v)
                    ensureLivePreview(com.stree.nexo.panel.IslandCalibrationMode.IDLE)
                }

                FineTuneSlider(
                    label = "Alto compacto activo",
                    value = compactHeight,
                    min = 26,
                    max = 52,
                    step = 1
                ) { v ->
                    compactHeight = v
                    Prefs.setDynamicIslandCompactHeight(context, v)
                    ensureLivePreview(com.stree.nexo.panel.IslandCalibrationMode.COMPACT)
                }

                FineTuneSlider(
                    label = "Ancho expandido (reproductor / Hub)",
                    value = expandedWidth,
                    min = 250,
                    max = 390,
                    step = 2
                ) { v ->
                    expandedWidth = v
                    Prefs.setDynamicIslandExpandedWidth(context, v)
                    ensureLivePreview(com.stree.nexo.panel.IslandCalibrationMode.EXPANDED)
                }

                FineTuneSlider(
                    label = "Alto expandido (reproductor / Hub)",
                    value = expandedHeight,
                    min = 112,
                    max = 210,
                    step = 2
                ) { v ->
                    expandedHeight = v
                    Prefs.setDynamicIslandExpandedHeight(context, v)
                    ensureLivePreview(com.stree.nexo.panel.IslandCalibrationMode.EXPANDED)
                }
            }
        }
    }
}

@Composable
private fun FineTuneSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = {
                        val next = (value - step).coerceIn(min, max)
                        if (next != value) {
                            Haptics.perform(context)
                            onChange(next)
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("−", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "$value dp",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(46.dp)
                )
                Surface(
                    onClick = {
                        val next = (value + step).coerceIn(min, max)
                        if (next != value) {
                            Haptics.perform(context)
                            onChange(next)
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { raw ->
                val snapped = (((raw - min) / step).roundToInt() * step + min).coerceIn(min, max)
                if (snapped != value) onChange(snapped)
            },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
    }
}

@Composable
private fun ActivationZoneCard(side: PanelSide) {
    val context = LocalContext.current
    var width by remember { mutableFloatStateOf(Prefs.activationWidth(context).toFloat()) }
    var height by remember { mutableFloatStateOf(Prefs.activationHeight(context).toFloat()) }
    var position by remember { mutableFloatStateOf(Prefs.panelYFraction(context)) }
    var inset by remember { mutableFloatStateOf(Prefs.activationInset(context).toFloat()) }
    var indicatorWidth by remember { mutableFloatStateOf(Prefs.indicatorWidth(context).toFloat()) }
    var indicatorHeight by remember { mutableFloatStateOf(Prefs.indicatorHeight(context).toFloat()) }
    var showArea by remember { mutableStateOf(Prefs.showActivationArea(context)) }

    var activatorStyle by remember { mutableStateOf(Prefs.activatorStyle(context)) }
    var activatorColor by remember { mutableIntStateOf(Prefs.activatorColor(context)) }
    var activatorOpacity by remember { mutableFloatStateOf(Prefs.activatorOpacity(context)) }
    var syncTheme by remember { mutableStateOf(Prefs.activatorSyncTheme(context)) }

    val themeSpec = remember(Prefs.themeRevision) {
        Prefs.panelStyle(context).visualSpec(light = Prefs.panelLightBackground(context))
    }
    val previewColor = if (syncTheme) themeSpec.primary else Color(activatorColor)

    val screenHeightDp = context.resources.displayMetrics.run { heightPixels / density }.coerceAtLeast(640f)
    val screenWidthDp = context.resources.displayMetrics.run { widthPixels / density }.coerceAtLeast(360f)

    val saveZone = {
        Prefs.setActivationSize(context, width.roundToInt(), height.roundToInt())
        Prefs.setActivationInset(context, inset.roundToInt())
        Prefs.setPanelYFraction(context, position)
        Prefs.setIndicator(context, indicatorWidth.roundToInt(), indicatorHeight.roundToInt(), showArea)
        EdgePanelService.refresh(context)
    }

    fun applyPreset(w: Float, h: Float, gap: Float, indW: Float, indH: Float) {
        width = w
        height = h
        inset = gap
        indicatorWidth = indW
        indicatorHeight = indH
        saveZone()
    }

    SettingsSectionCard(
        title = "Tirador y área táctil del borde",
        subtitle = "Aspecto del indicador, color, tamaño y separación del gesto Atrás",
        icon = Icons.Filled.TouchApp,
        badgeText = "${width.roundToInt()}×${height.roundToInt()} dp",
        badgeActive = true
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            // Simulador interactivo de doble vista: Silueta del teléfono + Lupa de detalle del borde
            TouchAreaVisualizer(
                side = side,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                zoneWidthDp = width,
                zoneHeightDp = height,
                insetDp = inset,
                positionFraction = position,
                indicatorWidthDp = indicatorWidth,
                indicatorHeightDp = indicatorHeight,
                activatorStyle = activatorStyle,
                activatorColor = previewColor,
                activatorOpacity = activatorOpacity,
                onDragPosition = { newPos ->
                    position = newPos.coerceIn(-1f, 1f)
                },
                onDragFinished = saveZone
            )

            Spacer(Modifier.height(12.dp))

            // Perfiles rápidos de calibración
            Text(
                text = "PERFILES RÁPIDOS DE CALIBRACIÓN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip("Equilibrado") { applyPreset(24f, 160f, 16f, 6f, 64f) }
                PresetChip("Gesto Atrás libre") { applyPreset(20f, 136f, 28f, 5f, 56f) }
                PresetChip("Compacto") { applyPreset(16f, 96f, 10f, 4f, 42f) }
                PresetChip("Amplio") { applyPreset(36f, 196f, 8f, 8f, 84f) }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))

            // 1. Estilo del indicador visual
            Text(
                text = "1 · ESTILO Y COLOR DEL INDICADOR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActivatorStyle.entries.forEach { option ->
                    val selected = activatorStyle == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activatorStyle = option
                                Prefs.setActivatorStyle(context, option)
                                EdgePanelService.refresh(context)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        },
                        border = if (selected) null else BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }
        }

        SwitchRow(
            title = "Sincronizar color con el tema activo",
            subtitle = "La barra adopta automáticamente el degradado del tema elegido en Nexo",
            checked = syncTheme,
            onCheckedChange = {
                syncTheme = it
                Prefs.setActivatorSyncTheme(context, it)
                EdgePanelService.refresh(context)
            }
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            // Paleta de color manual del tirador (tocar uno cambia a color manual)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (syncTheme) 0.52f else 1f)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    0xFF8B80FF,
                    0xFF22D3EE,
                    0xFF21BFA0,
                    0xFF319BDD,
                    0xFFE4A139,
                    0xFFFB7185,
                    0xFFEEEEEE,
                    0xFF333333
                ).forEach { value ->
                    val colorInt = value.toInt()
                    val selected = !syncTheme && activatorColor == colorInt
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                syncTheme = false
                                activatorColor = colorInt
                                Prefs.setActivatorSyncTheme(context, false)
                                Prefs.setActivatorColor(context, colorInt)
                                EdgePanelService.refresh(context)
                            },
                        shape = CircleShape,
                        color = Color(colorInt),
                        border = BorderStroke(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        if (selected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (value == 0xFFEEEEEE) Color.Black else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            LabeledSlider(
                label = "Opacidad del indicador",
                valueText = "${(activatorOpacity * 100).roundToInt()} %",
                value = activatorOpacity,
                valueRange = 0.25f..1f,
                onValueChange = { activatorOpacity = it },
                onValueChangeFinished = {
                    Prefs.setActivatorOpacity(context, activatorOpacity)
                    EdgePanelService.refresh(context)
                }
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))

            // 2. Dimensiones del Área Táctil
            Text(
                text = "2 · ZONA TÁCTIL (ÁREA DE ACCIÓN)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
        }

        SwitchRow(
            title = "Resaltar área táctil en pantalla real",
            subtitle = "Pinta en azul translúcido la ventana real del borde para verificar su posición exacta",
            checked = showArea,
            onCheckedChange = {
                showArea = it
                saveZone()
            }
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SteppedSlider(
                label = "Ancho táctil",
                valueText = "${width.roundToInt()} dp",
                value = width,
                valueRange = 12f..48f,
                stepAmount = 2f,
                onValueChange = { width = it },
                onValueChangeFinished = saveZone
            )

            SteppedSlider(
                label = "Alto táctil",
                valueText = "${height.roundToInt()} dp",
                value = height,
                valueRange = 48f..200f,
                stepAmount = 4f,
                onValueChange = { height = it },
                onValueChangeFinished = saveZone
            )

            SteppedSlider(
                label = "Separación del borde (Gesto Atrás)",
                valueText = "${inset.roundToInt()} dp",
                value = inset,
                valueRange = 0f..64f,
                stepAmount = 2f,
                onValueChange = { inset = it },
                onValueChangeFinished = saveZone
            )

            SteppedSlider(
                label = "Posición vertical",
                valueText = "${((position + 1f) * 50f).roundToInt()} %",
                value = position,
                valueRange = -1f..1f,
                stepAmount = 0.05f,
                onValueChange = { position = it },
                onValueChangeFinished = saveZone
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))

            // 3. Dimensiones del Indicador Visible
            Text(
                text = "3 · TAMAÑO DEL INDICADOR VISIBLE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "La marca visible es independiente del área táctil y se adapta dentro de sus límites.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            SteppedSlider(
                label = "Grosor del indicador",
                valueText = "${indicatorWidth.roundToInt()} dp",
                value = indicatorWidth,
                valueRange = 3f..24f,
                stepAmount = 1f,
                onValueChange = { indicatorWidth = it },
                onValueChangeFinished = saveZone
            )

            SteppedSlider(
                label = "Largo del indicador",
                valueText = "${indicatorHeight.roundToInt()} dp",
                value = indicatorHeight,
                valueRange = 16f..120f,
                stepAmount = 4f,
                onValueChange = { indicatorHeight = it },
                onValueChangeFinished = saveZone
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Visualizador profesional en dos paneles:
 * - Izquierda: Silueta vertical del teléfono (arrastrable para mover la altura).
 * - Derecha: Lupa ×4 del borde activo mostrando separación, área táctil e indicador real.
 */
@Composable
private fun TouchAreaVisualizer(
    side: PanelSide,
    screenWidthDp: Float,
    screenHeightDp: Float,
    zoneWidthDp: Float,
    zoneHeightDp: Float,
    insetDp: Float,
    positionFraction: Float,
    indicatorWidthDp: Float,
    indicatorHeightDp: Float,
    activatorStyle: ActivatorStyle,
    activatorColor: Color,
    activatorOpacity: Float,
    onDragPosition: (Float) -> Unit,
    onDragFinished: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val touchAreaColor = Color(0xFF38BDF8)
    val insetColor = Color(0xFFFBBF24)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Panel izquierdo: Silueta interactiva del teléfono a escala real
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Teléfono (arrastra)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        val delta = (dragAmount / (size.height * 0.38f))
                                        onDragPosition(positionFraction + delta)
                                    },
                                    onDragEnd = onDragFinished
                                )
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val pad = 6.dp.toPx()
                            val phoneW = size.width - pad * 2
                            val phoneH = size.height - pad * 2
                            val phoneLeft = pad
                            val phoneTop = pad
                            val corner = 18.dp.toPx()

                            // Fondo de pantalla del teléfono simulado
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.16f),
                                        surfaceColor,
                                        primaryColor.copy(alpha = 0.08f)
                                    )
                                ),
                                topLeft = Offset(phoneLeft, phoneTop),
                                size = Size(phoneW, phoneH),
                                cornerRadius = CornerRadius(corner, corner)
                            )

                            // Cuadrícula sutil de iconos de fondo
                            val cols = 4
                            val rows = 5
                            val cellW = phoneW / (cols + 1)
                            val cellH = (phoneH * 0.68f) / (rows + 1)
                            for (r in 1..rows) {
                                for (c in 1..cols) {
                                    drawRoundRect(
                                        color = onSurfaceColor.copy(alpha = 0.07f),
                                        topLeft = Offset(
                                            phoneLeft + c * cellW - 4.dp.toPx(),
                                            phoneTop + 18.dp.toPx() + r * cellH - 4.dp.toPx()
                                        ),
                                        size = Size(8.dp.toPx(), 8.dp.toPx()),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }

                            // Cámara / altavoz superior y barra inferior de gestos
                            drawRoundRect(
                                color = onSurfaceColor.copy(alpha = 0.25f),
                                topLeft = Offset(phoneLeft + phoneW * 0.36f, phoneTop + 6.dp.toPx()),
                                size = Size(phoneW * 0.28f, 3.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                            drawRoundRect(
                                color = onSurfaceColor.copy(alpha = 0.28f),
                                topLeft = Offset(phoneLeft + phoneW * 0.32f, phoneTop + phoneH - 7.dp.toPx()),
                                size = Size(phoneW * 0.36f, 2.5.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )

                            // Cálculo proporcional de la zona táctil en la silueta
                            val scaledZoneW = ((zoneWidthDp / screenWidthDp) * phoneW)
                                .coerceIn(8.dp.toPx(), phoneW * 0.42f)
                            val scaledZoneH = ((zoneHeightDp / screenHeightDp) * phoneH)
                                .coerceIn(18.dp.toPx(), phoneH * 0.85f)
                            val scaledInset = ((insetDp / screenWidthDp) * phoneW)
                                .coerceAtMost(phoneW * 0.25f)

                            val maxTravel = (phoneH - scaledZoneH - 16.dp.toPx()).coerceAtLeast(0f) / 2f
                            val centerY = phoneTop + phoneH / 2f + positionFraction * maxTravel
                            val zoneTop = (centerY - scaledZoneH / 2f)
                                .coerceIn(phoneTop + 8.dp.toPx(), phoneTop + phoneH - scaledZoneH - 8.dp.toPx())
                            val zoneLeft = if (side == PanelSide.START) {
                                phoneLeft + scaledInset
                            } else {
                                phoneLeft + phoneW - scaledInset - scaledZoneW
                            }

                            // Margen de separación del borde (si inset > 0)
                            if (insetDp > 0f) {
                                val gapLeft = if (side == PanelSide.START) phoneLeft else phoneLeft + phoneW - scaledInset
                                drawRect(
                                    color = insetColor.copy(alpha = 0.28f),
                                    topLeft = Offset(gapLeft, zoneTop),
                                    size = Size(scaledInset, scaledZoneH)
                                )
                            }

                            // Área táctil azul translúcida
                            drawRoundRect(
                                color = touchAreaColor.copy(alpha = 0.32f),
                                topLeft = Offset(zoneLeft, zoneTop),
                                size = Size(scaledZoneW, scaledZoneH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawRoundRect(
                                color = touchAreaColor.copy(alpha = 0.85f),
                                topLeft = Offset(zoneLeft, zoneTop),
                                size = Size(scaledZoneW, scaledZoneH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(width = 1.2.dp.toPx())
                            )

                            // Indicador visible dentro de la silueta
                            val compact = activatorStyle == ActivatorStyle.CIRCLE || activatorStyle == ActivatorStyle.ICON
                            val effIndW = if (compact) 24f.coerceAtMost(zoneWidthDp) else indicatorWidthDp.coerceAtMost(zoneWidthDp)
                            val effIndH = if (compact) effIndW.coerceAtMost(zoneHeightDp) else indicatorHeightDp.coerceAtMost(zoneHeightDp)
                            val scaledIndW = (effIndW / zoneWidthDp * scaledZoneW).coerceAtLeast(3.dp.toPx())
                            val scaledIndH = (effIndH / zoneHeightDp * scaledZoneH).coerceAtLeast(6.dp.toPx())
                            val indLeft = if (side == PanelSide.START) zoneLeft else zoneLeft + scaledZoneW - scaledIndW
                            val indTop = zoneTop + (scaledZoneH - scaledIndH) / 2f

                            drawRoundRect(
                                color = activatorColor.copy(alpha = activatorOpacity),
                                topLeft = Offset(indLeft, indTop),
                                size = Size(scaledIndW, scaledIndH),
                                cornerRadius = CornerRadius(scaledIndW / 2f, scaledIndW / 2f)
                            )

                            // Marco exterior del teléfono
                            drawRoundRect(
                                color = outlineColor.copy(alpha = 0.7f),
                                topLeft = Offset(phoneLeft, phoneTop),
                                size = Size(phoneW, phoneH),
                                cornerRadius = CornerRadius(corner, corner),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                // Panel derecho: Lupa ×4 del borde activo + Leyenda técnica
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Zoom del borde (${if (side == PanelSide.START) "Izquierdo" else "Derecho"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = activatorStyle.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            fontSize = 10.sp
                        )
                    }

                    // Lienzo de zoom del borde mostrando separación, área táctil y el tirador con sus puntos/icono
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
                            .border(
                                1.dp,
                                outlineColor.copy(alpha = 0.3f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val padV = 12.dp.toPx()
                            val padH = 14.dp.toPx()
                            val availW = size.width - padH * 2
                            val availH = size.height - padV * 2

                            // Escala horizontal: 112 dp caben en todo el ancho del zoom
                            val scaleX = availW / 112f
                            // Escala vertical: 200 dp caben en el alto del zoom
                            val scaleY = availH / 205f

                            val edgeX = if (side == PanelSide.START) padH else size.width - padH
                            val dir = if (side == PanelSide.START) 1f else -1f

                            // Línea del borde físico de la pantalla
                            drawLine(
                                color = onSurfaceColor.copy(alpha = 0.55f),
                                start = Offset(edgeX, padV * 0.5f),
                                end = Offset(edgeX, size.height - padV * 0.5f),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            val gapW = insetDp * scaleX
                            val zoneW = (zoneWidthDp * scaleX).coerceAtLeast(10.dp.toPx())
                            val zoneH = (zoneHeightDp * scaleY).coerceAtLeast(24.dp.toPx())
                            val zoneTop = (size.height - zoneH) / 2f
                            val zoneStartX = if (side == PanelSide.START) {
                                edgeX + gapW
                            } else {
                                edgeX - gapW - zoneW
                            }

                            // 1. Dibujo del margen de separación para el gesto Atrás
                            if (insetDp > 0f) {
                                val gapLeft = if (side == PanelSide.START) edgeX else edgeX - gapW
                                drawRect(
                                    color = insetColor.copy(alpha = 0.22f),
                                    topLeft = Offset(gapLeft, zoneTop),
                                    size = Size(gapW, zoneH)
                                )
                                drawRect(
                                    color = insetColor.copy(alpha = 0.75f),
                                    topLeft = Offset(gapLeft, zoneTop),
                                    size = Size(gapW, zoneH),
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                    )
                                )
                            }

                            // 2. Dibujo del Área Táctil (Azul translúcido con borde punteado)
                            drawRoundRect(
                                color = touchAreaColor.copy(alpha = 0.24f),
                                topLeft = Offset(zoneStartX, zoneTop),
                                size = Size(zoneW, zoneH),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            drawRoundRect(
                                color = touchAreaColor.copy(alpha = 0.9f),
                                topLeft = Offset(zoneStartX, zoneTop),
                                size = Size(zoneW, zoneH),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                                )
                            )

                            // 3. Dibujo fiel del Indicador Visual (igual que EdgeHandleView)
                            val compact = activatorStyle == ActivatorStyle.CIRCLE || activatorStyle == ActivatorStyle.ICON
                            val effIndW = if (compact) 24f.coerceAtMost(zoneWidthDp) else indicatorWidthDp.coerceAtMost(zoneWidthDp)
                            val effIndH = if (compact) effIndW.coerceAtMost(zoneHeightDp) else indicatorHeightDp.coerceAtMost(zoneHeightDp)

                            val pillW = (effIndW * scaleX).coerceAtLeast(5.dp.toPx())
                            val pillH = if (compact) {
                                pillW.coerceAtMost(zoneH)
                            } else {
                                (effIndH * scaleY).coerceAtLeast(12.dp.toPx()).coerceAtMost(zoneH)
                            }

                            val pillLeft = if (side == PanelSide.START) {
                                zoneStartX
                            } else {
                                zoneStartX + zoneW - pillW
                            }
                            val pillTop = zoneTop + (zoneH - pillH) / 2f

                            drawRoundRect(
                                color = activatorColor.copy(alpha = activatorOpacity),
                                topLeft = Offset(pillLeft, pillTop),
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(pillW / 2f, pillW / 2f)
                            )

                            val cx = pillLeft + pillW / 2f
                            val cy = pillTop + pillH / 2f

                            if (activatorStyle == ActivatorStyle.PILL) {
                                val dotGap = (pillH / 6.5f).coerceAtMost(8.dp.toPx())
                                val dotRad = (pillW * 0.13f).coerceAtLeast(1.5.dp.toPx())
                                for (i in -1..1) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = activatorOpacity),
                                        radius = dotRad,
                                        center = Offset(cx, cy + i * dotGap)
                                    )
                                }
                            } else if (activatorStyle == ActivatorStyle.ICON) {
                                val chevPath = Path().apply {
                                    moveTo(cx - dir * pillW * 0.14f, cy - pillH * 0.22f)
                                    lineTo(cx + dir * pillW * 0.16f, cy)
                                    lineTo(cx - dir * pillW * 0.14f, cy + pillH * 0.22f)
                                }
                                drawPath(
                                    path = chevPath,
                                    color = Color.White.copy(alpha = activatorOpacity),
                                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Leyenda de colores y medidas en vivo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDot(
                            color = touchAreaColor,
                            label = "Táctil ${zoneWidthDp.roundToInt()}×${zoneHeightDp.roundToInt()}"
                        )
                        LegendDot(
                            color = activatorColor,
                            label = "Marca ${indicatorWidthDp.roundToInt()}×${indicatorHeightDp.roundToInt()}"
                        )
                        LegendDot(
                            color = insetColor,
                            label = "Margen ${insetDp.roundToInt()} dp"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private fun gestureIcon(gesture: EdgeGesture): ImageVector = when (gesture) {
    EdgeGesture.SINGLE_TAP -> Icons.Filled.TouchApp
    EdgeGesture.DOUBLE_TAP -> Icons.Filled.Tune
    EdgeGesture.SWIPE_UP -> Icons.Filled.KeyboardArrowUp
    EdgeGesture.SWIPE_DOWN -> Icons.Filled.KeyboardArrowDown
    EdgeGesture.SWIPE_IN -> Icons.Filled.Layers
    EdgeGesture.PULL_HOLD -> Icons.Filled.Gesture
}

@Composable
private fun GestureCustomizationSection() {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var editingGesture by remember { mutableStateOf<EdgeGesture?>(null) }
    var pickingAppFor by remember { mutableStateOf<EdgeGesture?>(null) }

    val appLabels = remember(revision) {
        val pm = context.packageManager
        EdgeGesture.entries.associateWith { gesture ->
            val pkg = Prefs.edgeGestureApp(context, gesture)
            if (pkg.isNullOrBlank()) null
            else runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "ACCIÓN POR GESTO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp
        )
        TextButton(
            onClick = {
                EdgeGesture.entries.forEach { gesture ->
                    Prefs.setEdgeGestureAction(context, gesture, gesture.defaultAction)
                    Prefs.setEdgeGestureApp(context, gesture, null)
                }
                revision++
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Restablecer",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    Text(
        text = "Toca cualquier gesto para elegir qué acción ejecuta en Nexo o abrir una app directa.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EdgeGesture.entries.forEach { gesture ->
            val action = remember(revision) { Prefs.edgeGestureAction(context, gesture) }
            val actionDisplay = if (action == EdgeGestureAction.LAUNCH_APP) {
                val appName = appLabels[gesture]
                if (appName != null) "Abrir $appName" else "Elegir app…"
            } else {
                action.label
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingGesture = gesture },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                border = BorderStroke(
                    0.8.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = gestureIcon(gesture),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = gesture.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = gesture.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (action == EdgeGestureAction.NONE) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        }
                    ) {
                        Text(
                            text = actionDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (action == EdgeGestureAction.NONE) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    editingGesture?.let { gesture ->
        val currentAction = remember(gesture, revision) { Prefs.edgeGestureAction(context, gesture) }
        AlertDialog(
            onDismissRequest = { editingGesture = null },
            title = {
                Text(
                    text = gesture.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(EdgeGestureAction.entries) { option ->
                        val selected = option == currentAction
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (option == EdgeGestureAction.LAUNCH_APP) {
                                        editingGesture = null
                                        pickingAppFor = gesture
                                    } else {
                                        Prefs.setEdgeGestureAction(context, gesture, option)
                                        revision++
                                        editingGesture = null
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingGesture = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    pickingAppFor?.let { gesture ->
        EdgeAppPickerDialog(
            gestureLabel = gesture.label,
            selectedPackage = Prefs.edgeGestureApp(context, gesture),
            onDismiss = { pickingAppFor = null },
            onSelect = { pkg ->
                Prefs.setEdgeGestureAction(context, gesture, EdgeGestureAction.LAUNCH_APP)
                Prefs.setEdgeGestureApp(context, gesture, pkg)
                revision++
                pickingAppFor = null
            }
        )
    }
}

@Composable
private fun EdgeAppPickerDialog(
    gestureLabel: String,
    selectedPackage: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    pkg to label
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { (pkg, label) -> label.lowercase().contains(q) || pkg.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "App para $gestureLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar aplicación…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.first }) { (pkg, label) ->
                        val selected = pkg == selectedPackage
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(pkg) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun PanelOrderCard() {
    val context = LocalContext.current
    var pages by remember { mutableStateOf(Prefs.panelPageOrder(context)) }
    var hidden by remember { mutableStateOf(Prefs.hiddenPanelPages(context)) }
    var infiniteScroll by remember { mutableStateOf(Prefs.panelInfiniteScroll(context)) }

    SettingsSectionCard(
        title = "Páginas del panel lateral",
        subtitle = "Elige el orden del carrusel y qué páginas están activas",
        icon = Icons.Filled.ViewCarousel,
        badgeText = "${pages.count { it.name !in hidden }} activas",
        badgeActive = true
    ) {
        SwitchRow(
            title = "Desplazamiento infinito",
            subtitle = "Al llegar al final del carrusel vuelve al inicio sin detenerse",
            checked = infiniteScroll,
            onCheckedChange = {
                infiniteScroll = it
                Prefs.setPanelInfiniteScroll(context, it)
            }
        )

        SectionDivider()

        Text(
            text = "La primera página activa es la que se muestra al abrir el panel. Ocultar una página no borra sus iconos anclados ni sus widgets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            pages.forEachIndexed { index, page ->
                val isVisible = page.name !in hidden
                fun move(to: Int) {
                    val reordered = pages.toMutableList()
                    reordered.add(to, reordered.removeAt(index))
                    pages = reordered
                    Prefs.setPanelPageOrder(context, reordered)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isVisible) 0.68f else 0.32f),
                    border = BorderStroke(
                        1.dp,
                        if (index == 0 && isVisible) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isVisible) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = pageIcon(page),
                                contentDescription = null,
                                tint = if (isVisible) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = page.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVisible) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (index == 0 && isVisible) {
                                    Spacer(Modifier.width(6.dp))
                                    StatusBadge("INICIAL", active = true)
                                }
                            }
                            Text(
                                text = "Posición #${index + 1} · ${if (isVisible) "Visible" else "Oculta"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { move(index - 1) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Subir ${page.label}",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            enabled = index < pages.lastIndex,
                            onClick = { move(index + 1) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Bajar ${page.label}",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = isVisible,
                            enabled = !isVisible || pages.count { it.name !in hidden } > 1,
                            onCheckedChange = { visible ->
                                Prefs.setPanelPageVisible(context, page, visible)
                                hidden = Prefs.hiddenPanelPages(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun pageIcon(page: PanelPage): ImageVector = when (page) {
    PanelPage.MAIN -> Icons.Filled.Star
    PanelPage.SETTINGS -> Icons.Filled.Tune
    PanelPage.APPS -> Icons.Filled.Apps
    PanelPage.CONTACTS -> Icons.Filled.Person
    PanelPage.WIDGETS -> Icons.Filled.Widgets
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeActive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (badgeText != null) {
                    StatusBadge(text = badgeText, active = badgeActive)
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean) {
    val bg = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }
    val fg = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ValuePill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            ValuePill(valueText)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun SteppedSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepAmount: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(26.dp)
                    .clickable {
                        onValueChange((value - stepAmount).coerceIn(valueRange))
                        onValueChangeFinished()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Remove, contentDescription = "Reducir", modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            ValuePill(valueText)
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(26.dp)
                    .clickable {
                        onValueChange((value + stepAmount).coerceIn(valueRange))
                        onValueChangeFinished()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Aumentar", modifier = Modifier.size(14.dp))
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    )
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
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
