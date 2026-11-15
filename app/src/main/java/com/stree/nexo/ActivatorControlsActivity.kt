package com.stree.nexo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.ControlPanelPage
import com.stree.nexo.ui.PanelSurface
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import kotlinx.coroutines.launch

/** Brightness, audio and silence share their own pager, separate from launcher pages. */
class ActivatorControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("control") ?: "actions"
        setContent {
            NexoTheme(darkTheme = true) {
                PanelTheme(Prefs.panelStyle(this)) {
                    val modes = remember(mode) {
                        if (mode == "lastapp") listOf("lastapp") else listOf("brightness", "volume", "actions")
                    }
                    val pager = rememberPagerState(initialPage = modes.indexOf(mode).coerceAtLeast(0), pageCount = { modes.size })
                    val scope = rememberCoroutineScope()
                    BoxWithConstraints(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)).clickable { finish() },
                        contentAlignment = if (Prefs.panelSide(this) == PanelSide.END) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        val width = minOf(maxWidth * .82f, 300.dp)
                        val contentHeight = when (modes[pager.currentPage]) {
                            "brightness" -> 220.dp
                            "volume" -> 340.dp
                            else -> 300.dp
                        }
                        val height = minOf(maxHeight - 24.dp, 480.dp, contentHeight + if (modes.size > 1) 48.dp else 0.dp)
                        PanelSurface(Modifier.width(width).height(height)
                            .padding(12.dp).clickable {}) {
                            HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                                ControlPanelPage(modes[page])
                            }
                            if (modes.size > 1) Row(Modifier.fillMaxWidth()) {
                                listOf("Brillo", "Audio", "Silencio").forEachIndexed { index, label ->
                                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                        modifier = Modifier.weight(1f)) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                            color = if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        EdgePanelService.setPanelOpen(true)
    }

    override fun onPause() {
        EdgePanelService.setPanelOpen(false)
        super.onPause()
    }
}
