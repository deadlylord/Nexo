package com.stree.nexo.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Botón de micrófono con pulso animado mientras se dicta. */
@Composable
fun NexoMicButton(
    dictation: NexoDictationState,
    idleTint: Color,
    activeColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val pulse = rememberInfiniteTransition(label = "micPulse")
    val wave by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "micWave"
    )
    val listening = dictation.listening
    val ring = if (listening) wave + dictation.level * 0.25f else 1f

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable { dictation.toggle() },
        contentAlignment = Alignment.Center
    ) {
        if (listening) {
            Box(
                Modifier
                    .size(size * 0.78f)
                    .graphicsLayer { scaleX = ring; scaleY = ring }
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.22f))
            )
        }
        Box(
            Modifier
                .size(size * 0.7f)
                .clip(CircleShape)
                .background(if (listening) activeColor else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Detener dictado" else "Dictar por voz",
                tint = if (listening) Color.White else idleTint,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** Fila compacta con accesos a los modos de conversación por voz. */
@Composable
fun AiLiveChipsRow(
    accent: Color,
    textColor: Color,
    chipColor: Color,
    borderColor: Color,
    onGeminiLive: () -> Unit,
    onChatGptVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiChip(
            icon = Icons.Filled.GraphicEq,
            label = "Gemini Live",
            accent = accent,
            textColor = textColor,
            chipColor = chipColor,
            borderColor = borderColor,
            onClick = onGeminiLive,
            modifier = Modifier.weight(1f)
        )
        AiChip(
            icon = Icons.Filled.RecordVoiceOver,
            label = "ChatGPT Voz",
            accent = accent,
            textColor = textColor,
            chipColor = chipColor,
            borderColor = borderColor,
            onClick = onChatGptVoice,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Fila con las opciones para enviar la consulta escrita a una IA. */
@Composable
fun AiAskChipsRow(
    accent: Color,
    textColor: Color,
    chipColor: Color,
    borderColor: Color,
    onAskGemini: () -> Unit,
    onAskChatGpt: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiChip(
            icon = Icons.Filled.AutoAwesome,
            label = "Preguntar a Gemini",
            accent = accent,
            textColor = textColor,
            chipColor = chipColor,
            borderColor = borderColor,
            onClick = onAskGemini,
            modifier = Modifier.weight(1f)
        )
        AiChip(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "Preguntar a ChatGPT",
            accent = accent,
            textColor = textColor,
            chipColor = chipColor,
            borderColor = borderColor,
            onClick = onAskChatGpt,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AiChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    textColor: Color,
    chipColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = chipColor,
        border = BorderStroke(0.8.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
