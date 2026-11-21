package com.stree.nexo

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.ControlPanelPage
import com.stree.nexo.ui.PanelMotion
import com.stree.nexo.ui.PanelSurface
import com.stree.nexo.ui.computePanelSize
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation
import com.stree.nexo.ui.panelCardTransition
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brightness, audio and silence share their own pager, separate from launcher pages. */
class ActivatorControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        disableSystemTransitions()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val mode = intent.getStringExtra("control") ?: "actions"
        val side = Prefs.panelSide(this)
        setContent {
            NexoTheme(darkTheme = true) {
                PanelTheme(Prefs.panelStyle(this)) {
                    val modes = remember(mode) {
                        if (mode == "lastapp") listOf("lastapp") else listOf("brightness", "volume", "actions")
                    }
                    val infiniteScroll = remember(modes) { Prefs.panelInfiniteScroll(this) && modes.size > 1 }
                    val pagerCount = if (infiniteScroll) modes.size * 1000 else modes.size
                    val startIndex = modes.indexOf(mode).coerceAtLeast(0)
                    val initialPagerPage = if (infiniteScroll) modes.size * 500 + startIndex else startIndex
                    val pager = rememberPagerState(initialPage = initialPagerPage, pageCount = { pagerCount })
                    fun realIndex(virtualPage: Int): Int = ((virtualPage % modes.size) + modes.size) % modes.size
                    fun nearestVirtualPage(targetReal: Int): Int {
                        if (!infiniteScroll) return targetReal.coerceIn(0, modes.lastIndex)
                        val cur = pager.currentPage
                        val curReal = realIndex(cur)
                        var delta = targetReal - curReal
                        val half = modes.size / 2
                        if (delta > half) delta -= modes.size
                        else if (delta < -half) delta += modes.size
                        return (cur + delta).coerceIn(0, pagerCount - 1)
                    }
                    val scope = rememberCoroutineScope()
                    var shown by remember { mutableStateOf(false) }
                    var closing by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }

                    val dismiss: () -> Unit = {
                        if (!closing) {
                            closing = true
                            shown = false
                            scope.launch {
                                delay(PanelMotion.EXIT_DURATION_MS.toLong())
                                finishNoSystemAnimation()
                            }
                        }
                    }

                    BackHandler { dismiss() }

                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (shown) 0.22f else 0f,
                        animationSpec = tween(
                            durationMillis = if (shown) PanelMotion.SCRIM_ENTER_MS else PanelMotion.SCRIM_EXIT_MS,
                            easing = if (shown) PanelMotion.EnterEasing else PanelMotion.ExitEasing
                        ),
                        label = "controlsScrim"
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
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = if (side == PanelSide.END) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                val panelSize = computePanelSize(this@ActivatorControlsActivity, maxWidth, maxHeight)
                                val activeRealPage = realIndex(pager.currentPage)
                                HorizontalPager(
                                    state = pager,
                                    pageSpacing = 0.dp,
                                    modifier = Modifier
                                        .width(panelSize.width)
                                        .height(panelSize.height + 8.dp)
                                ) { page ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .panelCardTransition(pager, page, side)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        PanelSurface(
                                            Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {}
                                                )
                                        ) {
                                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                                ControlPanelPage(modes[realIndex(page)])
                                            }
                                            if (modes.size > 1) Row(Modifier.fillMaxWidth()) {
                                                listOf("Brillo", "Audio", "Silencio").forEachIndexed { index, label ->
                                                    TextButton(
                                                        onClick = {
                                                            scope.launch {
                                                                pager.animateScrollToPage(
                                                                    nearestVirtualPage(index),
                                                                    animationSpec = tween(
                                                                        PanelMotion.TAB_DURATION_MS,
                                                                        easing = PanelMotion.StandardEasing
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1,
                                                            color = if (activeRealPage == index) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
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
