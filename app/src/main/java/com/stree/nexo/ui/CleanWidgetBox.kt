package com.stree.nexo.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.stree.nexo.WidgetPickerActivity
import com.stree.nexo.data.Haptics
import com.stree.nexo.widget.NexoWidgetHostView
import com.stree.nexo.widget.WidgetStore

/**
 * Contenedor limpio para widgets incrustados (Spotlight y Panel lateral):
 * - En reposo muestra el widget puro sin recuadro, sin barra fija ni esquina de tamaño.
 * - El modo edición se activa explícitamente desde el botón de lápiz (evitando activaciones accidentales)
 *   y muestra borde sutil, puntos arrastrables para redimensionar y barra flotante superior
 *   (Ajustar a bordes / Pantalla completa / Reordenar / Mover pestaña / Reconfigurar / Eliminar / Listo).
 */
@Composable
internal fun CleanWidgetBox(
    itemId: String,
    editing: Boolean,
    accentColor: Color,
    barColor: Color,
    barBorderColor: Color,
    allowHorizontalResize: Boolean,
    minHeightDp: Int = 96,
    maxHeightDp: Int = 720,
    normalHorizontalPaddingDp: Int = 0,
    allowEdgeToEdgeToggle: Boolean = false,
    edgeBleedDp: Int = 0,
    longPressToEdit: Boolean = false,
    onEnterEdit: (() -> Unit)? = null,
    onExitEdit: () -> Unit,
    onExpandFullscreen: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onMoveToTab: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var binding by remember(itemId) { mutableStateOf(WidgetStore.get(context, itemId)) }
    val current = binding ?: return
    val info = remember(current.id) { AppWidgetManager.getInstance(context).getAppWidgetInfo(current.id) }
    var resizing by remember { mutableStateOf(false) }

    val isEdgeToEdge = allowEdgeToEdgeToggle && current.edgeToEdge
    val horizontalPad = if (isEdgeToEdge) 2.dp else normalHorizontalPaddingDp.dp
    val cornerRadius = if (isEdgeToEdge) 12.dp else 18.dp

    if (info == null) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = normalHorizontalPaddingDp.dp),
            shape = RoundedCornerShape(16.dp),
            color = barColor,
            border = BorderStroke(0.8.dp, barBorderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Widget no disponible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Eliminar widget",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isEdgeToEdge && edgeBleedDp > 0) {
                    Modifier.layout { measurable, constraints ->
                        val bleed = edgeBleedDp.dp.roundToPx()
                        val expanded = constraints.maxWidth + bleed * 2
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = expanded, maxWidth = expanded)
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.place(-bleed, 0)
                        }
                    }
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mini barra flotante superior SOLO cuando el modo edición está activo
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(999.dp),
                color = barColor,
                border = BorderStroke(0.9.dp, barBorderColor),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (allowEdgeToEdgeToggle) {
                        Surface(
                            onClick = {
                                Haptics.perform(context)
                                val nextEdge = !current.edgeToEdge
                                val updated = current.copy(edgeToEdge = nextEdge)
                                binding = updated
                                WidgetStore.put(context, itemId, updated)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isEdgeToEdge) accentColor.copy(alpha = 0.24f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FitScreen,
                                    contentDescription = "Ajustar a bordes",
                                    tint = if (isEdgeToEdge) accentColor else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Bordes",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isEdgeToEdge) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isEdgeToEdge) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (onExpandFullscreen != null) {
                        IconButton(
                            onClick = {
                                onExitEdit()
                                onExpandFullscreen()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "Pantalla completa",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onMoveUp != null) {
                        IconButton(
                            onClick = {
                                Haptics.perform(context)
                                onMoveUp()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Subir widget",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(
                            onClick = {
                                Haptics.perform(context)
                                onMoveDown()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Bajar widget",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onMoveToTab != null) {
                        IconButton(
                            onClick = {
                                Haptics.perform(context)
                                onMoveToTab()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = "Mover a otra pestaña",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    if (info.configure != null) {
                        IconButton(
                            onClick = {
                                Haptics.perform(context)
                                onExitEdit()
                                runCatching {
                                    context.startActivity(
                                        WidgetPickerActivity.reconfigureIntent(context, itemId, current.id)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Reconfigurar widget",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            Haptics.perform(context, strong = true)
                            onDelete()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar widget",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            Haptics.perform(context)
                            onExitEdit()
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 2.dp, end = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Listo",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad),
            contentAlignment = Alignment.TopCenter
        ) {
            val maxW = maxWidth.value.toInt().coerceAtLeast(100)
            val widthDp = when {
                isEdgeToEdge -> maxW
                allowHorizontalResize -> current.width.coerceIn(100, maxW)
                else -> maxW
            }
            val heightDp = current.height.coerceIn(minHeightDp, maxHeightDp)

            val editHorizontalPad = if (editing && !isEdgeToEdge) 8.dp else 0.dp
            val editVerticalPad = if (editing) 8.dp else 0.dp
            Box(
                modifier = Modifier
                    .then(
                        if (allowHorizontalResize && !isEdgeToEdge) Modifier.width(widthDp.dp + editHorizontalPad * 2)
                        else Modifier.fillMaxWidth()
                    )
                    .height(heightDp.dp + editVerticalPad * 2)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                            if (allowHorizontalResize && !isEdgeToEdge) Modifier.width(widthDp.dp)
                            else Modifier.fillMaxWidth()
                        )
                        .height(heightDp.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .then(
                            if (editing) Modifier.border(
                                width = 1.8.dp,
                                color = accentColor.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(cornerRadius)
                            ) else Modifier
                        )
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val hostView = WidgetStore.host(ctx).createView(ctx, current.id, info)
                            if (hostView is NexoWidgetHostView) {
                                hostView.onLongPressListener = if (longPressToEdit) onEnterEdit else null
                            }
                            hostView
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            if (view is NexoWidgetHostView) {
                                view.onLongPressListener = if (longPressToEdit) onEnterEdit else null
                                view.interceptAllTouchesForEdit = editing
                            }
                            if (!resizing) {
                                WidgetStore.applyWidgetSize(view, widthDp, heightDp)
                            }
                        }
                    )

                    if (editing && resizing) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text(
                                text = "$widthDp × $heightDp dp",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Puntos arrastrables de redimensionado (solo visibles en modo edición)
                if (editing) {
                    var dragStartW by remember { mutableStateOf(widthDp) }
                    var dragStartH by remember { mutableStateOf(heightDp) }

                    // Punto inferior central (altura)
                    WidgetResizeDot(
                        accentColor = accentColor,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onResizeStart = {
                            dragStartW = if (isEdgeToEdge) maxW else (binding?.width?.coerceIn(100, maxW) ?: widthDp)
                            dragStartH = binding?.height?.coerceIn(minHeightDp, maxHeightDp) ?: heightDp
                            resizing = true
                        },
                        onResizeTotal = { _, totalDyPx ->
                            val nextH = (dragStartH + totalDyPx / density).toInt()
                                .coerceIn(minHeightDp, maxHeightDp)
                            binding = (binding ?: current).copy(
                                width = if (allowHorizontalResize && !isEdgeToEdge) dragStartW else maxW,
                                height = nextH
                            )
                        },
                        onResizeEnd = {
                            resizing = false
                            binding?.let { WidgetStore.put(context, itemId, it) }
                        }
                    )

                    // Punto derecho central (ancho) si el contenedor permite ajuste horizontal y no está bloqueado a bordes
                    if (allowHorizontalResize && !isEdgeToEdge) {
                        WidgetResizeDot(
                            accentColor = accentColor,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onResizeStart = {
                                dragStartW = binding?.width?.coerceIn(100, maxW) ?: widthDp
                                dragStartH = binding?.height?.coerceIn(minHeightDp, maxHeightDp) ?: heightDp
                                resizing = true
                            },
                            onResizeTotal = { totalDxPx, _ ->
                                val nextW = (dragStartW + totalDxPx / density).toInt()
                                    .coerceIn(100, maxW)
                                binding = (binding ?: current).copy(width = nextW, height = dragStartH)
                            },
                            onResizeEnd = {
                                resizing = false
                                binding?.let { WidgetStore.put(context, itemId, it) }
                            }
                        )
                    }

                    // Punto esquina inferior derecha (ancho y alto a la vez, o solo alto en modo borde a borde)
                    if (!isEdgeToEdge) {
                        WidgetResizeDot(
                            accentColor = accentColor,
                            modifier = Modifier.align(Alignment.BottomEnd),
                            onResizeStart = {
                                dragStartW = binding?.width?.coerceIn(100, maxW) ?: widthDp
                                dragStartH = binding?.height?.coerceIn(minHeightDp, maxHeightDp) ?: heightDp
                                resizing = true
                            },
                            onResizeTotal = { totalDxPx, totalDyPx ->
                                val nextW = if (allowHorizontalResize) {
                                    (dragStartW + totalDxPx / density).toInt().coerceIn(100, maxW)
                                } else maxW
                                val nextH = (dragStartH + totalDyPx / density).toInt()
                                    .coerceIn(minHeightDp, maxHeightDp)
                                binding = (binding ?: current).copy(width = nextW, height = nextH)
                            },
                            onResizeEnd = {
                                resizing = false
                                binding?.let { WidgetStore.put(context, itemId, it) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetResizeDot(
    accentColor: Color,
    modifier: Modifier = Modifier,
    onResizeStart: () -> Unit,
    onResizeTotal: (totalDxPx: Float, totalDyPx: Float) -> Unit,
    onResizeEnd: () -> Unit
) {
    val startState by rememberUpdatedState(onResizeStart)
    val totalState by rememberUpdatedState(onResizeTotal)
    val endState by rememberUpdatedState(onResizeEnd)
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(34.dp)
            .onGloballyPositioned { rootPos = it.positionInRoot() }
            .pointerInput(Unit) {
                var startGlobal = Offset.Zero
                detectDragGestures(
                    onDragStart = { local ->
                        startGlobal = rootPos + local
                        startState()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val delta = (rootPos + change.position) - startGlobal
                        totalState(delta.x, delta.y)
                    },
                    onDragEnd = { endState() },
                    onDragCancel = { endState() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.4.dp, accentColor, CircleShape)
        )
    }
}
