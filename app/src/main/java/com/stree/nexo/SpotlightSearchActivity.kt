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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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

        val islandTopOffsetDp = Prefs.dynamicIslandTopOffset(this)
        val islandHorizontalOffsetDp = Prefs.dynamicIslandHorizontalOffset(this)
        val islandIdleHeightDp = Prefs.dynamicIslandIdleHeight(this)

        setContent {
            NexoTheme(darkTheme = true) {
                val style = remember(Prefs.themeRevision) { Prefs.panelStyle(this) }
                PanelTheme(style) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()
                    val progress = remember { Animatable(0f) }
                    var closing by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = PanelMotion.SPOTLIGHT_ENTER_DURATION_MS,
                                easing = PanelMotion.IslandEmergeEasing
                            )
                        )
                    }

                    val dismiss: () -> Unit = {
                        if (!closing) {
                            closing = true
                            scope.launch {
                                progress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = PanelMotion.SPOTLIGHT_EXIT_DURATION_MS,
                                        easing = PanelMotion.ExitEasing
                                    )
                                )
                                finishNoSystemAnimation(removeTask = true)
                            }
                        }
                    }

                    BackHandler { dismiss() }

                    val t = progress.value.coerceIn(0f, 1f)
                    val scrimAlpha = 0.56f * t
                    val density = LocalDensity.current

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ComposeColor.Black.copy(alpha = scrimAlpha))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = dismiss
                            )
                    ) {
                        val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
                        val heightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
                        val islandCenterX = widthPx / 2f + with(density) { islandHorizontalOffsetDp.dp.toPx() }
                        val islandTopPx = with(density) { islandTopOffsetDp.dp.toPx() }
                        val islandCenterY = islandTopPx + with(density) { (islandIdleHeightDp / 2f).dp.toPx() }
                        val pivotX = (islandCenterX / widthPx).coerceIn(0.15f, 0.85f)
                        val pivotY = (islandCenterY / heightPx).coerceIn(0.01f, 0.22f)
                        val cornerRadiusDp = lerp(40f, 0f, t).dp

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(pivotX, pivotY)
                                    scaleX = lerp(0.18f, 1f, t)
                                    scaleY = lerp(0.06f, 1f, t)
                                    translationY = lerp(-islandTopPx * 0.35f, 0f, t)
                                    alpha = (t / 0.35f).coerceIn(0f, 1f)
                                    shape = RoundedCornerShape(cornerRadiusDp)
                                    clip = t < 0.995f
                                }
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
                                autoListen = true,
                                overlayStyle = true,
                                onCancel = dismiss,
                                onOpenFloatingMenu = {
                                    startActivity(
                                        Intent(this@SpotlightSearchActivity, FloatingWindowMenuActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        }
                                    )
                                    dismiss()
                                },
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
