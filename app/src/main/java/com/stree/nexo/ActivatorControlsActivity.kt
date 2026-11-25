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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.ControlPanelPage
import com.stree.nexo.ui.PanelMotion
import com.stree.nexo.ui.PanelSurface
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation
import com.stree.nexo.ui.gradientTint
import com.stree.nexo.ui.panelCardTransition
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import com.stree.nexo.ui.theme.visualSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brightness, audio and silence share their own compact low-positioned pager. */
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
                val style = remember(Prefs.themeRevision) { Prefs.panelStyle(this) }
                val light = remember(Prefs.themeRevision) { Prefs.panelLightBackground(this) }
                val themeSpec = remember(style, light) { style.visualSpec(light = light) }
                val iconBrush = remember(themeSpec) { themeSpec.actionIconBrush }

                PanelTheme(style) {
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
                                // A la altura exacta del disparador lateral para alcanzarlo directo con el dedo.
                                contentAlignment = if (side == PanelSide.END) Alignment.TopEnd else Alignment.TopStart
                            ) {
                                val density = LocalDensity.current
                                val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                                val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                                val topSafeMargin = maxOf(statusBarTop, 24.dp) + 8.dp
                                val bottomSafeMargin = navBarBottom + 16.dp

                                // Tamaño compacto propio, independiente del panel principal.
                                val panelWidth = minOf(maxWidth * 0.64f, 268.dp)
                                val panelHeight = minOf(maxHeight * 0.38f, 296.dp).coerceAtLeast(258.dp)

                                val handleHeight = Prefs.activationHeight(this@ActivatorControlsActivity).dp
                                    .coerceAtMost((maxHeight - 56.dp).coerceAtLeast(48.dp))
                                val maxHandleOffset = ((maxHeight / 2f) - (handleHeight / 2f) - 28.dp).coerceAtLeast(0.dp)
                                val triggerCenterY = (maxHeight / 2f) +
                                    maxHandleOffset * Prefs.panelYFraction(this@ActivatorControlsActivity)
                                val maxTopMargin = (maxHeight - bottomSafeMargin - panelHeight - 8.dp)
                                    .coerceAtLeast(topSafeMargin)
                                val panelTopMargin = (triggerCenterY - (panelHeight + 8.dp) / 2f)
                                    .coerceIn(topSafeMargin, maxTopMargin)

                                val activeRealPage = realIndex(pager.currentPage)
                                val tabItems = remember {
                                    listOf(
                                        Icons.Filled.Brightness6 to "Brillo",
                                        Icons.AutoMirrored.Filled.VolumeUp to "Audio",
                                        Icons.AutoMirrored.Filled.VolumeOff to "Silencio y asistente"
                                    )
                                }
                                val activePillColor = themeSpec.primary.copy(alpha = 0.16f)

                                HorizontalPager(
                                    state = pager,
                                    pageSpacing = 0.dp,
                                    modifier = Modifier
                                        .padding(top = panelTopMargin)
                                        .width(panelWidth)
                                        .height(panelHeight + 8.dp)
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
                                                ControlPanelPage(
                                                    mode = modes[realIndex(page)],
                                                    onDismiss = dismiss
                                                )
                                            }
                                            if (modes.size > 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                                    thickness = 0.5.dp,
                                                    color = themeSpec.outlineVariant.copy(alpha = 0.28f)
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    tabItems.forEachIndexed { index, (icon, label) ->
                                                        val active = activeRealPage == index
                                                        IconButton(
                                                            onClick = {
                                                                Haptics.perform(this@ActivatorControlsActivity)
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
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (active) activePillColor else Color.Transparent),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = label,
                                                                    modifier = Modifier.size(20.dp).gradientTint(iconBrush),
                                                                    tint = Color.White
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
