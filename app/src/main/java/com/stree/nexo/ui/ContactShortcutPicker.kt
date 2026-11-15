package com.stree.nexo.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ContactShortcutPicker(
    contacts: List<SearchItem>,
    whatsapp: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    var permissionVersion by remember { mutableIntStateOf(0) }
    var permitted by remember { mutableStateOf(ContactsIndexer(context).hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) { Prefs.setContactsEnabled(context, true); permissionVersion++ }
    }
    val people by produceState(contacts, contacts, permissionVersion) {
        if (permitted && contacts.isEmpty()) {
            value = withContext(Dispatchers.IO) { ContactsIndexer(context).load() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (whatsapp) "WhatsApp a contacto" else "Contacto para llamar") },
        text = {
            Column {
                if (!permitted) {
                    TextButton(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Permitir acceso a contactos")
                    }
                } else {
                    OutlinedTextField(filter, { filter = it }, label = { Text("Buscar contacto") }, singleLine = true)
                    LazyColumn(Modifier.height(320.dp)) {
                        items(people.filter {
                            it.title.contains(filter, true) || it.phoneNumber.orEmpty().contains(filter)
                        }, key = { it.id }) { contact ->
                            TextButton(onClick = {
                                val target = ContactShortcuts.target(context, contact, whatsapp)
                                if (target != null) onAdd(contact.title, target)
                                else Toast.makeText(context, "No se pudo interpretar este número", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(contact.title)
                                    Text(contact.phoneNumber.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                }
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
