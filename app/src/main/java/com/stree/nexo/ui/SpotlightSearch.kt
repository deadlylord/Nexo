package com.stree.nexo.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stree.nexo.WidgetPickerActivity
import com.stree.nexo.data.ClipboardHistoryStore
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.SearchItem
import com.stree.nexo.data.SearchUiState
import com.stree.nexo.search.AiLauncher
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.ui.theme.visualSpec
import com.stree.nexo.widget.WidgetStore
import com.stree.nexo.widget.WidgetTab
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Búsqueda estilo Spotlight de iPhone integrada con la estética y temas de Nexo.
 * Se reutiliza tanto en la pantalla principal como en el overlay global por gesto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotlightSearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onItemClick: (SearchItem) -> Unit,
    onTogglePin: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    overlayStyle: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onOpenGestureSettings: (() -> Unit)? = null,
    onPinSpotlightShortcut: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val style = remember(Prefs.themeRevision) { Prefs.panelStyle(context) }
    val light = remember(Prefs.themeRevision) { !overlayStyle && Prefs.panelLightBackground(context) }
    val spec = remember(style, light) { style.visualSpec(light = light) }
    val iconBrush = remember(spec) { spec.actionIconBrush }

    val focusRequester = remember { FocusRequester() }
    var actionItem by remember { mutableStateOf<SearchItem?>(null) }
    var widgetRevision by remember { mutableIntStateOf(0) }
    var fullscreenWidgetId by remember { mutableStateOf<String?>(null) }
    var widgetEditMode by remember { mutableStateOf(false) }
    var selectedTabId by remember { mutableStateOf(WidgetStore.selectedSpotlightTabId(context)) }
    var editingTab by remember { mutableStateOf<WidgetTab?>(null) }
    var creatingTab by remember { mutableStateOf(false) }
    var movingWidgetId by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val currentQueryChange by rememberUpdatedState(onQueryChange)
    val dictation = rememberNexoDictation { spoken -> currentQueryChange(spoken) }

    DisposableEffect(owner) {
        var listening = false
        fun update() {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (!listening) listening = runCatching { WidgetStore.startListening(context); true }.getOrDefault(false)
            } else if (listening) {
                WidgetStore.stopListening(context)
                listening = false
                widgetEditMode = false
                dictation.stop()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) widgetRevision++
            update()
        }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (listening) WidgetStore.stopListening(context)
        }
    }

    val widgetTabs = remember(widgetRevision) { WidgetStore.spotlightTabs(context) }
    LaunchedEffect(widgetTabs) {
        if (widgetTabs.none { it.id == selectedTabId }) {
            selectedTabId = widgetTabs.first().id
        }
    }
    val spotlightWidgetIds = remember(widgetRevision, selectedTabId) {
        WidgetStore.spotlightItemsForTab(context, selectedTabId)
    }
    LaunchedEffect(spotlightWidgetIds, fullscreenWidgetId) {
        if (fullscreenWidgetId != null && fullscreenWidgetId !in spotlightWidgetIds) {
            fullscreenWidgetId = null
        }
    }

    LaunchedEffect(autoFocus, fullscreenWidgetId) {
        if (fullscreenWidgetId != null) {
            focusManager.clearFocus()
        } else if (autoFocus) {
            delay(110)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Al deslizar la lista hacia los widgets se esconde el teclado.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            }
    }

    BackHandler(enabled = widgetEditMode && fullscreenWidgetId == null) {
        widgetEditMode = false
    }

    BackHandler(enabled = fullscreenWidgetId != null) {
        fullscreenWidgetId = null
    }

    val openWidgetPicker: () -> Unit = {
        Haptics.perform(context)
        widgetEditMode = false
        val intent = Intent(context, WidgetPickerActivity::class.java)
            .putExtra("item_id", "spotlight-widget:${System.currentTimeMillis()}:${UUID.randomUUID()}")
            .putExtra(WidgetPickerActivity.EXTRA_TAB_ID, selectedTabId)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    val query = state.query.trim()
    val mathResult = remember(query) { evaluateSimpleMath(query) }
    val looksLikeUrl = remember(query) {
        query.isNotEmpty() && !query.contains(' ') &&
            (query.startsWith("http://", true) || query.startsWith("https://", true) ||
                (query.contains('.') && query.substringAfterLast('.').length in 2..6))
    }

    val cardColor = if (overlayStyle) {
        spec.surface.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val cardBorder = if (overlayStyle) {
        spec.outlineVariant.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    }
    val searchPillBg = if (overlayStyle) {
        spec.surfaceVariant.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    }
    val primaryTextColor = if (overlayStyle) spec.onSurface else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (overlayStyle) spec.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant

    val suggestedApps = remember(state.allItems, state.pinnedOrder, state.frequentApps) {
        val byId = state.allItems.associateBy { it.id }
        val pinned = state.pinnedOrder.mapNotNull { byId[it] }.filter { it.type == ItemType.APP }
        val freq = state.frequentApps.filter { it.type == ItemType.APP }
        val allApps = state.allItems.filter { it.type == ItemType.APP }
        (pinned + freq + allApps).distinctBy { it.id }.take(8)
    }

    val flatResults = remember(state.sections) { state.sections.flatMap { it.items } }
    val topHit = remember(query, flatResults) {
        if (query.isEmpty()) null else flatResults.firstOrNull()
    }

    val launchWebSearch: () -> Unit = {
        if (query.isNotEmpty()) {
            Haptics.perform(context)
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            onCancel?.invoke()
        }
    }

    val launchUrl: () -> Unit = {
        if (looksLikeUrl) {
            Haptics.perform(context)
            val normalized = if (query.startsWith("http://", true) || query.startsWith("https://", true)) {
                query
            } else {
                "https://$query"
            }
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            onCancel?.invoke()
        }
    }

    val activeFullscreenId = fullscreenWidgetId
    if (activeFullscreenId != null) {
        FullscreenSpotlightWidget(
            itemId = activeFullscreenId,
            cardColor = cardColor,
            cardBorder = cardBorder,
            primaryTextColor = primaryTextColor,
            accentColor = spec.primary,
            onExitFullscreen = { fullscreenWidgetId = null },
            onDelete = {
                WidgetStore.remove(context, activeFullscreenId)
                fullscreenWidgetId = null
                widgetRevision++
            },
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.imePadding()) {
        header?.invoke()

        // Barra Spotlight estilo iOS (píldora de cristal + cancelar opcional)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(22.dp),
                color = searchPillBg,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            spec.primary.copy(alpha = 0.45f),
                            cardBorder
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Buscar",
                        modifier = Modifier
                            .size(20.dp)
                            .gradientTint(iconBrush),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (state.query.isEmpty()) {
                            Text(
                                text = "Buscar apps, contactos o web…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedTextColor.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(spec.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    when {
                                        topHit != null -> onItemClick(topHit)
                                        looksLikeUrl -> launchUrl()
                                        query.isNotEmpty() -> launchWebSearch()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(mutedTextColor.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Limpiar",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    NexoMicButton(
                        dictation = dictation,
                        idleTint = mutedTextColor,
                        activeColor = spec.primary,
                        size = 32.dp
                    )
                }
            }

            if (onCancel != null) {
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Cancelar",
                        color = spec.primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (query.isEmpty()) {
            AiLiveChipsRow(
                accent = spec.primary,
                textColor = primaryTextColor,
                chipColor = cardColor,
                borderColor = cardBorder,
                onGeminiLive = {
                    if (AiLauncher.startGeminiLive(context)) onCancel?.invoke()
                },
                onChatGptVoice = {
                    if (AiLauncher.startChatGptVoice(context)) onCancel?.invoke()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (query.isEmpty()) {
                // 1. Sugerencias de apps (cuadrícula 4×2 estilo iOS Spotlight)
                if (suggestedApps.isNotEmpty()) {
                    item(key = "spotlight_suggestions") {
                        SpotlightCard(
                            title = "Sugerencias",
                            cardColor = cardColor,
                            cardBorder = cardBorder,
                            headerColor = mutedTextColor
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                suggestedApps.chunked(4).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .combinedClickable(
                                                        onClick = { onItemClick(item) },
                                                        onLongClick = {
                                                            Haptics.perform(context, strong = true)
                                                            actionItem = item
                                                        }
                                                    )
                                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                ItemIcon(item = item, size = 46.dp)
                                                Spacer(Modifier.height(5.dp))
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = primaryTextColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        repeat(4 - rowItems.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Widgets de pantalla completa en el panel estilo iPhone
                item(key = "spotlight_widgets_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 2.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "WIDGETS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = mutedTextColor.copy(alpha = 0.85f),
                            letterSpacing = 0.7.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                Haptics.perform(context)
                                widgetEditMode = !widgetEditMode
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (widgetEditMode) spec.primary.copy(alpha = 0.24f) else Color.Transparent,
                            border = BorderStroke(0.8.dp, spec.primary.copy(alpha = if (widgetEditMode) 0.55f else 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (widgetEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                                    contentDescription = if (widgetEditMode) "Terminar edición" else "Editar widgets",
                                    tint = spec.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (widgetEditMode) "Listo" else "Editar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = spec.primary
                                )
                            }
                        }
                        Surface(
                            onClick = openWidgetPicker,
                            shape = RoundedCornerShape(12.dp),
                            color = spec.primary.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, spec.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Añadir widget",
                                    tint = spec.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Añadir",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = spec.primary
                                )
                            }
                        }
                    }
                }

                item(key = "spotlight_widget_tabs") {
                    SpotlightWidgetTabsRow(
                        tabs = widgetTabs,
                        selectedId = selectedTabId,
                        editing = widgetEditMode,
                        accent = spec.primary,
                        textColor = primaryTextColor,
                        mutedColor = mutedTextColor,
                        borderColor = cardBorder,
                        onSelect = { id ->
                            Haptics.perform(context)
                            selectedTabId = id
                            WidgetStore.setSelectedSpotlightTabId(context, id)
                        },
                        onEditTab = { tab -> editingTab = tab },
                        onAddTab = { creatingTab = true }
                    )
                }

                if (spotlightWidgetIds.isEmpty()) {
                    item(key = "spotlight_widgets_empty") {
                        Surface(
                            onClick = openWidgetPicker,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = cardColor,
                            border = BorderStroke(0.8.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Widgets,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .gradientTint(iconBrush),
                                    tint = Color.White
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Widgets de pantalla completa",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor
                                    )
                                    Text(
                                        text = "Toca para añadir widgets a esta pestaña. Usa el lápiz para redimensionar, ajustar a bordes, reordenar o crear pestañas.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mutedTextColor
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Añadir widget",
                                    tint = spec.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(spotlightWidgetIds, key = { _, id -> "spotlight_widget_$id" }) { index, widgetItemId ->
                        CleanWidgetBox(
                            itemId = widgetItemId,
                            editing = widgetEditMode,
                            accentColor = spec.primary,
                            barColor = spec.surface.copy(alpha = 0.94f),
                            barBorderColor = cardBorder,
                            allowHorizontalResize = true,
                            minHeightDp = 110,
                            maxHeightDp = 720,
                            allowEdgeToEdgeToggle = true,
                            edgeBleedDp = 16,
                            longPressToEdit = false,
                            onExitEdit = { widgetEditMode = false },
                            onExpandFullscreen = {
                                Haptics.perform(context)
                                fullscreenWidgetId = widgetItemId
                            },
                            onMoveUp = if (index > 0) {
                                {
                                    WidgetStore.moveSpotlightWidgetInTab(context, selectedTabId, widgetItemId, -1)
                                    widgetRevision++
                                }
                            } else null,
                            onMoveDown = if (index < spotlightWidgetIds.lastIndex) {
                                {
                                    WidgetStore.moveSpotlightWidgetInTab(context, selectedTabId, widgetItemId, 1)
                                    widgetRevision++
                                }
                            } else null,
                            onMoveToTab = if (widgetTabs.size > 1) {
                                { movingWidgetId = widgetItemId }
                            } else null,
                            onDelete = {
                                Haptics.perform(context)
                                WidgetStore.remove(context, widgetItemId)
                                widgetRevision++
                            }
                        )
                    }
                }
            } else {
                // 1. Calculadora instantánea estilo Spotlight
                if (mathResult != null) {
                    item(key = "spotlight_math") {
                        SpotlightCard(
                            title = "Cálculo",
                            cardColor = cardColor,
                            cardBorder = cardBorder,
                            headerColor = mutedTextColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Haptics.perform(context)
                                        ClipboardHistoryStore.copyToClipboard(context, mathResult)
                                        Toast.makeText(context, "Copiado: $mathResult", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Calculate,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .gradientTint(iconBrush),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "$query =",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = mutedTextColor
                                    )
                                    Text(
                                        text = mathResult,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copiar resultado",
                                    tint = spec.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Resultado principal destacado (Top Hit)
                if (topHit != null) {
                    item(key = "spotlight_top_hit") {
                        SpotlightCard(
                            title = "Resultado principal",
                            cardColor = cardColor,
                            cardBorder = cardBorder,
                            headerColor = mutedTextColor
                        ) {
                            SpotlightResultRow(
                                item = topHit,
                                iconSize = 44.dp,
                                textColor = primaryTextColor,
                                subtitleColor = mutedTextColor,
                                accentColor = spec.primary,
                                onClick = { onItemClick(topHit) },
                                onLongClick = {
                                    Haptics.perform(context, strong = true)
                                    actionItem = topHit
                                },
                                onFloatingClick = if (ItemLauncher.canOpenFloating(topHit)) {
                                    { onOpenFloating(topHit) }
                                } else null
                            )
                        }
                    }
                }

                // 3. Secciones agrupadas estilo iOS Spotlight
                state.sections.forEach { section ->
                    val rows = section.items.filterNot { it.id == topHit?.id }
                    if (rows.isNotEmpty()) {
                        item(key = "spotlight_sec_${section.title}") {
                            SpotlightCard(
                                title = section.title,
                                cardColor = cardColor,
                                cardBorder = cardBorder,
                                headerColor = mutedTextColor
                            ) {
                                Column {
                                    rows.take(8).forEachIndexed { index, item ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 62.dp, end = 14.dp),
                                                thickness = 0.5.dp,
                                                color = cardBorder
                                            )
                                        }
                                        SpotlightResultRow(
                                            item = item,
                                            iconSize = 36.dp,
                                            textColor = primaryTextColor,
                                            subtitleColor = mutedTextColor,
                                            accentColor = spec.primary,
                                            onClick = { onItemClick(item) },
                                            onLongClick = {
                                                Haptics.perform(context, strong = true)
                                                actionItem = item
                                            },
                                            onFloatingClick = if (ItemLauncher.canOpenFloating(item)) {
                                                { onOpenFloating(item) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Preguntar a una IA con el texto escrito
                item(key = "spotlight_ai_ask") {
                    AiAskChipsRow(
                        accent = spec.primary,
                        textColor = primaryTextColor,
                        chipColor = cardColor,
                        borderColor = cardBorder,
                        onAskGemini = {
                            if (AiLauncher.queryGemini(context, query)) onCancel?.invoke()
                        },
                        onAskChatGpt = {
                            if (AiLauncher.queryChatGpt(context, query)) onCancel?.invoke()
                        }
                    )
                }

                // 5. Búsqueda en la web / abrir URL
                item(key = "spotlight_web") {
                    SpotlightCard(
                        title = "Web",
                        cardColor = cardColor,
                        cardBorder = cardBorder,
                        headerColor = mutedTextColor
                    ) {
                        Column {
                            if (looksLikeUrl) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = launchUrl)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .gradientTint(iconBrush),
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Abrir $query",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 48.dp, end = 14.dp),
                                    thickness = 0.5.dp,
                                    color = cardBorder
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = launchWebSearch)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .gradientTint(iconBrush),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Buscar «$query» en Google",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor,
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

    if (creatingTab || editingTab != null) {
        val tab = editingTab
        val tabIndex = tab?.let { t -> widgetTabs.indexOfFirst { it.id == t.id } } ?: -1
        WidgetTabEditorDialog(
            tab = tab,
            canDelete = tab != null && widgetTabs.size > 1,
            canMoveLeft = tabIndex > 0,
            canMoveRight = tabIndex in 0 until widgetTabs.lastIndex,
            onDismiss = {
                creatingTab = false
                editingTab = null
            },
            onSave = { name, icon ->
                if (tab == null) {
                    val created = WidgetStore.addSpotlightTab(context, name, icon)
                    selectedTabId = created.id
                    WidgetStore.setSelectedSpotlightTabId(context, created.id)
                } else {
                    WidgetStore.updateSpotlightTab(context, tab.id, name, icon)
                }
                creatingTab = false
                editingTab = null
                widgetRevision++
            },
            onMove = { delta ->
                if (tab != null) {
                    WidgetStore.moveSpotlightTab(context, tab.id, delta)
                    widgetRevision++
                }
            },
            onDelete = {
                if (tab != null && WidgetStore.deleteSpotlightTab(context, tab.id)) {
                    selectedTabId = WidgetStore.selectedSpotlightTabId(context)
                }
                editingTab = null
                widgetRevision++
            }
        )
    }

    movingWidgetId?.let { widgetId ->
        MoveWidgetToTabDialog(
            tabs = widgetTabs,
            currentTabId = selectedTabId,
            onDismiss = { movingWidgetId = null },
            onPick = { targetTab ->
                WidgetStore.assignSpotlightWidgetToTab(context, widgetId, targetTab)
                movingWidgetId = null
                widgetRevision++
            }
        )
    }

    actionItem?.let { item ->
        val pinned = item.id in state.pinnedIds
        AlertDialog(
            onDismissRequest = { actionItem = null },
            title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpotlightDialogAction(
                        icon = Icons.Filled.OpenInNew,
                        label = "Abrir normal"
                    ) {
                        actionItem = null
                        onItemClick(item)
                    }
                    if (ItemLauncher.canOpenFloating(item)) {
                        SpotlightDialogAction(
                            icon = Icons.Filled.PictureInPictureAlt,
                            label = "Abrir en ventana flotante"
                        ) {
                            actionItem = null
                            onOpenFloating(item)
                        }
                    }
                    SpotlightDialogAction(
                        icon = Icons.Filled.PushPin,
                        label = if (pinned) "Quitar de fijados" else "Fijar en el panel"
                    ) {
                        actionItem = null
                        onTogglePin(item)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionItem = null }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun SpotlightCard(
    title: String,
    cardColor: Color,
    cardBorder: Color,
    headerColor: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = headerColor.copy(alpha = 0.85f),
            letterSpacing = 0.7.sp,
            modifier = Modifier.padding(start = 6.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            border = BorderStroke(0.8.dp, cardBorder)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotlightResultRow(
    item: SearchItem,
    iconSize: androidx.compose.ui.unit.Dp,
    textColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFloatingClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemIcon(item = item, size = iconSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onFloatingClick != null) {
            IconButton(
                onClick = onFloatingClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureInPictureAlt,
                    contentDescription = "Abrir en ventana flotante",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SpotlightDialogAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private class SimpleMathParser(private val clean: String) {
    var pos = 0

    fun parseExpr(): Double? {
        var acc = parseTerm() ?: return null
        while (pos < clean.length && (clean[pos] == '+' || clean[pos] == '-')) {
            val op = clean[pos++]
            val rhs = parseTerm() ?: return null
            acc = if (op == '+') acc + rhs else acc - rhs
        }
        return acc
    }

    private fun parseTerm(): Double? {
        var acc = parseFactor() ?: return null
        while (pos < clean.length && (clean[pos] == '*' || clean[pos] == '/')) {
            val op = clean[pos++]
            val rhs = parseFactor() ?: return null
            if (op == '/' && rhs == 0.0) return null
            acc = if (op == '*') acc * rhs else acc / rhs
        }
        return acc
    }

    private fun parseFactor(): Double? {
        if (pos >= clean.length) return null
        if (clean[pos] == '+') { pos++; return parseFactor() }
        if (clean[pos] == '-') { pos++; return parseFactor()?.let { -it } }
        if (clean[pos] == '(') {
            pos++
            val inner = parseExpr() ?: return null
            if (pos >= clean.length || clean[pos] != ')') return null
            pos++
            return inner
        }
        val start = pos
        while (pos < clean.length && (clean[pos].isDigit() || clean[pos] == '.')) pos++
        if (start == pos) return null
        return clean.substring(start, pos).toDoubleOrNull()
    }
}

/** Evaluador seguro de operaciones aritméticas básicas (+, -, *, /, paréntesis) para Spotlight. */
private fun evaluateSimpleMath(input: String): String? {
    val clean = input.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
    if (clean.length < 3 || clean.length > 48) return null
    if (!clean.any { it in "+-*/" }) return null
    if (!clean.all { it.isDigit() || it in "+-*/()." }) return null

    val parser = SimpleMathParser(clean)
    val value = runCatching { parser.parseExpr() }.getOrNull() ?: return null
    if (parser.pos != clean.length || value.isNaN() || value.isInfinite()) return null
    return runCatching {
        BigDecimal.valueOf(value)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }.getOrNull()
}

@Composable
private fun FullscreenSpotlightWidget(
    itemId: String,
    cardColor: Color,
    cardBorder: Color,
    primaryTextColor: Color,
    accentColor: Color,
    onExitFullscreen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val binding = remember(itemId) { WidgetStore.get(context, itemId) }
    val info = remember(binding?.id) {
        binding?.let { AppWidgetManager.getInstance(context).getAppWidgetInfo(it.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            color = cardColor,
            border = BorderStroke(0.8.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info?.loadLabel(context.packageManager)?.toString() ?: "Widget",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (binding != null && info?.configure != null) {
                    IconButton(
                        onClick = {
                            Haptics.perform(context)
                            runCatching {
                                context.startActivity(
                                    WidgetPickerActivity.reconfigureIntent(context, itemId, binding.id)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Reconfigurar widget",
                            tint = primaryTextColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                TextButton(
                    onClick = onExitFullscreen,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FullscreenExit,
                        contentDescription = "Salir de pantalla completa",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Salir",
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Eliminar widget",
                        tint = primaryTextColor.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (binding != null && info != null) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
            ) {
                val width = maxWidth.value.toInt().coerceAtLeast(160)
                val height = maxHeight.value.toInt().coerceAtLeast(160)
                AndroidView(
                    factory = { WidgetStore.host(it).createView(it, binding.id, info) },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        WidgetStore.applyWidgetSize(view, width, height)
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Widget no disponible",
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryTextColor
                )
            }
        }
    }
}

