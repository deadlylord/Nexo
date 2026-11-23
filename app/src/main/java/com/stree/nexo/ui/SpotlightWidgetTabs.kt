package com.stree.nexo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stree.nexo.widget.WidgetTab

private fun WidgetTab.display(): String = if (icon.isBlank()) name else "$icon $name"

/**
 * Pestañas horizontales de widgets en Spotlight. En modo edición, tocar la
 * pestaña activa abre su editor y aparece el botón + para crear pestañas.
 */
@Composable
fun SpotlightWidgetTabsRow(
    tabs: List<WidgetTab>,
    selectedId: String,
    editing: Boolean,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onSelect: (String) -> Unit,
    onEditTab: (WidgetTab) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab.id == selectedId
            Surface(
                onClick = { if (active && editing) onEditTab(tab) else onSelect(tab.id) },
                shape = RoundedCornerShape(999.dp),
                color = if (active) accent.copy(alpha = 0.2f) else Color.Transparent,
                border = BorderStroke(0.8.dp, if (active) accent.copy(alpha = 0.55f) else borderColor)
            ) {
                Text(
                    text = if (active && editing) "${tab.display()}  ✎" else tab.display(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) textColor else mutedColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Surface(
            onClick = onAddTab,
            shape = RoundedCornerShape(999.dp),
            color = Color.Transparent,
            border = BorderStroke(0.8.dp, if (editing) accent.copy(alpha = 0.5f) else borderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (editing) 10.dp else 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Nueva pestaña",
                    tint = if (editing) accent else mutedColor,
                    modifier = Modifier.size(14.dp)
                )
                if (editing) {
                    Text("Pestaña", style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
        }
    }
}

/**
 * Editor de pestaña: nombre, emoji opcional, mover a izquierda/derecha y eliminar.
 * Con [tab] nulo funciona como diálogo de creación.
 */
@Composable
fun WidgetTabEditorDialog(
    tab: WidgetTab?,
    canDelete: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String) -> Unit,
    onMove: (delta: Int) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(tab?.id) { mutableStateOf(tab?.name.orEmpty()) }
    var icon by remember(tab?.id) { mutableStateOf(tab?.icon.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tab == null) "Nueva pestaña" else "Editar pestaña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Nombre (ej. Música, Trabajo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it.take(4) },
                    label = { Text("Emoji opcional (ej. 🎵 💼)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tab != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMove(-1) }, enabled = canMoveLeft) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mover a la izquierda")
                        }
                        IconButton(onClick = { onMove(1) }, enabled = canMoveRight) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mover a la derecha")
                        }
                        Text(
                            "Reordenar",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (canDelete) {
                            TextButton(onClick = onDelete) {
                                Icon(
                                    Icons.Filled.Delete, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(" Eliminar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (canDelete) {
                        Text(
                            "Al eliminarla, sus widgets pasan a la primera pestaña.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, icon) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Selector para mover un widget a otra pestaña. */
@Composable
fun MoveWidgetToTabDialog(
    tabs: List<WidgetTab>,
    currentTabId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover a pestaña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEach { tab ->
                    val current = tab.id == currentTabId
                    Surface(
                        onClick = { if (!current) onPick(tab.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tab.display(), modifier = Modifier.weight(1f))
                            if (current) {
                                Icon(
                                    Icons.Filled.Check, "Pestaña actual",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
