package com.stree.nexo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.SearchItem
import com.stree.nexo.search.SearchEngine
import com.stree.nexo.search.WhatsAppLinks

/**
 * Determina si una consulta en Spotlight coincide de forma inequívoca con una app o un contacto:
 * - Coincidencia exacta del nombre normalizado (desde 2 caracteres) si es única.
 * - O resultado único total (app o contacto) con al menos 3 caracteres escritos/dictados.
 */
internal fun resolveSpotlightAutoMatch(
    query: String,
    results: List<SearchItem>,
    looksLikeUrl: Boolean,
    mathResult: String?
): SearchItem? {
    if (looksLikeUrl || mathResult != null) return null
    val normalized = SearchEngine.normalize(query)
    if (normalized.length < 2) return null

    val eligible = results.filter { it.type == ItemType.APP || it.type == ItemType.CONTACT }
    if (eligible.isEmpty()) return null

    val exactMatches = eligible.filter { SearchEngine.normalize(it.title) == normalized }
    if (exactMatches.size == 1) {
        return exactMatches.first()
    }

    if (normalized.length >= 3 && eligible.size == 1 && results.size == 1) {
        return eligible.first()
    }

    return null
}

/**
 * Barra compacta de aviso con progreso animado justo antes de abrir automáticamente una app
 * o la ficha de un contacto. Tocar la barra cancela la apertura para esa consulta.
 */
@Composable
internal fun SpotlightAutoOpenBanner(
    item: SearchItem,
    durationMs: Int,
    accentColor: Color,
    cardColor: Color,
    cardBorder: Color,
    primaryTextColor: Color,
    mutedTextColor: Color,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember(item.id, durationMs) { Animatable(0f) }
    LaunchedEffect(item.id, durationMs) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs.coerceAtLeast(150), easing = LinearEasing)
        )
    }

    val actionLabel = if (item.type == ItemType.CONTACT) {
        "Abriendo ficha de ${item.title}…"
    } else {
        "Abriendo ${item.title}…"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCancel),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemIcon(item = item, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Cancelar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(cardBorder.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(accentColor)
                )
            }
        }
    }
}

/**
 * Ficha rápida de contacto dentro de Spotlight con todas las acciones principales
 * (Llamar, WhatsApp, Mensaje SMS, Copiar número, Ver en Contactos y Fijar).
 */
@Composable
internal fun SpotlightContactSheet(
    contact: SearchItem,
    isPinned: Boolean,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    primaryTextColor: Color,
    mutedTextColor: Color,
    onCall: () -> Unit,
    onTogglePin: () -> Unit,
    onActionCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val phone = contact.phoneNumber?.trim().orEmpty()
    val whatsAppIntent = remember(phone) {
        phone.takeIf { it.isNotBlank() }?.let { WhatsAppLinks.intent(context, it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(26.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = mutedTextColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ItemIcon(item = contact, size = 62.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = contact.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (phone.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Acciones rápidas principales: Llamar, WhatsApp, Mensaje SMS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ContactQuickActionTile(
                        icon = Icons.Filled.Call,
                        label = "Llamar",
                        accent = Color(0xFF22C55E),
                        textColor = primaryTextColor,
                        borderColor = borderColor,
                        enabled = phone.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Haptics.perform(context)
                        onDismiss()
                        onCall()
                    }
                    ContactQuickActionTile(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "WhatsApp",
                        accent = Color(0xFF25D366),
                        textColor = primaryTextColor,
                        borderColor = borderColor,
                        enabled = whatsAppIntent != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        val intent = whatsAppIntent ?: return@ContactQuickActionTile
                        Haptics.perform(context)
                        runCatching {
                            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure {
                            Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                        onActionCompleted()
                    }
                    ContactQuickActionTile(
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "Mensaje",
                        accent = accentColor,
                        textColor = primaryTextColor,
                        borderColor = borderColor,
                        enabled = phone.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Haptics.perform(context)
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure {
                            Toast.makeText(context, "No se pudo abrir Mensajes", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                        onActionCompleted()
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(thickness = 0.6.dp, color = borderColor)
                Spacer(Modifier.height(6.dp))

                // Acciones secundarias: Copiar número, Abrir en Contactos, Fijar
                if (phone.isNotBlank()) {
                    ContactSheetRow(
                        icon = Icons.Filled.ContentCopy,
                        label = "Copiar número",
                        accent = accentColor,
                        textColor = primaryTextColor
                    ) {
                        Haptics.perform(context)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText(contact.title, phone))
                        Toast.makeText(context, "Número copiado: $phone", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    ContactSheetRow(
                        icon = Icons.Filled.Person,
                        label = "Abrir en Contactos",
                        accent = accentColor,
                        textColor = primaryTextColor
                    ) {
                        Haptics.perform(context)
                        openNativeContactCard(context, phone)
                        onDismiss()
                        onActionCompleted()
                    }
                }

                ContactSheetRow(
                    icon = Icons.Filled.PushPin,
                    label = if (isPinned) "Quitar de fijados" else "Fijar en el panel",
                    accent = accentColor,
                    textColor = primaryTextColor
                ) {
                    Haptics.perform(context)
                    onTogglePin()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ContactQuickActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    textColor: Color,
    borderColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = if (enabled) 0.16f else 0.06f),
        border = BorderStroke(0.8.dp, if (enabled) accent.copy(alpha = 0.42f) else borderColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (enabled) 0.24f else 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) accent else accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) textColor else textColor.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactSheetRow(
    icon: ImageVector,
    label: String,
    accent: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun openNativeContactCard(context: Context, phone: String) {
    val lookupUri = runCatching {
        val filterUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone)
        )
        context.contentResolver.query(
            filterUri,
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val key = cursor.getString(1)
                if (!key.isNullOrBlank()) {
                    ContactsContract.Contacts.getLookupUri(id, key)
                } else {
                    Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
                }
            } else {
                null
            }
        }
    }.getOrNull()

    val opened = if (lookupUri != null) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, lookupUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    } else {
        false
    }

    if (!opened) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "No se pudo abrir la ficha del contacto", Toast.LENGTH_SHORT).show()
        }
    }
}
