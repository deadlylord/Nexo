package com.stree.nexo.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import kotlin.math.roundToInt
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Gesture
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.semantics.Role
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.stree.nexo.media.MediaControlService
import com.stree.nexo.media.OpenMusicResult
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.search.WhatsAppLinks
import com.stree.nexo.ui.theme.visualSpec
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
    bottomContentPaddingDp: Int = 20,
    listMode: Boolean = false,
    wrapHeight: Boolean = false,
    minFillSlots: Int = 0,
    onItemLongClickAtSlot: ((SearchItem, Int?) -> Unit)? = null
) {
    when {
        state.loading && state.allItems.isEmpty() -> {
            Box(
                if (wrapHeight) modifier.fillMaxWidth().padding(24.dp) else modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            Box(
                (if (wrapHeight) modifier.fillMaxWidth() else modifier.fillMaxSize())
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
                    val slots = linkedMapOf<Int, SearchItem>()
                    val unslotted = mutableListOf<SearchItem>()
                    pinned.forEach { item ->
                        val explicit = state.pinnedSlots[item.id]
                        if (explicit != null && explicit >= 0 && explicit !in slots) {
                            slots[explicit] = item
                        } else {
                            unslotted += item
                        }
                    }
                    var cursor = 0
                    unslotted.forEach { item ->
                        while (cursor in slots) cursor++
                        slots[cursor] = item
                        cursor++
                    }
                    val suggestions = ArrayDeque(unified.filterNot { it.id in state.pinnedIds })
                    val maxPinnedEnd = (slots.keys.maxOrNull() ?: -1) + 1
                    val end = maxOf(
                        maxPinnedEnd,
                        minFillSlots,
                        if (showEmptySlots) gridColumns * 6 else 0
                    )
                    val cells = (0 until end).mapNotNull { position ->
                        slots[position]
                            ?: (if (!showEmptySlots) suggestions.removeFirstOrNull() else null)
                            ?: if (showEmptySlots) SearchItem("empty-slot:$position", "Añadir", "", ItemType.CUSTOM) else null
                    }
                    if (cells.isEmpty()) emptyList() else listOf(ResultSection("", cells))
                } else if (unified.isEmpty()) emptyList() else listOf(ResultSection("", unified))
            }
            val bestId = if (listMode) state.firstMatch()?.id else null
            var draggedId by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableStateOf(Offset.Zero) }
            var dropTargetId by remember { mutableStateOf<String?>(null) }
            val cellBounds = remember { mutableMapOf<String, androidx.compose.ui.geometry.Rect>() }
            val displaySlots = sections.flatMap { it.items }.withIndex().associate { it.value.id to it.index }
            val currentDisplaySlots by rememberUpdatedState(displaySlots)
            val openActions: (SearchItem) -> Unit = { target ->
                if (onItemLongClickAtSlot != null) onItemLongClickAtSlot(target, currentDisplaySlots[target.id])
                else onItemLongClick(target)
            }
            val gestureScope = rememberCoroutineScope()
            val itemKeys = sections.flatMap { section ->
                section.items.map { item -> "result:${section.title}:${item.id}" to item.id }
            }.toMap()
            val currentItemKeys by rememberUpdatedState(itemKeys)

            // Al anclar/quitar elementos la lista cambia de secciones: vuelve arriba
            // para que "Ancladas" quede visible. Con la cuadrícula invertida el
            // índice 0 es la fila inferior, así que se respeta el anclaje.
            LaunchedEffect(state.pinnedIds) {
                if (!bottomAnchored) gridState.scrollToItem(0)
            }
            // Con la lista invertida el índice 0 es la fila inferior: así la
            // mejor coincidencia queda siempre visible junto al teclado.
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
                    .then(if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
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
                            if (!listMode && (item.showsAsTile() ||
                                (item.type == ItemType.CONTACT && item.id in state.pinnedIds))
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
                                                            openActions(item)
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
                                                            openActions(item)
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
                                onActions = { openActions(item) }
                            )
                        } else if (!listMode && (pinnedContact || item.showsAsTile())) {
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
                                    { openActions(item) }
                                }
                            )
                        } else {
                            ResultRow(
                                item = item,
                                pinned = item.id in state.pinnedIds,
                                compact = listMode,
                                best = listMode && item.id == bestId,
                                onClick = open,
                                onDoubleClick = if (ItemLauncher.canOpenFloating(item)
                                ) {
                                    { onOpenFloating(item) }
                                } else null,
                                onLongClick = if (draggable) {
                                    null
                                } else {
                                    { openActions(item) }
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
    onLongClick: (() -> Unit)?,
    compact: Boolean = false,
    best: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.padding(horizontal = 2.dp, vertical = 1.dp) else Modifier)
            .clip(RoundedCornerShape(if (compact) 16.dp else 0.dp))
            .background(
                if (best) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
            .padding(horizontal = if (compact) 10.dp else 18.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemIcon(item)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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

        if (best && !pinned) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Mejor coincidencia (Enter)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else if (pinned) {
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
            .clip(RoundedCornerShape(14.dp))
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
            val labelColor = MaterialTheme.colorScheme.onSurface
            val isLightText = (labelColor.red + labelColor.green + labelColor.blue) > 1.5f
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = if (isLightText) Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f) else null
                ),
                fontWeight = FontWeight.Medium,
                color = labelColor,
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
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(160, easing = PanelMotion.EnterEasing),
        label = "iconEntrance"
    )
    val photo by produceState<android.graphics.Bitmap?>(null, item.photoUri) {
        value = item.photoUri?.let { uri -> withContext(Dispatchers.IO) { ContactPhotos.load(context, uri) } }
    }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                alpha = 0.72f + 0.28f * entrance
                scaleX = 0.95f + 0.05f * entrance
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
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val panelColumns = remember { Prefs.panelColumns(context) }
    val panelIconScale = remember { Prefs.panelIconScale(context) }
    val panelSpacing = remember { Prefs.panelItemSpacing(context) }
    val panelShowLabels = remember { Prefs.panelShowLabels(context) }
    val pages = remember { Prefs.visiblePanelPages(context) }
    val infiniteScroll = remember(pages) { Prefs.panelInfiniteScroll(context) && pages.size > 1 }
    val pagerCount = if (infiniteScroll) pages.size * 1000 else pages.size
    val initialPagerPage = if (infiniteScroll) pages.size * 500 else 0
    val pagerState = rememberPagerState(initialPage = initialPagerPage, pageCount = { pagerCount })
    fun realPageIndex(virtualPage: Int): Int =
        if (pages.isEmpty()) 0 else ((virtualPage % pages.size) + pages.size) % pages.size
    fun nearestVirtualPage(targetReal: Int): Int {
        if (!infiniteScroll || pages.size <= 1) return targetReal.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val cur = pagerState.currentPage
        val curReal = realPageIndex(cur)
        var delta = targetReal - curReal
        val half = pages.size / 2
        if (delta > half) delta -= pages.size
        else if (delta < -half) delta += pages.size
        return (cur + delta).coerceIn(0, pagerCount - 1)
    }
    val scope = rememberCoroutineScope()

    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<SearchItem?>(null) }
    var actionItemSlot by remember { mutableStateOf<Int?>(null) }
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

    val dismissPanel: () -> Unit = {
        if (!closing) {
            closing = true
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            shown = false
            scope.launch {
                delay(PanelMotion.EXIT_DURATION_MS.toLong())
                onClose()
            }
        }
    }

    BackHandler {
        when {
            state.query.isNotBlank() -> onQueryChange("")
            realPageIndex(pagerState.currentPage) != 0 -> scope.launch {
                pagerState.animateScrollToPage(
                    nearestVirtualPage(0),
                    animationSpec = tween(PanelMotion.TAB_DURATION_MS, easing = PanelMotion.StandardEasing)
                )
            }
            else -> dismissPanel()
        }
    }

    // Alto real del teclado medido con el layout de la ventana. Los insets
    // llegan en cero en varios fabricantes, así que no alcanzan por sí solos.
    val imeHeight = rememberVisibleImeHeight()
    val imeOpen = imeHeight > 0.dp
    val savedImeDp = remember { Prefs.lastImeHeightDp(context) }
    var awaitingInitialIme by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        shown = true
        delay(650)
        awaitingInitialIme = false
    }
    LaunchedEffect(imeHeight) {
        if (imeHeight > 0.dp) awaitingInitialIme = false
        val dpInt = imeHeight.value.roundToInt()
        if (dpInt >= 120 && dpInt != savedImeDp) {
            Prefs.setLastImeHeightDp(context, dpInt)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pages.getOrNull(realPageIndex(pagerState.currentPage)) != PanelPage.MAIN) {
            awaitingInitialIme = false
            keyboard?.hide()
        }
    }

    val playback by MediaControlService.playback.collectAsStateWithLifecycle()
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    var fallbackPlaying by remember { mutableStateOf(audioManager?.isMusicActive == true) }
    val isPlaying = playback?.playing ?: fallbackPlaying

    fun dispatchMediaKey(keyCode: Int) {
        val manager = audioManager ?: return
        val now = SystemClock.uptimeMillis()
        runCatching {
            manager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            manager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    val themeRevision = Prefs.themeRevision
    val panelStyle = remember(themeRevision) { Prefs.panelStyle(context) }
    val panelLight = remember(themeRevision) { Prefs.panelLightBackground(context) }
    val themeSpec = remember(panelStyle, panelLight) { panelStyle.visualSpec(light = panelLight) }
    val iconBrush = remember(themeSpec) { themeSpec.actionIconBrush }

    val scrimInteraction = remember { MutableInteractionSource() }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 0.22f else 0f,
        animationSpec = tween(
            durationMillis = if (shown) PanelMotion.SCRIM_ENTER_MS else PanelMotion.SCRIM_EXIT_MS,
            easing = if (shown) PanelMotion.EnterEasing else PanelMotion.ExitEasing
        ),
        label = "scrim"
    )

    val togglePlayPause: () -> Unit = {
        Haptics.perform(context)
        val current = playback
        if (current != null) {
            runCatching {
                if (current.playing) current.controller.transportControls.pause()
                else current.controller.transportControls.play()
            }.onFailure {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        } else {
            fallbackPlaying = !fallbackPlaying
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    val openPlayingMusicApp: () -> Unit = {
        Haptics.perform(context, strong = true)
        when (MediaControlService.openPlayingApp(context)) {
            OpenMusicResult.OPENED -> dismissPanel()
            OpenMusicResult.PERMISSION_REQUIRED -> {
                Toast.makeText(
                    context,
                    "Autoriza el acceso a notificaciones para abrir la app de música",
                    Toast.LENGTH_LONG
                ).show()
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                dismissPanel()
            }
            OpenMusicResult.NO_ACTIVE_APP -> {
                Toast.makeText(
                    context,
                    "No hay ninguna app de música reproduciendo",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val skipNextTrack: () -> Unit = {
        Haptics.perform(context)
        val current = playback
        if (current != null) {
            runCatching { current.controller.transportControls.skipToNext() }
                .onFailure { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    val toggleKeyboard: () -> Unit = {
        if (imeOpen || view.isImeVisible()) {
            awaitingInitialIme = false
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        } else scope.launch {
            val main = pages.indexOf(PanelPage.MAIN)
            if (main >= 0 && realPageIndex(pagerState.currentPage) != main) {
                pagerState.animateScrollToPage(
                    nearestVirtualPage(main),
                    animationSpec = tween(PanelMotion.TAB_DURATION_MS, easing = PanelMotion.StandardEasing)
                )
            }
            searchFocus.requestFocus()
            delay(90)
            keyboard?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = dismissPanel
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
                contentAlignment = if (side == PanelSide.END) Alignment.BottomEnd else Alignment.BottomStart
            ) {
                val density = LocalDensity.current
                val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                val topSafeMargin = maxOf(statusBarTop, 24.dp) + 8.dp
                val imeBottomGap = 6.dp
                val referenceImeHeight = when {
                    imeOpen -> imeHeight
                    savedImeDp >= 120 -> savedImeDp.dp
                    else -> maxHeight * 0.39f
                }
                val availableAboveIme = (maxHeight - referenceImeHeight - topSafeMargin - imeBottomGap)
                    .coerceAtLeast(180.dp)
                val availableFullScreen = (maxHeight - topSafeMargin - navBarBottom - 24.dp)
                    .coerceAtLeast(180.dp)
                val keyboardSpaceActive = imeOpen || awaitingInitialIme
                val maxAllowedHeight = if (keyboardSpaceActive) availableAboveIme else availableFullScreen
                // Todos los paneles mantienen exactamente el mismo tamaño que el panel principal.
                val panelSize = remember(themeRevision, maxWidth, maxHeight, maxAllowedHeight) {
                    computePanelSize(
                        context = context,
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        maxAllowedHeight = maxAllowedHeight
                    )
                }
                val animatedPanelHeight by animateDpAsState(
                    targetValue = panelSize.height,
                    animationSpec = tween(PanelMotion.LAYOUT_DURATION_MS, easing = PanelMotion.StandardEasing),
                    label = "panelHeight"
                )
                val targetBottomPadding = when {
                    imeOpen -> imeHeight + imeBottomGap
                    awaitingInitialIme -> referenceImeHeight + imeBottomGap
                    else -> ((maxHeight - panelSize.height) / 2f).coerceAtLeast(navBarBottom + 16.dp)
                }
                val animatedBottomPadding by animateDpAsState(
                    targetValue = targetBottomPadding,
                    animationSpec = tween(PanelMotion.LAYOUT_DURATION_MS, easing = PanelMotion.StandardEasing),
                    label = "panelBottomOffset"
                )
                val cols = panelColumns.coerceIn(3, 6)
                val gridWidth = (panelSize.width - 36.dp - (panelSpacing * (cols - 1)).dp).coerceAtLeast(120.dp)
                val cellWidth = gridWidth / cols
                val iconBase = (cellWidth * 0.58f).coerceIn(26.dp, 50.dp)
                val estIconSize = (iconBase * panelIconScale.coerceIn(0.75f, 1.35f))
                    .coerceAtMost((cellWidth * 0.80f).coerceAtLeast(22.dp))
                val estRowHeight = estIconSize + 4.dp + (if (panelShowLabels) 18.dp else 0.dp) + panelSpacing.dp
                val gridAvailableHeight = (panelSize.height - 66.dp).coerceAtLeast(estRowHeight)
                val visibleRows = ((gridAvailableHeight + panelSpacing.dp) / estRowHeight).toInt().coerceIn(3, 8)
                val minFillSlots = visibleRows * cols

                // Campo de búsqueda único y oculto fuera del carrusel para mantener
                // el foco del teclado sin duplicarlo entre páginas.
                SearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onSubmit = { submitSearch(context, state, launchPanelItem) },
                    focusRequester = searchFocus,
                    autoFocus = true,
                    compact = true,
                    modifier = Modifier
                        .size(1.dp)
                        .graphicsLayer { alpha = 0f }
                )

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = state.query.isBlank(),
                    pageSpacing = 0.dp,
                    modifier = Modifier
                        .padding(bottom = (animatedBottomPadding - 4.dp).coerceAtLeast(0.dp))
                        .width(panelSize.width)
                        .height(animatedPanelHeight + 8.dp)
                ) { page ->
                    val pageType = pages[realPageIndex(page)]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .panelCardTransition(pagerState, page, side)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        PanelSurface(
                            wrapHeight = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 6.dp)
                                    .padding(horizontal = 6.dp)
                            ) {
                                if (state.query.isNotBlank()) {
                                    SearchBody(
                                        state = state,
                                        onItemClick = launchPanelItem,
                                        onCallContact = onCallContact,
                                        onOpenFloating = onOpenFloating,
                                        onItemLongClick = { actionItemSlot = null; actionItem = it },
                                        onRequestContacts = onRequestContacts,
                                        modifier = Modifier.weight(1f),
                                        showSectionHeaders = false,
                                        showShortcutsNote = false,
                                        showTileLabels = panelShowLabels,
                                        gridColumns = panelColumns,
                                        resultSide = side,
                                        bottomAnchored = true,
                                        tileIconScale = panelIconScale,
                                        bottomContentPaddingDp = 4,
                                        listMode = true,
                                        wrapHeight = false
                                    )
                                } else {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        when (pageType) {
                                            PanelPage.MAIN -> {
                                                SearchPage(
                                                    state = state,
                                                    side = side,
                                                    onItemClick = launchPanelItem,
                                                    onCallContact = onCallContact,
                                                    onOpenFloating = onOpenFloating,
                                                    onItemLongClick = { actionItemSlot = null; actionItem = it },
                                                    onItemLongClickAtSlot = { item, slot ->
                                                        actionItemSlot = slot
                                                        actionItem = item
                                                    },
                                                    onRequestContacts = onRequestContacts,
                                                    onAddRequest = { addingSlot = null; addKind = PanelAddKind.MENU },
                                                    onAddSlot = { addingSlot = it; addKind = PanelAddKind.MENU },
                                                    onMovePinned = onMovePinned,
                                                    onMovePinnedToSlot = onAssignSlot,
                                                    columns = panelColumns,
                                                    iconScale = panelIconScale,
                                                    spacingDp = panelSpacing,
                                                    showLabels = panelShowLabels,
                                                    onWidgetGesture = { item ->
                                                        EdgePanelService.showWidget(context, item.id)
                                                        dismissPanel()
                                                    },
                                                    wrapHeight = false,
                                                    minFillSlots = minFillSlots
                                                )
                                            }

                                            PanelPage.SETTINGS -> {
                                                QuickSettingsPage(
                                                    state = state,
                                                    onItemClick = launchPanelItem,
                                                    onOpenSettings = onOpenSettings,
                                                    onOpenFloating = onOpenFloating,
                                                    onItemLongClick = { actionItemSlot = null; actionItem = it },
                                                    columns = panelColumns,
                                                    iconScale = panelIconScale,
                                                    spacingDp = panelSpacing,
                                                    showLabels = panelShowLabels
                                                )
                                            }

                                            PanelPage.APPS -> {
                                                AllAppsPage(
                                                    state = state,
                                                    onItemClick = launchPanelItem,
                                                    onOpenFloating = onOpenFloating,
                                                    onItemLongClick = { actionItemSlot = null; actionItem = it }
                                                )
                                            }

                                            PanelPage.CONTACTS -> {
                                                ContactsPage(
                                                    state = state,
                                                    onContactActions = { actionItemSlot = null; actionItem = it },
                                                    onRequestContacts = onRequestContacts,
                                                    onCall = onCallContact,
                                                    onOpenWhatsApp = launchPanelItem
                                                )
                                            }

                                            PanelPage.WIDGETS -> WidgetsPage(active = pagerState.currentPage == page)
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    thickness = 0.5.dp,
                                    color = themeSpec.outlineVariant.copy(alpha = 0.28f)
                                )
                                PanelFooter(
                                    isPlaying = isPlaying,
                                    query = state.query,
                                    imeOpen = imeOpen,
                                    activePillColor = themeSpec.primary.copy(alpha = 0.16f),
                                    iconBrush = iconBrush,
                                    onTogglePlayPause = togglePlayPause,
                                    onOpenMusicApp = openPlayingMusicApp,
                                    onSkipNext = skipNextTrack,
                                    onClearQuery = { onQueryChange("") },
                                    onToggleKeyboard = toggleKeyboard,
                                    onOpenSettings = onOpenSettings
                                )
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
        onDismiss = {
            actionItem = null
            actionItemSlot = null
        },
        onOpen = { item ->
            actionItem = null
            actionItemSlot = null
            launchPanelItem(item)
        },
        onOpenFloating = { item ->
            actionItem = null
            actionItemSlot = null
            onOpenFloating(item)
        },
        onTogglePin = { item ->
            val slot = actionItemSlot
            actionItem = null
            actionItemSlot = null
            if (item.id !in state.pinnedIds && slot != null) {
                onAssignSlot(item.id, slot)
            } else {
                onTogglePin(item)
            }
        },
        onReplaceSlot = if (actionItem != null && actionItem!!.id !in state.pinnedIds && actionItemSlot != null) {
            {
                val slot = actionItemSlot
                actionItem = null
                actionItemSlot = null
                addingSlot = slot
                addKind = PanelAddKind.MENU
            }
        } else null,
        onAddMorePinned = {
            actionItem = null
            actionItemSlot = null
            addingSlot = null
            addKind = PanelAddKind.MENU
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelFooter(
    isPlaying: Boolean,
    query: String,
    imeOpen: Boolean,
    activePillColor: Color,
    iconBrush: Brush,
    onTogglePlayPause: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onSkipNext: () -> Unit,
    onClearQuery: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val hasQuery = query.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = if (isPlaying) "Pausar música" else "Reproducir música",
                    onClick = onTogglePlayPause,
                    onDoubleClick = onOpenMusicApp
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) activePillColor else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        fadeIn(tween(140, easing = PanelMotion.StandardEasing)) togetherWith
                            fadeOut(tween(110, easing = PanelMotion.StandardEasing))
                    },
                    label = "playPauseIcon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pausar música" else "Reproducir música",
                        modifier = Modifier
                            .size(21.dp)
                            .gradientTint(iconBrush),
                        tint = Color.White
                    )
                }
            }
        }

        IconButton(
            onClick = onSkipNext,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Siguiente canción",
                modifier = Modifier
                    .size(22.dp)
                    .gradientTint(iconBrush),
                tint = Color.White
            )
        }

        IconButton(
            onClick = {
                Haptics.perform(context)
                if (hasQuery) onClearQuery() else onToggleKeyboard()
            },
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (hasQuery || imeOpen) activePillColor else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = hasQuery,
                    transitionSpec = {
                        (fadeIn(tween(140, easing = PanelMotion.StandardEasing)) +
                            scaleIn(
                                initialScale = 0.82f,
                                animationSpec = tween(140, easing = PanelMotion.StandardEasing)
                            )) togetherWith
                            (fadeOut(tween(110, easing = PanelMotion.StandardEasing)) +
                                scaleOut(
                                    targetScale = 0.82f,
                                    animationSpec = tween(110, easing = PanelMotion.StandardEasing)
                                ))
                    },
                    label = "keyboardOrClearIcon"
                ) { showClear ->
                    if (showClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Borrar búsqueda",
                            modifier = Modifier
                                .size(20.dp)
                                .gradientTint(iconBrush),
                            tint = Color.White
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard),
                            contentDescription = if (imeOpen) "Ocultar teclado" else "Mostrar teclado",
                            modifier = Modifier
                                .size(20.dp)
                                .gradientTint(iconBrush),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = {
                Haptics.perform(context)
                onOpenSettings()
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Abrir ajustes de Nexo",
                modifier = Modifier
                    .size(20.dp)
                    .gradientTint(iconBrush),
                tint = Color.White
            )
        }
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
    onItemLongClickAtSlot: ((SearchItem, Int?) -> Unit)? = null,
    onRequestContacts: () -> Unit,
    onAddRequest: () -> Unit,
    onAddSlot: (Int) -> Unit,
    onMovePinned: (String, String) -> Unit,
    columns: Int,
    iconScale: Float,
    spacingDp: Int,
    showLabels: Boolean,
    onWidgetGesture: (SearchItem) -> Unit,
    onMovePinnedToSlot: (String, Int) -> Unit,
    wrapHeight: Boolean = false,
    minFillSlots: Int = 0
) {
    Column(if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {

        if (state.query.isBlank() && !state.loading && state.error == null) {
            val pinned = state.sections.flatMap { it.items }.filter {
                it.id in state.pinnedIds && it.type != ItemType.SETTING
            }.distinctBy { it.id }
            val frequentFill = state.frequentApps.filterNot { it.id in state.pinnedIds }
            // Las apps ancladas ocupan sus posiciones y, si no llenan el panel,
            // los huecos restantes se completan con las frecuentes no ancladas.
            PanelSection(
                "Favoritos",
                if (wrapHeight) Modifier.fillMaxWidth() else Modifier.weight(1f)
            ) {
                SearchBody(
                    state = state.copy(
                        sections = listOf(
                            ResultSection("Ancladas", pinned),
                            ResultSection("Frecuentes", frequentFill)
                        )
                    ),
                    onItemClick = onItemClick, onCallContact = onCallContact,
                    onOpenFloating = onOpenFloating, onItemLongClick = onItemLongClick,
                    onItemLongClickAtSlot = onItemLongClickAtSlot,
                    onRequestContacts = onRequestContacts,
                    modifier = if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                    bottomContentPaddingDp = 0,
                    showSectionHeaders = false, showShortcutsNote = false, showTileLabels = showLabels,
                    resultSide = side, centerItems = false, bottomAnchored = true,
                    onEmptySpaceClick = onAddRequest,
                    onMovePinned = onMovePinned, onMovePinnedToSlot = onMovePinnedToSlot,
                    onAddSlot = onAddSlot, gridColumns = columns,
                    tileIconScale = iconScale, tileSpacingDp = spacingDp,
                    onWidgetGesture = onWidgetGesture, wrapHeight = wrapHeight,
                    minFillSlots = minFillSlots
                )
            }
        } else SearchBody(
            state = state,
            onItemClick = onItemClick,
            onCallContact = onCallContact,
            onOpenFloating = onOpenFloating,
            onItemLongClick = onItemLongClick,
            onRequestContacts = onRequestContacts,
            modifier = if (wrapHeight) Modifier.fillMaxWidth() else Modifier.weight(1f),
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
            onWidgetGesture = onWidgetGesture,
            wrapHeight = wrapHeight
        )

    }
}

@Composable
private fun QuickSettingsPage(
    state: SearchUiState,
    onItemClick: (SearchItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFloating: (SearchItem) -> Unit = {},
    onItemLongClick: (SearchItem) -> Unit = {},
    columns: Int = 5,
    iconScale: Float = 1f,
    spacingDp: Int = 0,
    showLabels: Boolean = true
) {
    val settings = state.allItems.filter { it.type == ItemType.SETTING }
    val preferredOrder = listOf(
        "Batería", "Bluetooth", "Seguridad", "Wi-Fi", "Almacenamiento",
        "Aplicaciones", "Pantalla", "Sonido", "Ubicación",
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

    // Recientes, frecuentes y para esta hora juntos, compactados en una sola cuadrícula
    // con el mismo número de columnas, escala y espaciado del tema del panel.
    val cols = columns.coerceIn(3, 6)
    val recent = state.sections.firstOrNull { it.title == "Recientes" }?.items.orEmpty()
    val frequent = state.sections.firstOrNull { it.title == "Frecuentes" }?.items.orEmpty()
    val hourly = state.sections.firstOrNull { it.title == "Para esta hora" }?.items.orEmpty()
    val suggestions = (recent + frequent + hourly)
        .distinctBy { it.id }
        .take(cols * 3)

    if (state.loading && entries.isEmpty() && suggestions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(spacingDp.dp),
        verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
    ) {
        if (suggestions.isNotEmpty()) {
            item(key = "h_suggestions", span = { GridItemSpan(maxLineSpan) }) {
                CompactGridTitle("Recientes y frecuentes")
            }
            items(suggestions, key = { "sug:${it.id}" }) { item ->
                ResultTile(
                    item = item,
                    pinned = item.id in state.pinnedIds,
                    showLabel = showLabels,
                    iconScale = iconScale,
                    onClick = { onItemClick(item) },
                    onDoubleClick = if (ItemLauncher.canOpenFloating(item)) {
                        { onOpenFloating(item) }
                    } else null,
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
        item(key = "h_settings", span = { GridItemSpan(maxLineSpan) }) {
            CompactGridTitle("Ajustes rápidos", topPadding = if (suggestions.isNotEmpty()) 6 else 2)
        }
        items(entries, key = { "setting:${it.label}" }) { entry ->
            QuickSettingTile(
                entry = entry,
                showLabel = showLabels,
                iconScale = iconScale
            ) {
                if (entry.item != null) {
                    onItemClick(entry.item)
                } else {
                    onOpenSettings()
                }
            }
        }
    }
}

@Composable
private fun CompactGridTitle(title: String, topPadding: Int = 2) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.7.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = topPadding.dp, bottom = 2.dp)
    )
}

@Composable
private fun QuickSettingTile(
    entry: QuickEntry,
    showLabel: Boolean = true,
    iconScale: Float = 1f,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (showLabel) 66.dp else 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints {
            val scale = iconScale.coerceIn(0.75f, 1.35f)
            val base = (maxWidth * 0.58f).coerceIn(26.dp, 50.dp)
            val iconSize = (base * scale).coerceAtMost((maxWidth * 0.80f).coerceAtLeast(22.dp))
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size((iconSize * 0.52f).coerceIn(14.dp, 26.dp))
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                lineHeight = 13.sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
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
            .fillMaxWidth()
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
        val labelColor = MaterialTheme.colorScheme.onSurface
        val isLightText = (labelColor.red + labelColor.green + labelColor.blue) > 1.5f
        Text(
            text = app.title,
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = if (isLightText) Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f) else null
            ),
            fontWeight = FontWeight.Medium,
            color = labelColor,
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

/**
 * Menú de acciones de un elemento: cabecera con icono y tipo, acciones
 * principales como botones grandes y el resto agrupado en tarjetas, con la
 * acción destructiva separada al final.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemActionsSheet(
    item: SearchItem?,
    pinnedIds: Set<String>,
    onAddShortcut: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onTogglePin: (SearchItem) -> Unit,
    onReplaceSlot: (() -> Unit)? = null,
    onAddMorePinned: (() -> Unit)? = null
) {
    if (item == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pinned = item.id in pinnedIds
    val canFloat = ItemLauncher.canOpenFloating(item)
    val canPin = !item.id.startsWith("store:")
    val isApp = item.type == ItemType.APP && !item.packageName.isNullOrBlank()
    val isContact = item.type == ItemType.CONTACT && !item.phoneNumber.isNullOrBlank()
    var showGestures by remember(item.id) { mutableStateOf(false) }

    fun addContactShortcut(whatsapp: Boolean) {
        val target = ContactShortcuts.target(context, item, whatsapp)
        if (target != null) {
            onAddShortcut(item.title, target)
            onDismiss()
        } else Toast.makeText(context, "No se pudo interpretar este número", Toast.LENGTH_SHORT).show()
    }

    fun startOrToast(intent: Intent, error: String) {
        val opened = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!opened) Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    val typeLabel = when (item.type) {
        ItemType.APP -> "Aplicación"
        ItemType.SHORTCUT -> "Atajo"
        ItemType.SETTING -> "Ajuste del sistema"
        ItemType.CONTACT -> "Contacto"
        ItemType.CUSTOM -> "Acceso directo"
    }
    val detail = when {
        item.type == ItemType.CONTACT -> item.phoneNumber.orEmpty()
        item.subtitle.isNotBlank() -> item.subtitle
        else -> item.packageName.orEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Cabecera
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)),
                    contentAlignment = Alignment.Center
                ) { ItemIcon(item, size = 40.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(typeLabel, detail).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (pinned) Icon(
                    Icons.Filled.Star, "Anclado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            // Acciones principales
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetPrimaryAction(
                    modifier = Modifier.weight(1f),
                    icon = {
                        if (item.type == ItemType.CONTACT) Icon(painterResource(R.drawable.ic_phone), null, Modifier.size(22.dp))
                        else Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp))
                    },
                    label = if (item.type == ItemType.CONTACT) "Llamar" else "Abrir",
                    emphasized = true
                ) { onOpen(item) }
                if (canFloat) SheetPrimaryAction(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(painterResource(R.drawable.ic_floating_window), null, Modifier.size(22.dp)) },
                    label = "Flotante"
                ) { onOpenFloating(item) }
                if (canPin) SheetPrimaryAction(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(if (pinned) Icons.Filled.Star else Icons.Filled.StarBorder, null, Modifier.size(22.dp)) },
                    label = if (pinned) "Desanclar" else "Anclar"
                ) { onTogglePin(item) }
            }

            // Panel: reemplazar sugerencia frecuente o anclar más elementos
            if (onReplaceSlot != null || onAddMorePinned != null) {
                Spacer(Modifier.height(14.dp))
                SheetGroup {
                    if (onReplaceSlot != null) {
                        SheetAction(
                            icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                            label = "Cambiar por otra app",
                            supporting = "Ancla otra app, contacto o atajo en este espacio",
                            onClick = onReplaceSlot
                        )
                        if (onAddMorePinned != null) SheetDivider()
                    }
                    if (onAddMorePinned != null) {
                        SheetAction(
                            icon = { Icon(Icons.Filled.Add, null) },
                            label = "Anclar más apps",
                            supporting = "Añade otra app, carpeta o atajo al panel",
                            onClick = onAddMorePinned
                        )
                    }
                }
            }

            // Contacto
            if (isContact) {
                Spacer(Modifier.height(14.dp))
                SheetGroup {
                    SheetAction(
                        icon = { Icon(painterResource(R.drawable.ic_whatsapp_chat), null) },
                        label = "Escribir por WhatsApp"
                    ) { WhatsAppLinks.directChatItem(context, item)?.let(onOpen) }
                    SheetDivider()
                    SheetAction(
                        icon = { Icon(Icons.Filled.Person, null) },
                        label = "Añadir atajo de WhatsApp",
                        supporting = "Lo ancla al panel"
                    ) { addContactShortcut(true) }
                    SheetDivider()
                    SheetAction(
                        icon = { Icon(painterResource(R.drawable.ic_phone), null) },
                        label = "Añadir atajo de teléfono",
                        supporting = "Lo ancla al panel"
                    ) { addContactShortcut(false) }
                }
            }

            // Opciones
            Spacer(Modifier.height(14.dp))
            SheetGroup {
                SheetAction(
                    icon = { Icon(Icons.Filled.Gesture, null) },
                    label = "Gestos",
                    supporting = "Widget al deslizar hacia arriba"
                ) { showGestures = true }
                if (isApp) {
                    SheetDivider()
                    SheetAction(
                        icon = { Icon(Icons.Filled.Info, null) },
                        label = "Información de la app",
                        supporting = "Permisos, almacenamiento y notificaciones"
                    ) {
                        startOrToast(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${item.packageName}")),
                            "No se pudo abrir la información de la app"
                        )
                    }
                }
            }

            // Acción destructiva, separada del resto
            if (isApp) {
                Spacer(Modifier.height(14.dp))
                SheetGroup {
                    SheetAction(
                        icon = { Icon(Icons.Filled.Delete, null) },
                        label = "Desinstalar",
                        destructive = true
                    ) {
                        startOrToast(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${item.packageName}")),
                            "Esta aplicación no se puede desinstalar"
                        )
                    }
                }
            }

            if (canFloat) {
                Text(
                    text = "La ventana flotante depende del fabricante y de su soporte multitarea.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
                    modifier = Modifier.padding(horizontal = 4.dp).padding(top = 14.dp)
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
private fun SheetPrimaryAction(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val container = if (emphasized) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)),
        content = content
    )
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f)
    )
}

@Composable
private fun SheetAction(
    icon: @Composable () -> Unit,
    label: String,
    supporting: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = .14f)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides accent) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!destructive) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
            modifier = Modifier.size(20.dp)
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

@Composable
private fun rememberVisibleImeHeight(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var heightPx by remember { mutableStateOf(0) }

    DisposableEffect(view) {
        val rect = android.graphics.Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            view.getWindowVisibleDisplayFrame(rect)
            val rootHeight = view.rootView.height
            val frameHidden = (rootHeight - rect.bottom).coerceAtLeast(0)
            val insets = ViewCompat.getRootWindowInsets(view)
            val insetIme = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val visible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true ||
                (rootHeight > 0 && frameHidden > rootHeight * 0.15f)
            // Un teclado siempre ocupa más que un quinto de la pantalla;
            // valores menores son barras de navegación.
            heightPx = if (visible) maxOf(frameHidden, insetIme) else 0
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }

    return with(density) { heightPx.toDp() }
}

/** Paints the icon with a gradient instead of a flat tint. */
internal fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.SrcIn)
    }

private fun View.isImeVisible(): Boolean =
    ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
