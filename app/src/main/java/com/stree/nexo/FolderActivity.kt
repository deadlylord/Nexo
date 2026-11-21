package com.stree.nexo

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.ui.PanelMotion
import com.stree.nexo.ui.PanelSurface
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FolderActivity : ComponentActivity() {
    data class FolderApp(val packageName: String, val label: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        disableSystemTransitions()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val uri = intent.data
        val title = uri?.getQueryParameter("name").orEmpty().ifBlank { "Carpeta" }
        val side = Prefs.panelSide(this)
        val apps = uri?.getQueryParameters("app").orEmpty().mapNotNull { packageName ->
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                FolderApp(
                    packageName,
                    packageManager.getApplicationLabel(info).toString(),
                    packageManager.getApplicationIcon(info)
                )
            }.getOrNull()
        }

        setContent {
            NexoTheme(darkTheme = true) {
                PanelTheme(Prefs.panelStyle(this)) {
                    FolderPopup(title, apps, side, onDismiss = { finishNoSystemAnimation() }) { app ->
                        Haptics.perform(this)
                        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        finishNoSystemAnimation()
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPopup(
    title: String,
    apps: List<FolderActivity.FolderApp>,
    side: PanelSide,
    onDismiss: () -> Unit,
    onOpen: (FolderActivity.FolderApp) -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { shown = true }

    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            shown = false
            scope.launch {
                delay(PanelMotion.EXIT_DURATION_MS.toLong())
                onDismiss()
            }
        }
    }

    BackHandler { dismiss() }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 0.28f else 0f,
        animationSpec = tween(
            durationMillis = if (shown) PanelMotion.SCRIM_ENTER_MS else PanelMotion.SCRIM_EXIT_MS,
            easing = if (shown) PanelMotion.EnterEasing else PanelMotion.ExitEasing
        ),
        label = "folderScrim"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismiss
            )
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = PanelMotion.sideEnter(side),
            exit = PanelMotion.sideExit(side),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (side == PanelSide.END) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                PanelSurface(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .fillMaxHeight(0.52f)
                        .padding(horizontal = 10.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(Modifier.padding(top = 18.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                Column(
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(app) }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val bitmap = remember(app.packageName) {
                                        app.icon.toBitmap(96, 96).asImageBitmap()
                                    }
                                    Image(bitmap, app.label, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
