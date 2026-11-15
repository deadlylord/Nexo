package com.stree.nexo.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stree.nexo.WidgetPickerActivity
import com.stree.nexo.widget.WidgetStore
import java.util.UUID

@Composable
internal fun WidgetsPage(active: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner, active) {
        var listening = false
        fun update() {
            if (active && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (!listening) listening = runCatching { WidgetStore.startListening(context); true }.getOrDefault(false)
            } else if (listening) { WidgetStore.stopListening(context); listening = false }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
            update()
        }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (listening) WidgetStore.stopListening(context)
        }
    }
    val ids = remember(revision) { WidgetStore.panelItems(context) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                context.startActivity(Intent(context, WidgetPickerActivity::class.java)
                    .putExtra("item_id", "panel-widget:${System.currentTimeMillis()}:${UUID.randomUUID()}"))
            }) { Icon(Icons.Filled.Add, "Añadir widget") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ids.isEmpty()) item {
                Text("Añade widgets con +. Arrastra la esquina inferior derecha para ajustar su tamaño.", style = MaterialTheme.typography.bodySmall)
            }
            items(ids, key = { it }) { id ->
                PanelWidget(id, onDelete = { WidgetStore.remove(context, id); revision++ })
            }
        }
    }
}

@Composable
private fun PanelWidget(itemId: String, onDelete: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var binding by remember(itemId) { mutableStateOf(WidgetStore.get(context, itemId)) }
    val current = binding ?: return
    val info = remember(current.id) { AppWidgetManager.getInstance(context).getAppWidgetInfo(current.id) }
    var resizing by remember { mutableStateOf(false) }
    var cornerPosition by remember { mutableStateOf(Offset.Zero) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(info?.loadLabel(context.packageManager)?.toString() ?: "Widget no disponible",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Eliminar widget", Modifier.size(18.dp)) }
        }
        if (info != null) BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val maximumWidth = maxWidth.value.toInt().coerceAtLeast(80)
            val width = current.width.coerceIn(80, maximumWidth)
            val height = current.height.coerceIn(80, 650)
            Box(Modifier.width(width.dp).height(height.dp).clip(RoundedCornerShape(12.dp))) {
                AndroidView(
                    factory = { WidgetStore.host(it).createView(it, current.id, info) },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (!resizing) {
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.toFloat(), height.toFloat())))
                            } else {
                                @Suppress("DEPRECATION")
                                view.updateAppWidgetSize(null, width, height, width, height)
                            }
                        }
                    }
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).size(32.dp)
                        .onGloballyPositioned { cornerPosition = it.positionInRoot() }.pointerInput(current.id) {
                        var startPointer = Offset.Zero
                        var startWidth = 0; var startHeight = 0
                        detectDragGestures(onDragStart = {
                            startPointer = cornerPosition + it
                            startWidth = binding?.width?.coerceIn(80, maximumWidth) ?: width
                            startHeight = binding?.height?.coerceIn(80, 650) ?: height
                            resizing = true
                        }, onDrag = { change, _ ->
                            change.consume()
                            val movement = cornerPosition + change.position - startPointer
                            binding = binding?.copy(
                                width = (startWidth + movement.x / density).toInt().coerceIn(80, maximumWidth),
                                height = (startHeight + movement.y / density).toInt().coerceIn(80, 650)
                            )
                        }, onDragEnd = {
                            resizing = false
                            binding?.let { WidgetStore.put(context, itemId, it) }
                        }, onDragCancel = {
                            resizing = false
                            binding?.let { WidgetStore.put(context, itemId, it) }
                        })
                    },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .9f), shape = RoundedCornerShape(topStart = 10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("◢", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
    }
}
