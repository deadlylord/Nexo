package com.stree.nexo.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stree.nexo.WidgetPickerActivity
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.theme.visualSpec
import com.stree.nexo.widget.WidgetStore
import java.util.UUID

@Composable
internal fun WidgetsPage(active: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var editMode by remember { mutableStateOf(false) }

    val spec = remember(Prefs.themeRevision) {
        Prefs.panelStyle(context).visualSpec(light = Prefs.panelLightBackground(context))
    }

    DisposableEffect(owner, active) {
        var listening = false
        fun update() {
            if (active && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (!listening) listening = runCatching { WidgetStore.startListening(context); true }.getOrDefault(false)
            } else if (listening) {
                WidgetStore.stopListening(context)
                listening = false
                editMode = false
            }
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

    LaunchedEffect(ids) {
        if (ids.isEmpty() && editMode) editMode = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Widgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (ids.isNotEmpty()) {
                Surface(
                    onClick = {
                        Haptics.perform(context)
                        editMode = !editMode
                    },
                    shape = CircleShape,
                    color = if (editMode) spec.primary.copy(alpha = 0.22f) else Color.Transparent,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = if (editMode) "Terminar edición" else "Editar widgets",
                        tint = if (editMode) spec.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            IconButton(
                onClick = {
                    Haptics.perform(context)
                    editMode = false
                    context.startActivity(
                        Intent(context, WidgetPickerActivity::class.java)
                            .putExtra("item_id", "panel-widget:${System.currentTimeMillis()}:${UUID.randomUUID()}")
                    )
                }
            ) {
                Icon(Icons.Filled.Add, "Añadir widget")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ids.isEmpty()) {
                item {
                    Text(
                        text = "Añade widgets con + y toca el lápiz para redimensionar, reordenar o eliminar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
            itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                CleanWidgetBox(
                    itemId = id,
                    editing = editMode,
                    accentColor = spec.primary,
                    barColor = spec.surface.copy(alpha = 0.94f),
                    barBorderColor = spec.outlineVariant.copy(alpha = 0.45f),
                    allowHorizontalResize = true,
                    minHeightDp = 80,
                    maxHeightDp = 650,
                    longPressToEdit = false,
                    onExitEdit = { editMode = false },
                    onMoveUp = if (index > 0) {
                        {
                            WidgetStore.movePanelWidget(context, id, -1)
                            revision++
                        }
                    } else null,
                    onMoveDown = if (index < ids.lastIndex) {
                        {
                            WidgetStore.movePanelWidget(context, id, 1)
                            revision++
                        }
                    } else null,
                    onDelete = {
                        WidgetStore.remove(context, id)
                        revision++
                    }
                )
            }
        }
    }
}
