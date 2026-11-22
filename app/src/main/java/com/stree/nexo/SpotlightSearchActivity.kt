package com.stree.nexo

import android.content.Intent
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.SearchItem
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.search.ContactCalls
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.search.SearchViewModel
import com.stree.nexo.ui.PanelMotion
import com.stree.nexo.ui.SpotlightSearchContent
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Overlay global de búsqueda estilo iPhone Spotlight accesible por gesto o acceso directo. */
class SpotlightSearchActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()
    private val contactCalls = ContactCalls(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        disableSystemTransitions()
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 44 }
            }
        }

        EdgePanelService.setPanelOpen(true)

        setContent {
            NexoTheme(darkTheme = true) {
                val style = remember(Prefs.themeRevision) { Prefs.panelStyle(this) }
                PanelTheme(style) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
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
                                finishNoSystemAnimation(removeTask = true)
                            }
                        }
                    }

                    BackHandler { dismiss() }

                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (shown) 0.56f else 0f,
                        animationSpec = tween(
                            durationMillis = if (shown) PanelMotion.SCRIM_ENTER_MS else PanelMotion.SCRIM_EXIT_MS,
                            easing = if (shown) PanelMotion.EnterEasing else PanelMotion.ExitEasing
                        ),
                        label = "spotlightScrim"
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
                    ) {
                        AnimatedVisibility(
                            visible = shown,
                            enter = slideInVertically(
                                animationSpec = tween(PanelMotion.ENTER_DURATION_MS, easing = PanelMotion.EnterEasing),
                                initialOffsetY = { -it / 10 }
                            ) + fadeIn(tween(PanelMotion.ENTER_DURATION_MS)),
                            exit = slideOutVertically(
                                animationSpec = tween(PanelMotion.EXIT_DURATION_MS, easing = PanelMotion.ExitEasing),
                                targetOffsetY = { -it / 12 }
                            ) + fadeOut(tween(PanelMotion.EXIT_DURATION_MS))
                        ) {
                            SpotlightSearchContent(
                                state = state,
                                onQueryChange = viewModel::onQueryChange,
                                onItemClick = { item ->
                                    openItem(item)
                                    dismiss()
                                },
                                onTogglePin = { item ->
                                    val pinned = viewModel.togglePinned(item)
                                    Haptics.perform(this@SpotlightSearchActivity, strong = true)
                                    Toast.makeText(
                                        this@SpotlightSearchActivity,
                                        if (pinned) "Fijado: ${item.title}" else "Quitado: ${item.title}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onOpenFloating = { item ->
                                    openFloating(item)
                                    dismiss()
                                },
                                autoFocus = true,
                                overlayStyle = true,
                                onCancel = dismiss,
                                onOpenGestureSettings = {
                                    startActivity(
                                        Intent(this@SpotlightSearchActivity, MainActivity::class.java).apply {
                                            putExtra("open_settings", true)
                                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        }
                                    )
                                    dismiss()
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.statusBars)
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

    private fun openItem(item: SearchItem) {
        viewModel.recordUsage(item.id)
        Haptics.perform(this)
        if (contactCalls.handle(item)) return
        if (!ItemLauncher.launch(this, item)) {
            Toast.makeText(this, "No se pudo abrir ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFloating(item: SearchItem) {
        viewModel.recordUsage(item.id)
        Haptics.perform(this)
        if (!ItemLauncher.launchFloating(this, item)) {
            if (!ItemLauncher.launch(this, item)) {
                Toast.makeText(this, "No se pudo abrir ${item.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
