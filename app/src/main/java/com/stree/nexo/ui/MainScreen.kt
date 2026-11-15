package com.stree.nexo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stree.nexo.data.PanelSide
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
    var tab by rememberSaveable { mutableStateOf(MainTab.SEARCH) }
    LaunchedEffect(settingsRequest) {
        if (settingsRequest > 0) tab = MainTab.SETTINGS
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.SEARCH -> {
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
                    header = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Nexo",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Todo a uno o dos toques",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .windowInsetsPadding(WindowInsets.statusBars)
                )
            }
        }
    }
}
