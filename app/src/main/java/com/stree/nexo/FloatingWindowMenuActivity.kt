package com.stree.nexo

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.Prefs
import com.stree.nexo.floating.FloatingWindowRegistry
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.search.SearchViewModel
import com.stree.nexo.ui.FloatingWindowMenuContent
import com.stree.nexo.ui.PanelMotion
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Menú profesional flotante y gestor visual de layouts multi-ventana de Nexo. */
class FloatingWindowMenuActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        disableSystemTransitions()
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 38 }
            }
        }

        EdgePanelService.setPanelOpen(true)

        setContent {
            NexoTheme(darkTheme = true) {
                val style = remember(Prefs.themeRevision) { Prefs.panelStyle(this) }
                PanelTheme(style) {
                    val searchState by viewModel.state.collectAsStateWithLifecycle()
                    val entries by FloatingWindowRegistry.entries.collectAsStateWithLifecycle()
                    val activeLayout by FloatingWindowRegistry.activeLayout.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()
                    var shown by remember { mutableStateOf(false) }
                    var closing by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) { shown = true }

                    val orderedApps = remember(
                        searchState.allItems,
                        searchState.pinnedOrder,
                        searchState.frequentApps
                    ) {
                        val byId = searchState.allItems.associateBy { it.id }
                        val pinned = searchState.pinnedOrder.mapNotNull { byId[it] }.filter { it.type == ItemType.APP }
                        val freq = searchState.frequentApps.filter { it.type == ItemType.APP }
                        val all = searchState.allItems.filter { it.type == ItemType.APP }
                        (pinned + freq + all).distinctBy { it.packageName }
                    }

                    val dismiss: () -> Unit = {
                        if (!closing) {
                            closing = true
                            shown = false
                            scope.launch {
                                delay(PanelMotion.EXIT_DURATION_MS.toLong())
                                finishNoSystemAnimation(removeTask = true)
                            }
                        }
                    }

                    BackHandler { dismiss() }

                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (shown) 0.48f else 0f,
                        animationSpec = tween(
                            durationMillis = if (shown) PanelMotion.SCRIM_ENTER_MS else PanelMotion.SCRIM_EXIT_MS,
                            easing = if (shown) PanelMotion.EnterEasing else PanelMotion.ExitEasing
                        ),
                        label = "floatingMenuScrim"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ComposeColor.Black.copy(alpha = scrimAlpha))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = dismiss
                            )
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedVisibility(
                            visible = shown,
                            enter = scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(PanelMotion.ENTER_DURATION_MS, easing = PanelMotion.EnterEasing)
                            ) + fadeIn(tween(PanelMotion.ENTER_DURATION_MS)),
                            exit = scaleOut(
                                targetScale = 0.94f,
                                animationSpec = tween(PanelMotion.EXIT_DURATION_MS, easing = PanelMotion.ExitEasing)
                            ) + fadeOut(tween(PanelMotion.EXIT_DURATION_MS))
                        ) {
                            FloatingWindowMenuContent(
                                entries = entries,
                                activeLayout = activeLayout,
                                availableApps = orderedApps,
                                onMinimizeApp = { pkg ->
                                    FloatingWindowRegistry.markMinimized(this@FloatingWindowMenuActivity, pkg)
                                    EdgePanelService.minimizeToBackground(this@FloatingWindowMenuActivity)
                                    dismiss()
                                },
                                onMinimizeAll = {
                                    FloatingWindowRegistry.markAllMinimized(this@FloatingWindowMenuActivity)
                                    EdgePanelService.minimizeToBackground(this@FloatingWindowMenuActivity)
                                    dismiss()
                                },
                                onFocusApp = { entry ->
                                    ItemLauncher.launchPackageFloatingInBounds(
                                        context = this@FloatingWindowMenuActivity,
                                        packageName = entry.packageName,
                                        bounds = entry.lastBounds,
                                        registerInDock = true
                                    )
                                    dismiss()
                                },
                                onMaximizeApp = { pkg ->
                                    FloatingWindowRegistry.remove(this@FloatingWindowMenuActivity, pkg)
                                    if (!ItemLauncher.launchPackageFullscreen(this@FloatingWindowMenuActivity, pkg)) {
                                        Toast.makeText(this@FloatingWindowMenuActivity, "No se pudo maximizar la app", Toast.LENGTH_SHORT).show()
                                    }
                                    dismiss()
                                },
                                onRemoveApp = { pkg ->
                                    FloatingWindowRegistry.remove(this@FloatingWindowMenuActivity, pkg)
                                    if (FloatingWindowRegistry.entries.value.isEmpty()) {
                                        dismiss()
                                    }
                                },
                                onClearAll = {
                                    FloatingWindowRegistry.clearAll(this@FloatingWindowMenuActivity)
                                    dismiss()
                                },
                                onMoveSlot = { from, to ->
                                    FloatingWindowRegistry.moveEntry(from, to)
                                },
                                onAddApp = { pkg, readyLayout, keepOpen ->
                                    if (readyLayout != null) {
                                        val pkgs = (FloatingWindowRegistry.entries.value.map { it.packageName } + pkg).distinct()
                                        FloatingWindowRegistry.applyLayout(this@FloatingWindowMenuActivity, readyLayout, pkgs)
                                        dismiss()
                                    } else if (keepOpen) {
                                        FloatingWindowRegistry.registerMinimized(this@FloatingWindowMenuActivity, pkg)
                                    } else {
                                        ItemLauncher.launchPackageFloatingInBounds(
                                            context = this@FloatingWindowMenuActivity,
                                            packageName = pkg,
                                            bounds = null,
                                            registerInDock = true
                                        )
                                        dismiss()
                                    }
                                },
                                onApplyLayout = { layout ->
                                    val pkgs = FloatingWindowRegistry.entries.value.map { it.packageName }
                                    FloatingWindowRegistry.applyLayout(this@FloatingWindowMenuActivity, layout, pkgs)
                                    dismiss()
                                },
                                onDismiss = dismiss,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        EdgePanelService.setPanelOpen(true)
        viewModel.refresh()
    }

    override fun onPause() {
        EdgePanelService.setPanelOpen(false)
        super.onPause()
    }

    override fun onDestroy() {
        EdgePanelService.setPanelOpen(false)
        super.onDestroy()
    }
}
