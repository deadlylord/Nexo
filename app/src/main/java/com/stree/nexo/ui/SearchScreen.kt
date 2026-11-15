package com.stree.nexo.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.SolidColor
import com.stree.nexo.data.PanelPage
import kotlin.math.abs
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.stree.nexo.R
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.ContactShortcuts
import com.stree.nexo.data.ContactPhotos
import com.stree.nexo.WidgetPickerActivity
import com.stree.nexo.widget.WidgetStore
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.SearchItem
import com.stree.nexo.data.SearchUiState
import com.stree.nexo.data.ResultSection
import com.stree.nexo.data.PlayStore
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.search.WhatsAppLinks
import java.text.Collator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    autoFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val fieldFocusRequester = focusRequester ?: remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current

    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect

        // The panel can open before its window is focused; asking for the
        // keyboard too early makes Android silently ignore the request.
        var waited = 0
        while (!view.hasWindowFocus() && waited < 2_000) {
            delay(50)
            waited += 50
        }

        delay(150)
        runCatching { fieldFocusRequester.requestFocus() }

        val controller = context.findActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }

        // Window transitions cancel the first IME request, so retry until the
        // keyboard is actually visible.
        var attempts = 0
        while (attempts < 10 && !view.isImeVisible()) {
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.ime())
            } else {
                keyboardController?.show()
            }
            delay(150)
            attempts++
        }
    }

    if (compact) {
        Column(modifier.fillMaxWidth()) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                    .focusRequester(fieldFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (event.type == KeyEventType.KeyUp) onSubmit()
                            true
                        } else false
                    },
                decorationBox = { field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Search, "Buscar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp)
                        )
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(
                                "Buscar…", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            field()
                        }
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier.size(32.dp).clickable { onQueryChange("") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, "Borrar búsqueda", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        return
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    if (event.type == KeyEventType.KeyUp) onSubmit()
                    true
                } else false
            }
            .focusRequester(fieldFocusRequester),
        placeholder = {
            Text(
                text = "Buscar apps, atajos…",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onItemClick: (SearchItem) -> Unit,
    onTogglePin: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onRequestContacts: () -> Unit,
    onAddShortcut: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    header: (@Composable () -> Unit)? = null
) {
    var actionItem by remember { mutableStateOf<SearchItem?>(null) }
    val context = LocalContext.current

    Column(modifier = modifier.imePadding()) {
        header?.invoke()

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onSubmit = { submitSearch(context, state, onItemClick) },
            autoFocus = autoFocus,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(6.dp))

        SearchBody(
            state = state,
            onItemClick = onItemClick,
            onOpenFloating = onOpenFloating,
            onItemLongClick = { actionItem = it },
            onRequestContacts = onRequestContacts,
            modifier = Modifier.weight(1f)
        )
    }

    ItemActionsSheet(
        item = actionItem,
        pinnedIds = state.pinnedIds,
        onAddShortcut = onAddShortcut,
        onDismiss = { actionItem = null },
        onOpen = { item ->
            actionItem = null
            onItemClick(item)
        },
        onOpenFloating = { item ->
            actionItem = null
            onOpenFloating(item)
        },
        onTogglePin = { item ->
            actionItem = null
            onTogglePin(item)
        }
    )
}

@Composable
fun SearchBody(
    state: SearchUiState,
    onItemClick: (SearchItem) -> Unit,
    onCallContact: ((SearchItem) -> Unit)? = null,
    onOpenFloating: (SearchItem) -> Unit,
    onItemLongClick: (SearchItem) -> Unit,
    onRequestContacts: () -> Unit,
    modifier: Modifier = Modifier,
    showPinHint: Boolean = false,
    bottomAnchored: Boolean = false,
    resultSide: PanelSide = PanelSide.START,
    emptyLabel: String = "Sin resultados",
    showShortcutsNote: Boolean = true,
    showSectionHeaders: Boolean = true,
    showTileLabels: Boolean = true,
    onEmptySpaceClick: (() -> Unit)? = null,
    onMovePinned: ((String, String) -> Unit)? = null,
    gridColumns: Int = 5,
    tileIconScale: Float = 1f,
    tileSpacingDp: Int = 0,
    onAddSlot: ((Int) -> Unit)? = null,
    onWidgetGesture: ((SearchItem) -> Unit)? = null,
    onMovePinnedToSlot: ((String, Int) -> Unit)? = null,
    centerItems: Boolean = false,
    bottomContentPaddingDp: Int = 20
) {
    when {
        state.loading && state.allItems.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            Box(
                modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        else -> {
            val showContactsHint = showSectionHeaders && state.query.isBlank() && !state.contactsPermission &&
                state.allItems.isNotEmpty()

            val gridState = rememberLazyGridState()
            val context = LocalContext.current

            var showEmptySlots by remember { mutableStateOf(false) }
            val sections = if (showSectionHeaders) state.sections else {
                val unified = state.sections.flatMap { it.items }.distinctBy { it.id }
                if (onAddSlot != null && state.query.isBlank()) {
                    val pinned = unified.filter { it.id in state.pinnedIds }
                    val slots = pinned.associateBy { state.pinnedSlots[it.id] ?: pinned.indexOf(it) }
                    val suggestions = ArrayDeque(unified.filterNot { it.id in state.pinnedIds })
                    val end = maxOf((slots.keys.maxOrNull() ?: -1) + 1,
                        if (showEmptySlots) gridColumns * 6 else 0)
                    val cells = (0 until end).mapNotNull { position ->
                        slots[position] ?: (if (!showEmptySlots) suggestions.removeFirstOrNull() else null)
                            ?: if (showEmptySlots) SearchItem("empty-slot:$position", "Añadir", "", ItemType.CUSTOM) else null
                    }
                    listOf(ResultSection("", cells + suggestions.toList()))
                } else if (unified.isEmpty()) emptyList() else listOf(ResultSection("", unified))
            }
            var draggedId by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableStateOf(Offset.Zero) }
            var dropTargetId by remember { mutableStateOf<String?>(null) }
            val cellBounds = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
            val displaySlots = sections.flatMap { it.items }.withIndex().associate { it.value.id to it.index }
            val currentDisplaySlots by rememberUpdatedState(displaySlots)
            val gestureScope = rememberCoroutineScope()
            val itemKeys = sections.flatMap { section ->
                section.items.map { item -> "result:${section.title}:${item.id}" to item.id }
            }.toMap()
            val currentItemKeys by rememberUpdatedState(itemKeys)

            // Al anclar/quitar elementos la lista cambia de secciones: vuelve arriba
            // para que "Ancladas" quede visible.
            LaunchedEffect(state.pinnedIds) {
                gridState.scrollToItem(0)
            }
            LaunchedEffect(state.query) {
                gridState.scrollToItem(0)
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides if (resultSide == PanelSide.END) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns.coerceIn(3, 6)),
                reverseLayout = bottomAnchored,
                modifier = modifier
                    .fillMaxSize()
                    .then(
                        if (onEmptySpaceClick != null) {
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = {},
                                onLongClick = { showEmptySlots = !showEmptySlots; Haptics.perform(context) }
                            )
                        } else Modifier
                    ),
                state = gridState,
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = bottomContentPaddingDp.dp),
                horizontalArrangement = Arrangement.spacedBy(tileSpacingDp.dp),
                verticalArrangement = Arrangement.spacedBy(
                    tileSpacingDp.dp, if (centerItems) Alignment.CenterVertically else if (bottomAnchored) Alignment.Bottom else Alignment.Top
                )
            ) {
                if (showPinHint && state.query.isBlank() &&
                    state.pinnedIds.isEmpty() && state.allItems.isNotEmpty()
                ) {
                    item(
                        key = "pin_hint",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        PinHint()
                    }
                }

                if (showContactsHint) {
                    item(
                        key = "contacts_hint",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        ContactsHint(onRequestContacts)
                    }
                }

                sections.forEach { section ->
                    if (showSectionHeaders && !bottomAnchored) {
                        item(key = "section_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(section.title, resultSide)
                        }
                    }
                    items(
                        items = section.items,
                        key = { "result:${section.title}:${it.id}" },
                        span = { item ->
                            if (item.showsAsTile() ||
                                (item.type == ItemType.CONTACT && item.id in state.pinnedIds)
                            ) {
                                GridItemSpan(1)
                            } else {
                                GridItemSpan(maxLineSpan)
                            }
                        }
                    ) { item ->
                        if (item.id.startsWith("empty-slot:")) {
                            Box(
                                Modifier.fillMaxWidth().height(if (showTileLabels) 62.dp else 48.dp)
                                    .onGloballyPositioned { cellBounds[item.id] = it.boundsInRoot() }
                                    .background(if (dropTargetId == item.id) MaterialTheme.colorScheme.primary.copy(alpha = .3f) else Color.Transparent)
                                    .combinedClickable(onClick = {
                                        if (showEmptySlots) onAddSlot?.invoke(item.id.substringAfter(':').toInt())
                                    }, onLongClick = { showEmptySlots = !showEmptySlots; Haptics.perform(context) }),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showEmptySlots) Icon(Icons.Filled.Add, "Añadir en este espacio",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f),
                                    modifier = Modifier.size(32.dp))
                            }
                        } else {
                        val draggable = state.query.isBlank() &&
                            item.id in state.pinnedIds && onMovePinned != null
                        val pinnedContact = item.type == ItemType.CONTACT &&
                            item.id in state.pinnedIds
                        val widgetBinding = WidgetStore.get(context, item.id)
                        // Evita abrir la app al soltar un arrastre o el menú.
                        var suppressClickUntil by remember(item.id) { mutableStateOf(0L) }
                        val open = {
                            if (System.currentTimeMillis() >= suppressClickUntil) onItemClick(item)
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { cellBounds[item.id] = it.boundsInRoot() }
                                .zIndex(if (draggedId == item.id) 1f else 0f)
                                .then(if (widgetBinding != null && onWidgetGesture != null) {
                                    Modifier.pointerInput(item.id, widgetBinding.id) {
                                        var movement = 0f
                                        detectVerticalDragGestures(onDragStart = { movement = 0f },
                                            onVerticalDrag = { change, amount ->
                                                movement += amount
                                                change.consume()
                                            }, onDragEnd = {
                                                if (movement < -36.dp.toPx()) {
                                                    onWidgetGesture(item)
                                                }
                                            })
                                    }
                                } else Modifier)
                                .then(
                                    if (draggable) {
                                        Modifier.pointerInput(item.id) {
                                            var pointer = Offset.Zero
                                            var moved = false
                                            var menuOpened = false
                                            var menuJob: kotlinx.coroutines.Job? = null
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { localStart ->
                                                    draggedId = item.id
                                                    dragOffset = Offset.Zero
                                                    dropTargetId = null
                                                    suppressClickUntil = Long.MAX_VALUE
                                                    Haptics.perform(context, strong = true)
                                                    pointer = (cellBounds[item.id]?.topLeft ?: Offset.Zero) + localStart
                                                    moved = false
                                                    menuOpened = false
                                                    menuJob = gestureScope.launch {
                                                        // La primera pausa prepara el arrastre; mantener
                                                        // inmóvil un poco más abre el menú sin soltar.
                                                        delay(600)
                                                        if (!moved && draggedId == item.id) {
                                                            menuOpened = true
                                                            draggedId = null
                                                            dragOffset = Offset.Zero
                                                            dropTargetId = null
                                                            onItemLongClick(item)
                                                        }
                                                    }
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    if (!menuOpened) {
                                                        pointer = (cellBounds[item.id]?.topLeft ?: Offset.Zero) + change.position
                                                        dragOffset += amount
                                                        if (dragOffset.getDistance() > viewConfiguration.touchSlop) {
                                                            moved = true
                                                            menuJob?.cancel()
                                                        }
                                                        val visibleIds = gridState.layoutInfo.visibleItemsInfo.mapNotNull { currentItemKeys[it.key] }.toSet()
                                                        dropTargetId = cellBounds.entries.firstOrNull { (id, bounds) ->
                                                            id != item.id && id in visibleIds &&
                                                                (onMovePinnedToSlot != null || id in state.pinnedIds) &&
                                                                bounds.contains(pointer)
                                                        }?.key
                                                    }
                                                },
                                                onDragEnd = {
                                                    menuJob?.cancel()
                                                    if (!menuOpened) {
                                                        if (moved) {
                                                            dropTargetId?.let { target ->
                                                                val slot = target.removePrefix("empty-slot:").toIntOrNull()
                                                                    ?: currentDisplaySlots[target]
                                                                if (target in state.pinnedIds) onMovePinned?.invoke(item.id, target)
                                                                else if (onMovePinnedToSlot != null && slot != null) onMovePinnedToSlot(item.id, slot)
                                                                else onMovePinned?.invoke(item.id, target)
                                                            }
                                                        } else {
                                                            onItemLongClick(item)
                                                        }
                                                    }
                                                    draggedId = null
                                                    dragOffset = Offset.Zero
                                                    dropTargetId = null
                                                    suppressClickUntil = System.currentTimeMillis() + 400
                                                },
                                                onDragCancel = {
                                                    menuJob?.cancel()
                                                    draggedId = null
                                                    dragOffset = Offset.Zero
                                                    dropTargetId = null
                                                    suppressClickUntil = System.currentTimeMillis() + 400
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                        Box(Modifier.fillMaxWidth().graphicsLayer {
                                    if (draggedId == item.id) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.12f
                                        scaleY = 1.12f
                                        shadowElevation = 12.dp.toPx()
                                    }
                                }
                                .background(
                                    if (dropTargetId == item.id) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    } else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                        if (item.type == ItemType.CONTACT && !pinnedContact) {
                            ContactResultRow(
                                item = item,
                                pinned = item.id in state.pinnedIds,
                                onCall = { (onCallContact ?: onItemClick)(item) },
                                onOpenWhatsApp = onItemClick,
                                onActions = { onItemLongClick(item) }
                            )
                        } else if (pinnedContact || item.showsAsTile()) {
                            ResultTile(
                                item = item,
                                pinned = item.id in state.pinnedIds,
                                showLabel = showTileLabels,
                                iconScale = tileIconScale,
                                onClick = open,
                                onDoubleClick = if (ItemLauncher.canOpenFloating(item)
                                ) {
                                    { onOpenFloating(item) }
                                } else null,
                                onLongClick = if (draggable) {
                                    null
                                } else {
                                    { onItemLongClick(item) }
                                }
                            )
                        } else {
                            ResultRow(
                                item = item,
                                pinned = item.id in state.pinnedIds,
                                onClick = open,
                                onDoubleClick = if (ItemLauncher.canOpenFloating(item)
                                ) {
                                    { onOpenFloating(item) }
                                } else null,
                                onLongClick = if (draggable) {
                                    null
                                } else {
                                    { onItemLongClick(item) }
                                }
                            )
                         }
                         }
                         }
                         }
                        }
                    }
                    if (showSectionHeaders && bottomAnchored) {
                        item(key = "section_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(section.title, resultSide)
                        }
                    }
                }

                if (sections.isEmpty() && !state.loading) {
                    item(
                        key = "empty",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                emptyLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (state.shortcutsBlocked && showShortcutsNote) {
                    item(
                        key = "shortcuts_note",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        NoteCard(
                            "Los atajos de otras apps necesitan que Nexo sea el lanzador predeterminado. Puedes crear tus propios atajos en la pestaña Atajos."
                        )
                    }
                }
            }
            }
        }
    }
}

private fun SearchUiState.firstMatch(): SearchItem? =
    if (query.isBlank()) null else sections.firstOrNull { it.items.isNotEmpty() }
        ?.items?.firstOrNull { !it.id.startsWith("web:") && !it.id.startsWith("store:") }

/**
 * Enter o Buscar: abre la primera coincidencia real del teléfono. Si no hay
 * ninguna, busca el texto directamente en Play Store.
 */
private fun submitSearch(context: Context, state: SearchUiState, onItemClick: (SearchItem) -> Unit) {
    val match = state.firstMatch()
    if (match != null) {
        onItemClick(match)
        return
    }
    if (state.query.isNotBlank()) PlayStore.open(context, state.query)
}

private fun SearchItem.showsAsTile(): Boolean = when (type) {
    ItemType.APP, ItemType.SHORTCUT, ItemType.SETTING -> true
    ItemType.CONTACT -> false
    // La búsqueda web y la de tienda se muestran como fila completa.
    ItemType.CUSTOM -> !id.startsWith("web:") && !id.startsWith("store:")
}

/** Atajos que representan a una persona (llamada o chat de WhatsApp). */
private fun SearchItem.isPersonShortcut(): Boolean =
    subtitle.contains("wa.me", ignoreCase = true) ||
        intent?.data?.host.equals("wa.me", ignoreCase = true) ||
        intent?.data?.scheme.equals("whatsapp", ignoreCase = true)

@Composable
private fun SectionHeader(title: String, side: PanelSide = PanelSide.START) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        textAlign = if (side == PanelSide.END) TextAlign.Right else TextAlign.Left,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactResultRow(
    item: SearchItem,
    pinned: Boolean,
    onCall: () -> Unit,
    onOpenWhatsApp: (SearchItem) -> Unit,
    onActions: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // El área de texto conserva las acciones, pero ya no envuelve los
        // botones: así el toque de llamada no lo intercepta la fila padre.
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onActions, onLongClick = onActions),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.clip(CircleShape)) { ItemIcon(item, size = 40.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.phoneNumber.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (pinned) Icon(Icons.Filled.Star, "Anclado", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp))
        }
        IconButton(onClick = {
            // El país se infiere del teléfono: se abre el chat directamente,
            // sin diálogo, sin pedir el número y sin mensaje preparado.
            val remembered = Prefs.contactWhatsAppLink(context, item.id)
            val chatItem = if (remembered != null) {
                SearchItem(
                    id = "whatsapp:${item.id}",
                    title = "WhatsApp · ${item.title}",
                    subtitle = item.phoneNumber.orEmpty(),
                    type = ItemType.CUSTOM,
                    intent = Intent(Intent.ACTION_VIEW, Uri.parse(remembered))
                )
            } else {
                WhatsAppLinks.directChatItem(context, item)
            }
            if (chatItem != null) {
                onOpenWhatsApp(chatItem)
            } else {
                Toast.makeText(
                    context,
                    "No se pudo interpretar el número de ${item.title}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF128C46)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_whatsapp_chat),
                    "WhatsApp a ${item.title}", tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }
        IconButton(onClick = onCall) {
            Icon(painterResource(R.drawable.ic_phone), "Llamar a ${item.title}",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultRow(
    item: SearchItem,
    pinned: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemIcon(item)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.type == ItemType.APP) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (pinned) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Fijado",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else if (item.type == ItemType.SHORTCUT) {
            Text(
                text = "Atajo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultTile(
    item: SearchItem,
    pinned: Boolean,
    showLabel: Boolean,
    iconScale: Float = 1f,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (showLabel) 66.dp else 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints {
            val scale = iconScale.coerceIn(0.75f, 1.35f)
            // La base depende del ancho real de la celda: con menos columnas
            // los iconos crecen y con más columnas se reducen.
            val base = (maxWidth * 0.58f).coerceIn(26.dp, 50.dp)
            val iconSize = (base * scale).coerceAtMost((maxWidth * 0.80f).coerceAtLeast(22.dp))
            ItemIcon(item, size = iconSize)

            if (pinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Anclada",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (item.type == ItemType.CONTACT || item.isPersonShortcut()) {
                val whatsapp = item.isPersonShortcut()
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp)
                        .size(17.dp).clip(CircleShape)
                        .background(if (whatsapp) Color(0xFF128C46) else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(if (whatsapp) R.drawable.ic_whatsapp_chat else R.drawable.ic_phone),
                        if (whatsapp) "WhatsApp" else "Teléfono",
                        tint = if (whatsapp) Color.White else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = 13.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ItemIcon(item: SearchItem, size: Dp = 38.dp) {
    val shape = if (item.type == ItemType.CONTACT || item.isPersonShortcut()) CircleShape else RoundedCornerShape(11.dp)
    val context = LocalContext.current
    val animate = remember { Prefs.animatePanelIcons(context) }
    var entered by remember(item.id) { mutableStateOf(!animate) }
    LaunchedEffect(item.id) { entered = true }
    val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(150), label = "iconEntrance")
    val photo by produceState<android.graphics.Bitmap?>(null, item.photoUri) {
        value = item.photoUri?.let { uri -> withContext(Dispatchers.IO) { ContactPhotos.load(context, uri) } }
    }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                alpha = .5f + .5f * entrance
                scaleX = .9f + .1f * entrance
                scaleY = scaleX
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        val drawable = item.icon

        if (photo != null) {
            Image(photo!!.asImageBitmap(), item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (drawable != null) {
            val bitmap = remember(item.id, drawable) {
                runCatching { drawable.toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                FallbackIcon(item)
            }
        } else {
            FallbackIcon(item)
        }
    }
}

@Composable
private fun FallbackIcon(item: SearchItem) {
    val icon = if (item.isPersonShortcut()) {
        Icons.Filled.Person
    } else if (item.id.startsWith("store:") && item.type == ItemType.CUSTOM) {
        Icons.Filled.ShoppingCart
    } else if (item.id.startsWith("web:") && item.type == ItemType.CUSTOM) {
        Icons.Filled.Search
    } else when (item.type) {
        ItemType.APP -> Icons.Filled.Search
        ItemType.SHORTCUT -> Icons.Filled.Star
        ItemType.CONTACT -> Icons.Filled.Person
        ItemType.SETTING -> quickSettingIcon(item.title)
        ItemType.CUSTOM -> Icons.Filled.Star
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun ContactsHint(onRequestContacts: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(onClick = onRequestContacts),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Buscar en tus contactos",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Toca para dar permiso y llamar sin salir de la búsqueda",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
fun SearchPanel(
    state: SearchUiState,
    side: PanelSide,
    onQueryChange: (String) -> Unit,
    onItemClick: (SearchItem) -> Unit,
    onCallContact: (SearchItem) -> Unit,
    onTogglePin: (SearchItem) -> Unit,
    onMovePinned: (String, String) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onRequestContacts: () -> Unit,
    onAddShortcut: (String, String) -> Unit,
    onAddPinnedShortcut: (String, String) -> Unit,
    onAddPinnedAtSlot: (String, String, Int) -> Unit,
    onAssignSlot: (String, Int) -> Unit,
    onExpand: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val panelColumns = remember { Prefs.panelColumns(context) }
    val panelIconScale = remember { Prefs.panelIconScale(context) }
    val panelSpacing = remember { Prefs.panelItemSpacing(context) }
    val panelShowLabels = remember { Prefs.panelShowLabels(context) }
    val pages = remember { Prefs.visiblePanelPages(context) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    BackHandler {
        when {
            state.query.isNotBlank() -> onQueryChange("")
            pagerState.currentPage != 0 -> scope.launch {
                pagerState.animateScrollToPage(0)
            }
            else -> onClose()
        }
    }

    var shown by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<SearchItem?>(null) }
    var addKind by remember { mutableStateOf<PanelAddKind?>(null) }
    var addingSlot by remember { mutableStateOf<Int?>(null) }
    var openFolder by remember { mutableStateOf<SearchItem?>(null) }
    val launchPanelItem: (SearchItem) -> Unit = { item ->
        if (item.intent?.data?.scheme == "nexo-folder") openFolder = item
        else onItemClick(item)
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var quickExpanded by remember { mutableStateOf(Prefs.quickDrawerExpanded(context)) }
    val storeItem = remember(context) { PlayStore.homeItem(context) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(pagerState.currentPage) {
        if (pages[pagerState.currentPage] != PanelPage.MAIN) keyboard?.hide()
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    // El velo entra con el panel: antes aparecía negro de golpe y luego el
    // panel, lo que se percibía como una pantalla negra lenta.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 0.18f else 0f,
        animationSpec = tween(130),
        label = "scrim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onClose
            )
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = slideInHorizontally(
                animationSpec = tween(130),
                initialOffsetX = { if (side == PanelSide.END) it else -it }
            ) + fadeIn(tween(90)),
            exit = slideOutHorizontally(
                animationSpec = tween(110),
                targetOffsetX = { if (side == PanelSide.END) it else -it }
            ) + fadeOut(tween(80)),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (side == PanelSide.END) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
            ) {
                val panelWidth = minOf(maxWidth * 0.58f, 372.dp)
                val panelHeight = Prefs.panelHeight(LocalContext.current)
                val density = LocalDensity.current
                val keyboardHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
                // Solo cambia el espacio útil interior; el fondo del panel
                // mantiene siempre su altura y posición originales.
                val bottomMargin = maxHeight * ((1f - panelHeight) / 2f)
                val keyboardOverlap = (keyboardHeight - bottomMargin).coerceAtLeast(0.dp)
                val drawerLimit = (maxHeight * panelHeight - keyboardOverlap - 129.dp).coerceIn(0.dp, 180.dp)

                PanelSurface(
                    modifier = Modifier
                        .fillMaxHeight(panelHeight)
                        .width(panelWidth)
                        .padding(horizontal = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = keyboardOverlap)
                            .padding(horizontal = 6.dp)
                    ) {
                        SearchField(
                            query = state.query, onQueryChange = onQueryChange,
                            onSubmit = { submitSearch(context, state, launchPanelItem) },
                            focusRequester = searchFocus, autoFocus = true, compact = true,
                            modifier = Modifier.height(1.dp).graphicsLayer { alpha = 0f }
                        )

                        if (state.query.isNotBlank()) SearchBody(
                            state = state, onItemClick = launchPanelItem, onCallContact = onCallContact,
                            onOpenFloating = onOpenFloating, onItemLongClick = { actionItem = it },
                            onRequestContacts = onRequestContacts, modifier = Modifier.weight(1f),
                            showSectionHeaders = false, showShortcutsNote = false,
                            showTileLabels = panelShowLabels, gridColumns = panelColumns,
                            resultSide = side, bottomAnchored = false, tileIconScale = panelIconScale
                        ) else HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            pageSpacing = 12.dp
                        ) { page ->
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    val distance = abs(
                                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    ).coerceIn(0f, 1f)
                                    alpha = 1f - distance * 0.45f
                                    scaleX = 1f - distance * 0.06f
                                    scaleY = 1f - distance * 0.06f
                                    shape = RoundedCornerShape(22.dp)
                                    clip = true
                                }
                            ) {
                            when (pages[page]) {
                                PanelPage.MAIN -> {
                                    SearchPage(
                                        state = state,
                                        side = side,
                                        onItemClick = launchPanelItem,
                                        onCallContact = onCallContact,
                                        onOpenFloating = onOpenFloating,
                                        onItemLongClick = { actionItem = it },
                                        onRequestContacts = onRequestContacts,
                                        onAddRequest = { addKind = PanelAddKind.MENU },
                                        onAddSlot = { addingSlot = it; addKind = PanelAddKind.MENU },
                                        onMovePinned = onMovePinned,
                                        onMovePinnedToSlot = onAssignSlot,
                                        columns = panelColumns,
                                        iconScale = panelIconScale,
                                        spacingDp = panelSpacing,
                                        showLabels = panelShowLabels,
                                        onWidgetGesture = { item ->
                                            EdgePanelService.showWidget(context, item.id)
                                            onClose()
                                        }
                                    )
                                }

                                PanelPage.SETTINGS -> {
                                    QuickSettingsPage(
                                        state = state,
                                        onItemClick = launchPanelItem,
                                        onOpenSettings = onOpenSettings
                                    )
                                }

                                PanelPage.APPS -> {
                                    AllAppsPage(
                                        state = state,
                                        onItemClick = launchPanelItem,
                                        onOpenFloating = onOpenFloating,
                                        onItemLongClick = { actionItem = it }
                                    )
                                }

                                PanelPage.CONTACTS -> {
                                    ContactsPage(
                                        state = state,
                                        onContactActions = { actionItem = it },
                                        onRequestContacts = onRequestContacts,
                                        onCall = onCallContact,
                                        onOpenWhatsApp = launchPanelItem
                                    )
                                }
                                PanelPage.WIDGETS -> WidgetsPage(active = pagerState.currentPage == page)
                            }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().height(32.dp).clickable {
                                quickExpanded = !quickExpanded
                                Prefs.setQuickDrawerExpanded(context, quickExpanded)
                            }, verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(if (quickExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                if (quickExpanded) "Contraer accesos y reproductor" else "Expandir accesos y reproductor",
                                Modifier.size(18.dp))
                            Text("Accesos y música", style = MaterialTheme.typography.labelSmall)
                        }
                        AnimatedVisibility(visible = quickExpanded) {
                            Column(Modifier.fillMaxWidth().heightIn(max = drawerLimit)
                                .verticalScroll(rememberScrollState())) {
                                val system = state.allItems.filter { it.type == ItemType.SETTING }.take(panelColumns)
                                if (system.isNotEmpty()) SearchBody(
                                    state = state.copy(query = "", sections = listOf(ResultSection("Ajustes", system))),
                                    onItemClick = launchPanelItem, onOpenFloating = onOpenFloating,
                                    onItemLongClick = { actionItem = it }, onRequestContacts = onRequestContacts,
                                    modifier = Modifier.fillMaxWidth().height(if (panelShowLabels) 66.dp else 48.dp),
                                    showSectionHeaders = false, showShortcutsNote = false, showTileLabels = panelShowLabels,
                                    gridColumns = panelColumns, tileIconScale = panelIconScale, bottomContentPaddingDp = 0
                                )
                                MiniPlayer(Modifier.fillMaxWidth())
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { launchPanelItem(storeItem) }, modifier = Modifier.weight(1f)) {
                                ItemIcon(storeItem, 20.dp)
                            }
                            IconButton(onClick = onExpand, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.OpenInFull, "Abrir Nexo completo", Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                if (view.isImeVisible()) {
                                    keyboard?.hide()
                                    focusManager.clearFocus(force = true)
                                } else scope.launch {
                                    val main = pages.indexOf(PanelPage.MAIN)
                                    if (main >= 0) pagerState.scrollToPage(main)
                                    searchFocus.requestFocus()
                                    delay(120)
                                    keyboard?.show()
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(painterResource(R.drawable.ic_keyboard),
                                    if (keyboardHeight > 0.dp) "Ocultar teclado" else "Mostrar teclado", Modifier.size(20.dp))
                            }
                            IconButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Settings, "Abrir ajustes de Nexo", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    ItemActionsSheet(
        item = actionItem,
        pinnedIds = state.pinnedIds,
        onAddShortcut = onAddPinnedShortcut,
        onDismiss = { actionItem = null },
        onOpen = { item ->
            actionItem = null
            launchPanelItem(item)
        },
        onOpenFloating = { item ->
            actionItem = null
            onOpenFloating(item)
        },
        onTogglePin = { item ->
            actionItem = null
            onTogglePin(item)
        }
    )

    PanelAddDialogs(
        kind = addKind,
        state = state,
        onKindChange = { addKind = it },
        onPinItem = { item ->
            val slot = addingSlot
            if (slot != null) onAssignSlot(item.id, slot)
            else if (item.id !in state.pinnedIds) onTogglePin(item)
            addingSlot = null
            addKind = null
        },
        onAddPinned = { label, target ->
            val slot = addingSlot
            if (slot != null) onAddPinnedAtSlot(label, target, slot) else onAddPinnedShortcut(label, target)
            addingSlot = null
            addKind = null
        }
    )
    openFolder?.let { folder ->
        val packages = folder.intent?.data?.getQueryParameters("app").orEmpty()
        val appsByPackage = remember(state.allItems) {
            state.allItems.filter { it.type == ItemType.APP }.associateBy { it.packageName }
        }
        val apps = packages.mapNotNull { appsByPackage[it] }
        AlertDialog(
            onDismissRequest = { openFolder = null },
            title = { Text(folder.title) },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(300.dp)) {
                    items(apps, key = { it.id }) { app ->
                        ResultTile(app, false, true, onClick = {
                            openFolder = null
                            onItemClick(app)
                        }, onDoubleClick = null, onLongClick = null)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openFolder = null }) { Text("Cerrar") } }
        )
    }
}

private data class QuickEntry(
    val label: String,
    val icon: ImageVector,
    val item: SearchItem?
)

@Composable
private fun PanelTabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "Buscar" to Icons.Filled.Search,
        "Ajustes" to Icons.Filled.Tune,
        "Apps" to Icons.Filled.Apps,
        "Contactos" to Icons.Filled.Person
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val active = index == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchPage(
    state: SearchUiState,
    side: PanelSide,
    onItemClick: (SearchItem) -> Unit,
    onCallContact: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onItemLongClick: (SearchItem) -> Unit,
    onRequestContacts: () -> Unit,
    onAddRequest: () -> Unit,
    onAddSlot: (Int) -> Unit,
    onMovePinned: (String, String) -> Unit,
    columns: Int,
    iconScale: Float,
    spacingDp: Int,
    showLabels: Boolean,
    onWidgetGesture: (SearchItem) -> Unit,
    onMovePinnedToSlot: (String, Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {

        if (state.query.isBlank() && !state.loading && state.error == null) {
            val pinned = state.sections.flatMap { it.items }.filter {
                it.id in state.pinnedIds && it.type != ItemType.SETTING
            }.distinctBy { it.id }
            val recent = state.sections.firstOrNull { it.title == "Recientes" }?.items.orEmpty()
            val frequent = state.sections.firstOrNull { it.title == "Frecuentes" }?.items.orEmpty()
            val ranked = buildList {
                for (index in 0 until columns) {
                    recent.getOrNull(index)?.let { add(it) }
                    frequent.getOrNull(index)?.let { add(it) }
                }
                addAll(state.sections.filter { it.title == "Para esta hora" || it.title == "Aplicaciones" }.flatMap { it.items })
            }.filter { it.type == ItemType.APP && it.id !in state.pinnedIds }
                .distinctBy { it.id }.take(columns * 2)
            val rowHeight = if (showLabels) 66 else 48
            BoxWithConstraints(Modifier.weight(1f)) {
            val availableHeight = maxHeight
            val suggestedRows = if (availableHeight < 170.dp) 0 else if (availableHeight < 300.dp) 1 else 2
            val suggestions = ranked.take(columns * suggestedRows)
            val occupiedRows = (suggestions.size + columns - 1) / columns
            val sectionDecoration = 0
            Column(Modifier.fillMaxSize()) {
                if (suggestions.isNotEmpty()) {
                PanelSection("Sugerencias", Modifier.fillMaxWidth()
                    .height((rowHeight * occupiedRows + spacingDp * (occupiedRows - 1) + sectionDecoration).dp)) {
                SearchBody(
                    state = state.copy(sections = listOf(ResultSection("", suggestions))),
                    onItemClick = onItemClick, onOpenFloating = onOpenFloating,
                    onItemLongClick = onItemLongClick, onRequestContacts = onRequestContacts,
                    modifier = Modifier.fillMaxSize(), bottomContentPaddingDp = 0,
                    showSectionHeaders = false, showShortcutsNote = false, showTileLabels = showLabels,
                    gridColumns = columns, tileIconScale = iconScale, tileSpacingDp = spacingDp
                )
                }
                Spacer(Modifier.height(6.dp))
                }
                PanelSection("Favoritos", Modifier.weight(1f)) {
                SearchBody(
                    state = state.copy(sections = listOf(ResultSection("Ancladas", pinned))),
                    onItemClick = onItemClick, onCallContact = onCallContact,
                    onOpenFloating = onOpenFloating, onItemLongClick = onItemLongClick,
                    onRequestContacts = onRequestContacts, modifier = Modifier.fillMaxSize(), bottomContentPaddingDp = 0,
                    showSectionHeaders = false, showShortcutsNote = false, showTileLabels = showLabels,
                    resultSide = side, centerItems = true, onEmptySpaceClick = onAddRequest,
                    onMovePinned = onMovePinned, onMovePinnedToSlot = onMovePinnedToSlot,
                    onAddSlot = onAddSlot, gridColumns = columns,
                    tileIconScale = iconScale, tileSpacingDp = spacingDp, onWidgetGesture = onWidgetGesture
                )
                }
            }
            }
        } else SearchBody(
            state = state,
            onItemClick = onItemClick,
            onCallContact = onCallContact,
            onOpenFloating = onOpenFloating,
            onItemLongClick = onItemLongClick,
            onRequestContacts = onRequestContacts,
            modifier = Modifier.weight(1f),
            showPinHint = false,
            bottomAnchored = false,
            resultSide = side,
            emptyLabel = "Busca o desliza para ver ajustes y apps.",
            showShortcutsNote = false,
            showSectionHeaders = false,
            showTileLabels = showLabels,
            onEmptySpaceClick = onAddRequest,
            onMovePinned = onMovePinned,
            onMovePinnedToSlot = onMovePinnedToSlot,
            gridColumns = columns,
            tileIconScale = iconScale,
            tileSpacingDp = spacingDp,
            onAddSlot = onAddSlot,
            onWidgetGesture = onWidgetGesture
        )

    }
}

@Composable
private fun QuickSettingsPage(
    state: SearchUiState,
    onItemClick: (SearchItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings = state.allItems.filter { it.type == ItemType.SETTING }
    val preferredOrder = listOf(
        "Batería", "Bluetooth", "Seguridad", "Wi-Fi", "Almacenamiento",
        "Aplicaciones", "Datos móviles", "Pantalla", "Sonido", "Ubicación",
        "Idioma", "Fecha y hora", "Accesibilidad", "NFC", "VPN",
        "Pantalla de inicio", "Opciones de desarrollador", "Ajustes"
    )
    val ordered = preferredOrder.mapNotNull { title ->
        settings.firstOrNull { it.title == title }
    }

    val entries = buildList {
        ordered.forEachIndexed { index, item ->
            add(
                QuickEntry(
                    label = if (item.title == "Batería") "Ahorro" else item.title,
                    icon = quickSettingIcon(item.title),
                    item = item
                )
            )
            if (index == 4) {
                add(QuickEntry("Perfil", Icons.Filled.Person, null))
            }
        }
        if (ordered.size <= 4) {
            add(QuickEntry("Perfil", Icons.Filled.Person, null))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Ajustes rápidos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)
        )
        Text(
            text = "Accesos directos del sistema",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )

        if (state.loading && entries.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.label }) { entry ->
                    QuickSettingTile(entry) {
                        if (entry.item != null) {
                            onItemClick(entry.item)
                        } else {
                            onOpenSettings()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSettingTile(entry: QuickEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = entry.label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ContactsPage(
    state: SearchUiState,
    onContactActions: (SearchItem) -> Unit,
    onRequestContacts: () -> Unit,
    onCall: (SearchItem) -> Unit,
    onOpenWhatsApp: (SearchItem) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val collator = remember { Collator.getInstance() }
    val contacts = remember(state.allItems) {
        state.allItems
            .filter { it.type == ItemType.CONTACT && !it.id.startsWith("custom:") }
            .distinctBy { it.phoneNumber ?: it.id }
            .sortedWith(compareBy(collator) { it.title })
    }
    val filtered = remember(contacts, filter) {
        if (filter.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.title.contains(filter, ignoreCase = true) ||
                    it.phoneNumber.orEmpty().contains(filter)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Contactos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = contacts.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Toca uno para llamar, abrir WhatsApp o anclarlo al panel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )

        if (!state.contactsPermission) {
            ContactsHint(onRequestContacts)
        } else {
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.loading) "Cargando…" else "Sin contactos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!state.loading && filter.isBlank()) {
                            TextButton(onClick = onRequestContacts) { Text("Cargar agenda") }
                        }
                    }
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactListRow(
                            contact = contact,
                            pinned = contact.id in state.pinnedIds,
                            onClick = { onContactActions(contact) },
                            onCall = { onCall(contact) },
                            onChat = {
                                WhatsAppLinks.directChatItem(context, contact)?.let(onOpenWhatsApp)
                            }
                        )
                    }
                }
                val letters = filtered.mapNotNull { it.title.trim().firstOrNull()?.uppercaseChar() }
                    .filter { it.isLetter() }.distinct()
                AlphabetRail(letters) { letter ->
                    val index = filtered.indexOfFirst { it.title.trim().firstOrNull()?.uppercaseChar() == letter }
                    if (index >= 0) scope.launch { listState.scrollToItem(index) }
                }
                }
            }
            if (showSearch) {
                SearchField(filter, { filter = it }, {}, autoFocus = true, focusRequester = focus,
                    compact = true, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = {
                    if (!showSearch) showSearch = true
                    else { focus.requestFocus(); keyboard?.show() }
                }) { Icon(Icons.Filled.Search, "Buscar contacto / mostrar teclado") }
            }
        }
    }
}

@Composable
private fun ContactListRow(
    contact: SearchItem,
    pinned: Boolean,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clip(CircleShape)) { ItemIcon(contact, size = 32.dp) }
        Spacer(Modifier.width(5.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.phoneNumber.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (pinned) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Anclado",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        IconButton(onClick = onChat, modifier = Modifier.size(28.dp)) {
            Icon(painterResource(R.drawable.ic_whatsapp_chat), "Escribir por WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onCall, modifier = Modifier.size(28.dp)) {
            Icon(painterResource(R.drawable.ic_phone), "Llamar", modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.MoreVert, "Más opciones", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllAppsPage(
    state: SearchUiState,
    onItemClick: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onItemLongClick: (SearchItem) -> Unit
) {
    val collator = remember { Collator.getInstance() }
    val apps = remember(state.allItems, collator) {
        state.allItems
            .filter { it.type == ItemType.APP }
            .distinctBy { it.packageName ?: it.id }
            .sortedWith(compareBy(collator) { it.title })
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val alphabet = remember(apps) {
        apps.mapNotNull { it.title.trim().firstOrNull()?.uppercaseChar() }
            .filter { it.isLetter() }
            .distinct()
    }
    var alphabetRailHeight by remember { mutableStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Todas las apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = apps.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.loading && apps.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state = gridState,
                    contentPadding = PaddingValues(start = 6.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
                ) {
                    items(apps, key = { it.id }) { app ->
                        AppGridTile(
                            app = app,
                            onClick = { onItemClick(app) },
                            onDoubleClick = { onOpenFloating(app) },
                            onLongClick = { onItemLongClick(app) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight()
                        .onSizeChanged { alphabetRailHeight = it.height.coerceAtLeast(1) }
                        .pointerInput(alphabet, apps, alphabetRailHeight) {
                            var lastLetterIndex = -1
                            fun jumpTo(positionY: Float) {
                                if (alphabet.isEmpty()) return
                                val letterIndex = (
                                    positionY.coerceIn(0f, alphabetRailHeight.toFloat() - 1f) /
                                        alphabetRailHeight * alphabet.size
                                    ).toInt().coerceIn(alphabet.indices)
                                if (letterIndex == lastLetterIndex) return
                                lastLetterIndex = letterIndex
                                val letter = alphabet[letterIndex]
                                val appIndex = apps.indexOfFirst {
                                    it.title.trim().firstOrNull()?.uppercaseChar() == letter
                                }
                                if (appIndex >= 0) {
                                    scope.launch { gridState.scrollToItem(appIndex) }
                                }
                            }
                            detectDragGestures(
                                onDragStart = { jumpTo(it.y) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    jumpTo(change.position.y)
                                }
                            )
                        }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    alphabet.forEach { letter ->
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val index = apps.indexOfFirst {
                                    it.title.trim().firstOrNull()?.uppercaseChar() == letter
                                }
                                if (index >= 0) scope.launch { gridState.scrollToItem(index) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridTile(
    app: SearchItem,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val widget = WidgetStore.get(context, app.id)
    Column(
        modifier = Modifier
            .width(64.dp)
            .then(if (widget != null) Modifier.pointerInput(app.id, widget.id) {
                var movement = 0f
                detectVerticalDragGestures(onDragStart = { movement = 0f }, onVerticalDrag = { change, amount ->
                    movement += amount
                    change.consume()
                }, onDragEnd = {
                    if (movement < -36.dp.toPx()) {
                        EdgePanelService.showWidget(context, app.id)
                        context.findActivity()?.finish()
                    }
                })
            } else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ItemIcon(app, size = 40.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

private fun quickSettingIcon(title: String): ImageVector = when (title) {
    "Wi-Fi" -> Icons.Filled.Wifi
    "Bluetooth" -> Icons.Filled.Bluetooth
    "Datos móviles" -> Icons.Filled.DataUsage
    "Pantalla" -> Icons.Filled.Brightness6
    "Sonido" -> Icons.AutoMirrored.Filled.VolumeUp
    "Batería" -> Icons.Filled.BatterySaver
    "Almacenamiento" -> Icons.Filled.Storage
    "Aplicaciones" -> Icons.Filled.Apps
    "Ubicación" -> Icons.Filled.LocationOn
    "Idioma" -> Icons.Filled.Translate
    "Fecha y hora" -> Icons.Filled.Schedule
    "Accesibilidad" -> Icons.Filled.Accessibility
    "Seguridad" -> Icons.Filled.Security
    "Opciones de desarrollador" -> Icons.Filled.DeveloperMode
    "VPN" -> Icons.Filled.VpnKey
    "NFC" -> Icons.Filled.Nfc
    "Pantalla de inicio" -> Icons.Filled.Home
    else -> Icons.Filled.Settings
}

private enum class PanelAddKind {
    MENU, APP, CONTACT, SETTING, PAIR, FOLDER, WEB, WHATSAPP
}

@Composable
private fun PanelAddDialogs(
    kind: PanelAddKind?,
    state: SearchUiState,
    onKindChange: (PanelAddKind?) -> Unit,
    onPinItem: (SearchItem) -> Unit,
    onAddPinned: (String, String) -> Unit
) {
    val apps = state.allItems.filter { it.type == ItemType.APP }
    val contacts = state.allItems.filter {
        it.type == ItemType.CONTACT && !it.id.startsWith("custom:")
    }
    val settings = state.allItems.filter { it.type == ItemType.SETTING }

    when (kind) {
        PanelAddKind.MENU -> AlertDialog(
            onDismissRequest = { onKindChange(null) },
            title = { Text("Añadir al panel") },
            text = {
                Column {
                    PanelAddRow(Icons.Filled.Apps, "Aplicación") { onKindChange(PanelAddKind.APP) }
                    PanelAddRow(Icons.Filled.Person, "Contacto para llamar") { onKindChange(PanelAddKind.CONTACT) }
                    PanelAddRow(Icons.Filled.Tune, "Preferencia del sistema") { onKindChange(PanelAddKind.SETTING) }
                    PanelAddRow(Icons.AutoMirrored.Filled.CompareArrows, "Par de aplicaciones") { onKindChange(PanelAddKind.PAIR) }
                    PanelAddRow(Icons.Filled.Folder, "Carpeta") { onKindChange(PanelAddKind.FOLDER) }
                    PanelAddRow(Icons.Filled.Language, "Enlace o página web") { onKindChange(PanelAddKind.WEB) }
                    PanelAddRow(Icons.AutoMirrored.Filled.Chat, "WhatsApp a contacto") { onKindChange(PanelAddKind.WHATSAPP) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onKindChange(null) }) { Text("Cancelar") } }
        )

        PanelAddKind.APP -> PanelItemPicker("Añadir aplicación", apps, onKindChange, onPinItem)
        PanelAddKind.CONTACT -> ContactShortcutPicker(
            contacts, false, { onKindChange(null) }, onAddPinned
        )
        PanelAddKind.SETTING -> PanelItemPicker("Añadir ajuste", settings, onKindChange, onPinItem)

        PanelAddKind.PAIR -> AddAppPairDialog(
            apps = apps,
            onDismiss = { onKindChange(null) },
            onConfirm = { label, target -> onAddPinned(label, target) }
        )

        PanelAddKind.FOLDER -> AddFolderDialog(
            apps = apps,
            onDismiss = { onKindChange(null) },
            onConfirm = { label, selected ->
                val builder = Uri.Builder().scheme("nexo-folder").authority("open")
                    .appendQueryParameter("name", label)
                selected.forEach { builder.appendQueryParameter("app", it.packageName) }
                onAddPinned(label, builder.build().toString())
            }
        )

        PanelAddKind.WEB -> AddShortcutDialog(
            onDismiss = { onKindChange(null) },
            onConfirm = onAddPinned
        )

        PanelAddKind.WHATSAPP -> ContactShortcutPicker(
            contacts = contacts,
            whatsapp = true,
            onDismiss = { onKindChange(null) },
            onAdd = onAddPinned
        )

        null -> Unit
    }
}

@Composable
private fun PanelAddRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PanelItemPicker(
    title: String,
    items: List<SearchItem>,
    onKindChange: (PanelAddKind?) -> Unit,
    onSelected: (SearchItem) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onKindChange(null) },
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Buscar") },
                    singleLine = true
                )
                LazyColumn(Modifier.height(320.dp)) {
                    items(items.filter {
                        it.title.contains(filter, ignoreCase = true) ||
                            it.subtitle.contains(filter, ignoreCase = true)
                    }, key = { it.id }) { item ->
                        TextButton(
                            onClick = { onSelected(item) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ItemIcon(item, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title)
                                if (item.subtitle.isNotBlank()) {
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onKindChange(null) }) { Text("Cancelar") } }
    )
}

@Composable
private fun PinHint() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Mantén pulsada una app para anclarla",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemActionsSheet(
    item: SearchItem?,
    pinnedIds: Set<String>,
    onAddShortcut: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onTogglePin: (SearchItem) -> Unit
) {
    if (item == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pinned = item.id in pinnedIds
    val canFloat = ItemLauncher.canOpenFloating(item)
    var showGestures by remember(item.id) { mutableStateOf(false) }
    fun addContactShortcut(whatsapp: Boolean) {
        val target = ContactShortcuts.target(context, item, whatsapp)
        if (target != null) {
            onAddShortcut(item.title, target)
            onDismiss()
        } else Toast.makeText(context, "No se pudo interpretar este número", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemIcon(item, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(4.dp))

            SheetAction(icon = { Icon(Icons.Filled.Tune, null) }, label = "Gestos") {
                showGestures = true
            }

            SheetAction(
                icon = {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                },
                label = if (item.type == ItemType.CONTACT) "Llamar al contacto" else "Abrir"
            ) {
                onOpen(item)
            }

            if (item.type == ItemType.CONTACT && !item.phoneNumber.isNullOrBlank()) {
                SheetAction(
                    icon = { Icon(painterResource(R.drawable.ic_whatsapp_chat), null) },
                    label = "Escribir por WhatsApp"
                ) {
                    WhatsAppLinks.directChatItem(context, item)?.let(onOpen)
                }
                SheetAction(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = "Añadir atajo de WhatsApp"
                ) { addContactShortcut(true) }
                SheetAction(
                    icon = { Icon(painterResource(R.drawable.ic_phone), null) },
                    label = "Añadir atajo de teléfono"
                ) { addContactShortcut(false) }
            }

            if (canFloat) {
                SheetAction(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_floating_window),
                            contentDescription = null
                        )
                    },
                    label = "Abrir en ventana flotante"
                ) {
                    onOpenFloating(item)
                }
            }

            if (item.type == ItemType.APP && !item.packageName.isNullOrBlank()) {
                SheetAction(
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = "Información de la app"
                ) {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${item.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        true
                    }.getOrDefault(false)
                    if (!opened) Toast.makeText(
                        context, "No se pudo abrir la información de la app", Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }

                SheetAction(
                    icon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    label = "Desinstalar"
                ) {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:${item.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        true
                    }.getOrDefault(false)
                    if (!opened) Toast.makeText(
                        context, "Esta aplicación no se puede desinstalar", Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }

            if (!item.id.startsWith("store:")) {
                SheetAction(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    label = if (pinned) "Quitar de ancladas" else "Anclar al panel"
                ) {
                    onTogglePin(item)
                }
            }

            if (canFloat) {
                Text(
                    text = "La ventana flotante depende del fabricante y de su soporte multitarea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showGestures) {
        AlertDialog(
            onDismissRequest = { showGestures = false },
            title = { Text("Gestos · ${item.title}") },
            text = { Text("Deslizar hacia arriba: abrir un widget flotante. Busca el widget y ajusta su ancho y alto con los botones de la ventana; el tamaño queda guardado para este icono.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(context, WidgetPickerActivity::class.java).putExtra("item_id", item.id))
                    showGestures = false
                    onDismiss()
                }) { Text("Elegir o cambiar widget") }
            },
            dismissButton = {
                TextButton(onClick = { WidgetStore.remove(context, item.id); showGestures = false; onDismiss() }) {
                    Text("Quitar gesto")
                }
            }
        )
    }
}

@Composable
private fun SheetAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun View.isImeVisible(): Boolean =
    ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
