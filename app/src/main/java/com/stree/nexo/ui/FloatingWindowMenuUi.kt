package com.stree.nexo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.SearchItem
import com.stree.nexo.floating.FloatingAppEntry
import com.stree.nexo.floating.FloatingDockLayout
import com.stree.nexo.ui.theme.visualSpec

@Composable
fun FloatingWindowMenuContent(
    entries: List<FloatingAppEntry>,
    activeLayout: FloatingDockLayout,
    availableApps: List<SearchItem>,
    onMinimizeApp: (String) -> Unit,
    onMinimizeAll: () -> Unit,
    onFocusApp: (FloatingAppEntry) -> Unit,
    onMaximizeApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onClearAll: () -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onAddApp: (String, FloatingDockLayout?, Boolean) -> Unit,
    onApplyLayout: (FloatingDockLayout) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val style = remember(Prefs.themeRevision) { Prefs.panelStyle(context) }
    val spec = remember(style) { style.visualSpec(light = false) }
    val shape = RoundedCornerShape(28.dp)

    var selectedPkg by remember { mutableStateOf(entries.firstOrNull()?.packageName) }
    var showAppPicker by remember { mutableStateOf(entries.isEmpty()) }
    var pendingLayout by remember { mutableStateOf<FloatingDockLayout?>(null) }
    var pickerQuery by remember { mutableStateOf("") }

    LaunchedEffect(entries) {
        if (selectedPkg == null || entries.none { it.packageName == selectedPkg }) {
            selectedPkg = entries.firstOrNull()?.packageName
        }
    }

    val selectedEntry = entries.firstOrNull { it.packageName == selectedPkg } ?: entries.firstOrNull()
    val trackedPackages = remember(entries) { entries.map { it.packageName }.toSet() }
    val filteredApps = remember(availableApps, pickerQuery, trackedPackages) {
        val q = pickerQuery.trim()
        availableApps
            .filter { it.type == ItemType.APP && !it.packageName.isNullOrBlank() }
            .filter { it.packageName !in trackedPackages }
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
            .take(40)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, spec.borderBrush, shape),
        shape = shape,
        color = spec.surface.copy(alpha = 0.94f),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(spec.sheenBrush)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Encabezado profesional
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(spec.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureInPictureAlt,
                        contentDescription = null,
                        tint = spec.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Ventanas flotantes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = spec.onSurface
                        )
                        if (entries.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = spec.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${entries.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = spec.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Control rápido y acople multi-ventana",
                        style = MaterialTheme.typography.labelSmall,
                        color = spec.onSurfaceVariant
                    )
                }
                if (entries.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            Haptics.perform(context)
                            onMinimizeAll()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Minimize,
                            contentDescription = "Minimizar todas a burbuja",
                            tint = spec.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            Haptics.perform(context, strong = true)
                            onClearAll()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Cerrar burbujas",
                            tint = Color(0xFFFB7185),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar menú",
                        tint = spec.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Carrusel de apps en el espacio flotante + botón "+ Añadir app"
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(entries, key = { _, e -> e.packageName }) { index, entry ->
                    val isSelected = entry.packageName == selectedEntry?.packageName
                    FloatingAppChipCard(
                        entry = entry,
                        slotNumber = index + 1,
                        selected = isSelected,
                        canMoveLeft = index > 0,
                        canMoveRight = index < entries.lastIndex,
                        accent = spec.primary,
                        surfaceColor = spec.surfaceVariant.copy(alpha = if (isSelected) 0.85f else 0.48f),
                        textColor = spec.onSurface,
                        mutedColor = spec.onSurfaceVariant,
                        onSelect = {
                            Haptics.perform(context)
                            selectedPkg = entry.packageName
                        },
                        onMoveLeft = {
                            Haptics.perform(context)
                            onMoveSlot(index, index - 1)
                        },
                        onMoveRight = {
                            Haptics.perform(context)
                            onMoveSlot(index, index + 1)
                        },
                        onRemove = {
                            Haptics.perform(context)
                            onRemoveApp(entry.packageName)
                        }
                    )
                }
                item(key = "add_floating_app") {
                    Surface(
                        onClick = {
                            Haptics.perform(context)
                            pendingLayout = null
                            showAppPicker = !showAppPicker
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (showAppPicker) spec.primary.copy(alpha = 0.24f) else spec.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, spec.primary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .width(76.dp)
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Añadir app",
                                tint = spec.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Añadir",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = spec.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. Acciones rápidas profesionales para la ventana seleccionada
            if (selectedEntry != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WindowActionPill(
                        icon = Icons.Filled.Minimize,
                        label = "Burbuja",
                        tint = spec.primary,
                        textColor = spec.onSurface,
                        bgColor = spec.surfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Haptics.perform(context)
                            onMinimizeApp(selectedEntry.packageName)
                        }
                    )
                    WindowActionPill(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = if (selectedEntry.minimized) "Reabrir" else "Enfocar",
                        tint = Color(0xFF34D399),
                        textColor = spec.onSurface,
                        bgColor = spec.surfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Haptics.perform(context)
                            onFocusApp(selectedEntry)
                        }
                    )
                    WindowActionPill(
                        icon = Icons.Filled.Fullscreen,
                        label = "Maximizar",
                        tint = spec.onSurface,
                        textColor = spec.onSurface,
                        bgColor = spec.surfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Haptics.perform(context)
                            onMaximizeApp(selectedEntry.packageName)
                        }
                    )
                    WindowActionPill(
                        icon = Icons.Filled.Close,
                        label = "Quitar",
                        tint = Color(0xFFFB7185),
                        textColor = spec.onSurface,
                        bgColor = spec.surfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Haptics.perform(context)
                            onRemoveApp(selectedEntry.packageName)
                        }
                    )
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = spec.outlineVariant.copy(alpha = 0.3f))

            // 4. Layouts visuales de acople multi-ventana
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAYOUTS DE ACOPLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = spec.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (entries.size > 1) "Usa ← → para ordenar slots" else "Toca un esquema",
                        style = MaterialTheme.typography.labelSmall,
                        color = spec.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }

                val layouts = FloatingDockLayout.entries
                layouts.chunked(3).forEach { rowLayouts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowLayouts.forEach { layout ->
                            val isCurrent = layout == activeLayout && entries.size >= layout.minApps
                            val missing = (layout.minApps - entries.size).coerceAtLeast(0)
                            DockLayoutTile(
                                layout = layout,
                                selected = isCurrent || pendingLayout == layout,
                                missingCount = missing,
                                accent = spec.primary,
                                cardBg = spec.surfaceVariant.copy(alpha = 0.52f),
                                textColor = spec.onSurface,
                                mutedColor = spec.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    Haptics.perform(context)
                                    if (entries.size >= layout.minApps) {
                                        pendingLayout = null
                                        onApplyLayout(layout)
                                    } else {
                                        pendingLayout = layout
                                        showAppPicker = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 5. Selector rápido inline para "+ Añadir app" o completar slots de un layout
            AnimatedVisibility(visible = showAppPicker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(spec.surfaceVariant.copy(alpha = 0.42f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val targetLayout = pendingLayout
                    val missing = targetLayout?.let { (it.minApps - entries.size).coerceAtLeast(1) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (targetLayout != null && missing != null) {
                                "Elige $missing app${if (missing > 1) "s" else ""} más para ${targetLayout.label}"
                            } else {
                                "Añadir app al espacio flotante"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = spec.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                showAppPicker = false
                                pendingLayout = null
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cerrar selector",
                                tint = spec.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Mini buscador de apps
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = spec.surface.copy(alpha = 0.72f),
                        border = BorderStroke(0.8.dp, spec.outlineVariant.copy(alpha = 0.32f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = spec.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (pickerQuery.isEmpty()) {
                                    Text(
                                        text = "Filtrar aplicaciones…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = spec.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                BasicTextField(
                                    value = pickerQuery,
                                    onValueChange = { pickerQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = spec.onSurface, fontSize = 13.sp),
                                    cursorBrush = SolidColor(spec.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (pickerQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Limpiar",
                                    tint = spec.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { pickerQuery = "" }
                                )
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 210.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.id }) { app ->
                            val pkg = app.packageName ?: return@items
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        Haptics.perform(context)
                                        val nextCount = entries.size + 1
                                        val readyLayout = pendingLayout?.takeIf { nextCount >= it.minApps }
                                        val keepOpen = pendingLayout != null && readyLayout == null
                                        onAddApp(pkg, readyLayout, keepOpen)
                                        pickerQuery = ""
                                        if (!keepOpen) {
                                            pendingLayout = null
                                            showAppPicker = false
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppPackageIcon(packageName = pkg, sizeDp = 38)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = app.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = spec.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
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
private fun FloatingAppChipCard(
    entry: FloatingAppEntry,
    slotNumber: Int,
    selected: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    accent: Color,
    surfaceColor: Color,
    textColor: Color,
    mutedColor: Color,
    onSelect: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = BorderStroke(
            width = if (selected) 1.4.dp else 0.8.dp,
            color = if (selected) accent else mutedColor.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .width(106.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#$slotNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) accent else mutedColor
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Quitar ${entry.label}",
                    tint = mutedColor,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onRemove)
                )
            }
            AppPackageIcon(packageName = entry.packageName, sizeDp = 34)
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected && (canMoveLeft || canMoveRight)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Mover slot izquierda",
                        tint = if (canMoveLeft) accent else mutedColor.copy(alpha = 0.25f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(enabled = canMoveLeft, onClick = onMoveLeft)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Mover slot derecha",
                        tint = if (canMoveRight) accent else mutedColor.copy(alpha = 0.25f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(enabled = canMoveRight, onClick = onMoveRight)
                    )
                }
            } else {
                Text(
                    text = if (entry.minimized) "Burbuja" else "Activa",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (entry.minimized) mutedColor else Color(0xFF34D399)
                )
            }
        }
    }
}

@Composable
private fun WindowActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    textColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DockLayoutTile(
    layout: FloatingDockLayout,
    selected: Boolean,
    missingCount: Int,
    accent: Color,
    cardBg: Color,
    textColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else cardBg,
        border = BorderStroke(
            width = if (selected) 1.3.dp else 0.7.dp,
            color = if (selected) accent else mutedColor.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DockLayoutPreviewIcon(
                layout = layout,
                fillColor = if (selected) accent.copy(alpha = 0.45f) else textColor.copy(alpha = 0.16f),
                strokeColor = if (selected) accent else textColor.copy(alpha = 0.55f),
                modifier = Modifier.size(width = 44.dp, height = 34.dp)
            )
            Text(
                text = layout.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (missingCount > 0) {
                Text(
                    text = "+$missingCount app${if (missingCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = accent.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/** Miniatura vectorial estilo Snap Layouts que muestra los slots de cada esquema. */
@Composable
private fun DockLayoutPreviewIcon(
    layout: FloatingDockLayout,
    fillColor: Color,
    strokeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val gap = 3.dp.toPx()
        val r = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        val stroke = Stroke(width = 1.2.dp.toPx())
        val w = size.width
        val h = size.height

        fun box(x: Float, y: Float, bw: Float, bh: Float) {
            drawRoundRect(color = fillColor, topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = r)
            drawRoundRect(color = strokeColor, topLeft = Offset(x, y), size = Size(bw, bh), cornerRadius = r, style = stroke)
        }

        when (layout) {
            FloatingDockLayout.CENTER -> box(w * 0.14f, h * 0.12f, w * 0.72f, h * 0.76f)
            FloatingDockLayout.SPLIT_VERTICAL -> {
                val halfH = (h - gap) / 2f
                box(0f, 0f, w, halfH)
                box(0f, halfH + gap, w, halfH)
            }
            FloatingDockLayout.SPLIT_HORIZONTAL -> {
                val halfW = (w - gap) / 2f
                box(0f, 0f, halfW, h)
                box(halfW + gap, 0f, halfW, h)
            }
            FloatingDockLayout.MAIN_PLUS_TWO -> {
                val topH = (h - gap) * 0.54f
                val botH = h - topH - gap
                val halfW = (w - gap) / 2f
                box(0f, 0f, w, topH)
                box(0f, topH + gap, halfW, botH)
                box(halfW + gap, topH + gap, halfW, botH)
            }
            FloatingDockLayout.GRID_2X2 -> {
                val halfW = (w - gap) / 2f
                val halfH = (h - gap) / 2f
                box(0f, 0f, halfW, halfH)
                box(halfW + gap, 0f, halfW, halfH)
                box(0f, halfH + gap, halfW, halfH)
                box(halfW + gap, halfH + gap, halfW, halfH)
            }
            FloatingDockLayout.CASCADE -> {
                val bw = w * 0.62f
                val bh = h * 0.56f
                box(0f, 0f, bw, bh)
                box(w * 0.19f, h * 0.22f, bw, bh)
                box(w * 0.38f, h * 0.44f, bw, bh)
            }
        }
    }
}

@Composable
private fun AppPackageIcon(packageName: String, sizeDp: Int) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
        )
    }
}
