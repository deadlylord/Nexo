package com.stree.nexo.ui

import android.net.Uri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.stree.nexo.data.SearchItem

@Composable
fun ShortcutsScreen(
    apps: List<SearchItem>,
    contacts: List<SearchItem>,
    shortcuts: List<SearchItem>,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenHomeSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var showAppDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp)
        ) {
            item {
                Text(
                    text = "Atajos",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enlaces y acciones que abres en un toque",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Atajos de otras apps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Android solo permite leer los atajos internos de otras apps si Nexo es el lanzador predeterminado. Mientras tanto, crea aquí tus propios atajos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenHomeSettings) {
                            Text("Abrir ajustes de inicio")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (shortcuts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Todavía no tienes atajos",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Toca + para agregar un enlace, una búsqueda o una acción.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(shortcuts, key = { it.id }) { shortcut ->
                    ShortcutRow(
                        shortcut = shortcut,
                        onDelete = { onDelete(shortcut.id.removePrefix("custom:")) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
                modifier = Modifier.width(280.dp)
            ) {
                AddMenuItem(Icons.Filled.Apps, "Aplicación") {
                    showAddMenu = false; showAppDialog = true
                }
                AddMenuItem(Icons.Filled.Language, "Enlace o página web") {
                    showAddMenu = false; showDialog = true
                }
                AddMenuItem(Icons.AutoMirrored.Filled.CompareArrows, "Par de aplicaciones") {
                    showAddMenu = false; showPairDialog = true
                }
                AddMenuItem(Icons.Filled.Folder, "Carpeta") {
                    showAddMenu = false; showFolderDialog = true
                }
                AddMenuItem(Icons.Filled.Person, "Contacto para llamar") {
                    showAddMenu = false; showContactDialog = true
                }
                AddMenuItem(Icons.AutoMirrored.Filled.Chat, "WhatsApp a contacto") {
                    showAddMenu = false; showWhatsAppDialog = true
                }
            }
            FloatingActionButton(
                onClick = { showAddMenu = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar atajo")
            }
        }
    }

    if (showDialog) {
        AddShortcutDialog(
            onDismiss = { showDialog = false },
            onConfirm = { label, target ->
                onAdd(label, target)
                showDialog = false
            }
        )
    }
    if (showPairDialog) {
        AddAppPairDialog(
            apps = apps,
            onDismiss = { showPairDialog = false },
            onConfirm = { label, target ->
                onAdd(label, target)
                showPairDialog = false
            }
        )
    }
    if (showWhatsAppDialog) {
        ContactShortcutPicker(
            contacts = contacts,
            whatsapp = true,
            onDismiss = { showWhatsAppDialog = false },
            onAdd = { label, target ->
                onAdd(label, target)
                showWhatsAppDialog = false
            }
        )
    }
    if (showAppDialog) {
        AddAppShortcutDialog(apps, { showAppDialog = false }) { app ->
            val target = Uri.Builder().scheme("nexo-app").authority("open")
                .appendQueryParameter("package", app.packageName).build().toString()
            onAdd(app.title, target)
            showAppDialog = false
        }
    }
    if (showFolderDialog) {
        AddFolderDialog(apps, { showFolderDialog = false }) { label, selected ->
            val builder = Uri.Builder().scheme("nexo-folder").authority("open")
                .appendQueryParameter("name", label)
            selected.forEach { builder.appendQueryParameter("app", it.packageName) }
            onAdd(label, builder.build().toString())
            showFolderDialog = false
        }
    }
    if (showContactDialog) {
        ContactShortcutPicker(contacts, false, { showContactDialog = false }) { label, target ->
            onAdd(label, target)
            showContactDialog = false
        }
    }
}

@Composable
private fun AddMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.titleMedium) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        onClick = onClick
    )
}

@Composable
private fun AddAppShortcutDialog(
    apps: List<SearchItem>,
    onDismiss: () -> Unit,
    onConfirm: (SearchItem) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir aplicación") },
        text = {
            Column {
                OutlinedTextField(filter, { filter = it }, label = { Text("Buscar") }, singleLine = true)
                LazyColumn(Modifier.height(340.dp)) {
                    items(apps.filter { it.title.contains(filter, true) }, key = { it.id }) { app ->
                        TextButton(onClick = { onConfirm(app) }, modifier = Modifier.fillMaxWidth()) {
                            ItemIcon(app, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(app.title, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddContactCallDialog(
    contacts: List<SearchItem>,
    onDismiss: () -> Unit,
    onConfirm: (SearchItem) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contacto para llamar") },
        text = {
            Column {
                OutlinedTextField(filter, { filter = it }, label = { Text("Buscar contacto") }, singleLine = true)
                LazyColumn(Modifier.height(340.dp)) {
                    items(contacts.filter {
                        it.title.contains(filter, true) || it.phoneNumber.orEmpty().contains(filter)
                    }, key = { it.id }) { contact ->
                        TextButton(onClick = { onConfirm(contact) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(contact.title)
                                Text(contact.phoneNumber.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
internal fun AddFolderDialog(
    apps: List<SearchItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<SearchItem>) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val selectedApps = apps.filter { it.packageName in selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva carpeta") },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(filter, { filter = it }, label = { Text("Buscar aplicaciones") }, singleLine = true)
                Text("${selected.size} seleccionadas", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.height(300.dp)) {
                    items(apps.filter { it.title.contains(filter, true) }, key = { it.id }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { checked ->
                                    app.packageName?.let { packageName ->
                                        selected = if (checked) {
                                            selected + packageName
                                        } else {
                                            selected - packageName
                                        }
                                    }
                                }
                            )
                            ItemIcon(app, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(app.title, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && selectedApps.isNotEmpty(),
                onClick = { onConfirm(label.trim(), selectedApps) }
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
internal fun AddAppPairDialog(
    apps: List<SearchItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<SearchItem?>(null) }
    var second by remember { mutableStateOf<SearchItem?>(null) }
    var choosing by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pareja de apps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nexo abre la primera actividad y, un segundo después, abre la segunda con LAUNCH_ADJACENT. No requiere Accesibilidad. Android y ambas aplicaciones deben admitir pantalla dividida; de lo contrario pueden abrirse normalmente a pantalla completa.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nombre (opcional)") }, singleLine = true)
                OutlinedButton(onClick = { filter = ""; choosing = 1 }) {
                    first?.let { ItemIcon(it, 32.dp); Spacer(Modifier.width(8.dp)) }
                    Text(first?.title ?: "Elegir primera app")
                }
                OutlinedButton(onClick = { filter = ""; choosing = 2 }) {
                    second?.let { ItemIcon(it, 32.dp); Spacer(Modifier.width(8.dp)) }
                    Text(second?.title ?: "Elegir segunda app")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = first != null && second != null && first?.packageName != second?.packageName,
                onClick = {
                    val a = first ?: return@TextButton
                    val b = second ?: return@TextButton
                    val target = Uri.Builder().scheme("nexo-pair").authority("open")
                        .appendQueryParameter("first", a.packageName)
                        .appendQueryParameter("firstClass", a.intent?.component?.className)
                        .appendQueryParameter("second", b.packageName)
                        .appendQueryParameter("secondClass", b.intent?.component?.className)
                        .appendQueryParameter("delay", "1000")
                        .build().toString()
                    onConfirm(label.trim().ifBlank { "${a.title} + ${b.title}" }, target)
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (choosing != 0) {
        AlertDialog(
            onDismissRequest = { choosing = 0 },
            title = { Text(if (choosing == 1) "Primera app" else "Segunda app") },
            text = {
                Column {
                    OutlinedTextField(value = filter, onValueChange = { filter = it }, label = { Text("Buscar app") }, singleLine = true)
                    LazyColumn(Modifier.height(280.dp)) {
                        items(apps.filter { it.title.contains(filter, ignoreCase = true) }, key = { it.id }) { app ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = app.packageName != (if (choosing == 1) second?.packageName else first?.packageName),
                                onClick = {
                                    if (choosing == 1) first = app else second = app
                                    choosing = 0
                                }
                            ) {
                                ItemIcon(app, 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(app.title, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = 0 }) { Text("Volver") } }
        )
    }
}

@Composable
private fun ShortcutRow(
    shortcut: SearchItem,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = shortcut.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = shortcut.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar atajo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    val canSave = label.isNotBlank() && target.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo atajo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Mi playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Enlace o acción") },
                    placeholder = { Text("https://…") },
                    supportingText = { Text("También sirve tel:, mailto: o sms:") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim(), target.trim()) },
                enabled = canSave
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
