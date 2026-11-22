package com.stree.nexo.ui

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stree.nexo.R
import com.stree.nexo.SpotlightShortcutActivity
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.SearchItem
import com.stree.nexo.data.SearchUiState

enum class MainTab(val label: String) {
    SEARCH("Buscar"),
    SHORTCUTS("Atajos"),
    SETTINGS("Ajustes")
}

@Composable
fun MainScreen(
    settingsRequest: Int = 0,
    state: SearchUiState,
    panelEnabled: Boolean,
    panelSide: PanelSide,
    contactsEnabled: Boolean,
    hapticsEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onItemClick: (SearchItem) -> Unit,
    onTogglePin: (SearchItem) -> Unit,
    onOpenFloating: (SearchItem) -> Unit,
    onRequestContacts: () -> Unit,
    onAddShortcut: (String, String) -> Unit,
    onDeleteShortcut: (String) -> Unit,
    onTogglePanel: (Boolean) -> Unit,
    onSideChange: (PanelSide) -> Unit,
    onToggleContacts: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.SEARCH) }
    var settingsSection by remember { mutableStateOf(SettingsSectionFilter.OVERVIEW) }
    var spotlightMode by remember { mutableStateOf(Prefs.mainSpotlightMode(context)) }

    LaunchedEffect(settingsRequest) {
        if (settingsRequest > 0) {
            settingsSection = SettingsSectionFilter.OVERVIEW
            tab = MainTab.SETTINGS
        }
    }

    val pinSpotlightShortcut: () -> Unit = {
        Haptics.perform(context)
        val sm = context.getSystemService(ShortcutManager::class.java)
        if (sm != null && sm.isRequestPinShortcutSupported) {
            val shortcut = ShortcutInfo.Builder(context, "pin_nexo_spotlight")
                .setShortLabel("Spotlight")
                .setLongLabel("Búsqueda rápida Spotlight")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_panel))
                .setIntent(
                    Intent(context, SpotlightShortcutActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                )
                .build()
            runCatching { sm.requestPinShortcut(shortcut, null) }
                .onFailure { Toast.makeText(context, "No se pudo crear el acceso directo", Toast.LENGTH_SHORT).show() }
        } else {
            Toast.makeText(context, "Mantén pulsado el icono de Nexo para usar el acceso Spotlight", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    MainTab.SEARCH -> Icons.Filled.Search
                                    MainTab.SHORTCUTS -> Icons.Filled.Star
                                    MainTab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontWeight = if (tab == item) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val direction = if (forward) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(PanelMotion.TAB_DURATION_MS, easing = PanelMotion.StandardEasing),
                    initialOffsetX = { fullWidth -> direction * (fullWidth / 7) }
                ) + fadeIn(tween(PanelMotion.TAB_DURATION_MS, easing = PanelMotion.StandardEasing))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(PanelMotion.TAB_DURATION_MS, easing = PanelMotion.StandardEasing),
                        targetOffsetX = { fullWidth -> -direction * (fullWidth / 7) }
                    ) + fadeOut(tween(140, easing = PanelMotion.StandardEasing)))
            },
            label = "mainTabTransition"
        ) { activeTab ->
            when (activeTab) {
                MainTab.SEARCH -> {
                    val searchHeader: @Composable () -> Unit = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Nexo",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (spotlightMode) "Búsqueda rápida estilo Spotlight" else "Todo a uno o dos toques",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable {
                                        Haptics.perform(context)
                                        spotlightMode = !spotlightMode
                                        Prefs.setMainSpotlightMode(context, spotlightMode)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (spotlightMode) Icons.Filled.Search else Icons.Filled.ViewAgenda,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (spotlightMode) "Spotlight" else "Clásico",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = pinSpotlightShortcut,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddToHomeScreen,
                                        contentDescription = "Crear acceso directo Spotlight",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (spotlightMode) {
                        SpotlightSearchContent(
                            state = state,
                            onQueryChange = onQueryChange,
                            onItemClick = onItemClick,
                            onTogglePin = onTogglePin,
                            onOpenFloating = onOpenFloating,
                            onOpenGestureSettings = {
                                settingsSection = SettingsSectionFilter.GESTURES
                                tab = MainTab.SETTINGS
                            },
                            onPinSpotlightShortcut = pinSpotlightShortcut,
                            header = searchHeader,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        )
                    } else {
                        SearchContent(
                            state = state,
                            onQueryChange = onQueryChange,
                            onItemClick = onItemClick,
                            onTogglePin = onTogglePin,
                            onOpenFloating = onOpenFloating,
                            onRequestContacts = onRequestContacts,
                            onAddShortcut = onAddShortcut,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .windowInsetsPadding(WindowInsets.statusBars),
                            header = searchHeader
                        )
                    }
                }

                MainTab.SHORTCUTS -> {
                    ShortcutsScreen(
                        apps = state.allItems.filter { it.type == com.stree.nexo.data.ItemType.APP },
                        contacts = state.allItems.filter {
                            it.type == com.stree.nexo.data.ItemType.CONTACT &&
                                !it.id.startsWith("custom:")
                        },
                        shortcuts = state.allItems.filter { it.id.startsWith("custom:") },
                        onAdd = onAddShortcut,
                        onDelete = onDeleteShortcut,
                        onOpenHomeSettings = onOpenHomeSettings,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    )
                }

                MainTab.SETTINGS -> {
                    SettingsScreen(
                        panelEnabled = panelEnabled,
                        panelSide = panelSide,
                        contactsEnabled = contactsEnabled,
                        hapticsEnabled = hapticsEnabled,
                        shortcutsBlocked = state.shortcutsBlocked,
                        onTogglePanel = onTogglePanel,
                        onSideChange = onSideChange,
                        onToggleContacts = onToggleContacts,
                        onToggleHaptics = onToggleHaptics,
                        onOpenOverlayPermission = onOpenOverlayPermission,
                        onRequestBatteryExemption = onRequestBatteryExemption,
                        onOpenHomeSettings = onOpenHomeSettings,
                        onRefresh = onRefresh,
                        initialSection = settingsSection,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    )
                }
            }
        }
    }
}
