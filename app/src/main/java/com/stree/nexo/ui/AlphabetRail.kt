package com.stree.nexo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
internal fun AlphabetRail(letters: List<Char>, onLetter: (Char) -> Unit) {
    var height by remember { mutableIntStateOf(1) }
    val selectLetter by rememberUpdatedState(onLetter)
    Column(
        Modifier.width(22.dp).fillMaxHeight().onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters, height) {
                var last = -1
                fun select(y: Float) {
                    if (letters.isEmpty()) return
                    val index = (y.coerceIn(0f, height - 1f) / height * letters.size).toInt()
                        .coerceIn(letters.indices)
                    if (index != last) { last = index; selectLetter(letters[index]) }
                }
                detectDragGestures(onDragStart = { last = -1; select(it.y) }, onDrag = { change, _ ->
                    change.consume(); select(change.position.y)
                })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { letter ->
            Text(letter.toString(), Modifier.clickable { onLetter(letter) },
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
