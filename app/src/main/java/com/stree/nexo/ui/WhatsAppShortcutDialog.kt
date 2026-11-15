package com.stree.nexo.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stree.nexo.search.WhatsAppLinks

@Composable
fun WhatsAppShortcutDialog(
    initialName: String = "",
    initialPhone: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var phone by remember {
        // Si el contacto guarda el número sin prefijo, se completa solo con
        // el país del teléfono. El usuario puede corregirlo.
        mutableStateOf(WhatsAppLinks.e164(context, initialPhone) ?: initialPhone)
    }
    val compact = phone.trim().replace(Regex("[\\s()\\-]"), "")
    val digits = when {
        compact.startsWith("+") -> compact.drop(1)
        compact.startsWith("00") -> compact.drop(2)
        else -> ""
    }
    val valid = digits.matches(Regex("[1-9][0-9]{7,14}"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atajo de WhatsApp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nombre del contacto") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Número con código de país") },
                    placeholder = { Text("+57…") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phone.isNotBlank() && !valid,
                    supportingText = { Text("Se completa con el país del teléfono; puedes editarlo.") })
                Text("El atajo abrirá el chat directamente, sin mensaje preparado.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid && name.isNotBlank(), onClick = {
                val uri = Uri.parse("https://wa.me/$digits")
                onConfirm("WhatsApp · ${name.trim()}", uri.toString())
            }) { Text("Guardar atajo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
