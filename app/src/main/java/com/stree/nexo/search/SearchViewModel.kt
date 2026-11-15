package com.stree.nexo.search

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stree.nexo.data.CustomShortcut
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.ResultSection
import com.stree.nexo.data.SearchItem
import com.stree.nexo.data.SearchRepository
import com.stree.nexo.data.SearchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SearchRepository(application)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var currentItems: List<SearchItem> = emptyList()
    private var refreshing = false
    private var lastBuildAt = 0L

    init {
        repository.cachedIndex()?.let { cached ->
            currentItems = cached.items
            _state.value = SearchUiState(
                allItems = cached.items,
                loading = false,
                contactsPermission = cached.contactsPermission,
                shortcutsBlocked = cached.shortcutsBlocked,
                pinnedIds = repository.pinnedIds,
                pinnedOrder = repository.pinnedOrder,
                pinnedSlots = repository.pinnedSlots
            )
            recompute("")
        }
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                if (_state.subscriptionCount.value > 0) {
                    withContext(Dispatchers.IO) { runCatching { repository.usage.importDeviceUsage() } }
                    recompute(_state.value.query)
                }
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (refreshing) return
        // Al reabrir el panel se reutiliza el índice reciente: reconstruirlo en
        // cada apertura consumía CPU justo cuando el panel debía aparecer.
        if (!force && currentItems.isNotEmpty() &&
            System.currentTimeMillis() - lastBuildAt < 30_000L
        ) {
            return
        }
        refreshing = true

        viewModelScope.launch {
            _state.update { it.copy(loading = currentItems.isEmpty(), error = null) }

            runCatching {
                withContext(Dispatchers.IO) { repository.buildIndex() }
            }.onSuccess { result ->
                currentItems = result.items
                lastBuildAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        allItems = result.items,
                        loading = false,
                        contactsPermission = result.contactsPermission,
                        shortcutsBlocked = result.shortcutsBlocked,
                        pinnedIds = repository.pinnedIds,
                        pinnedOrder = repository.pinnedOrder,
                        pinnedSlots = repository.pinnedSlots,
                        error = null
                    )
                }
                recompute(_state.value.query)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "No se pudo cargar el índice"
                    )
                }
            }

            refreshing = false
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        recompute(query)
    }

    fun recordUsage(id: String) {
        repository.usage.record(id)
        recompute(_state.value.query)
    }

    fun customShortcuts(): List<CustomShortcut> = repository.customShortcuts()

    fun addCustomShortcut(label: String, target: String) {
        repository.addCustomShortcut(label, target)
        updateCustomItems()
    }

    fun addPinnedShortcut(label: String, target: String) {
        val shortcut = repository.addCustomShortcut(label, target)
        repository.togglePinned("custom:${shortcut.id}")
        _state.update { it.copy(pinnedIds = repository.pinnedIds, pinnedOrder = repository.pinnedOrder) }
        updateCustomItems()
    }

    fun addPinnedShortcutAtSlot(label: String, target: String, slot: Int) {
        val shortcut = repository.addCustomShortcut(label, target)
        repository.togglePinned("custom:${shortcut.id}")
        repository.assignSlot("custom:${shortcut.id}", slot)
        syncPinnedState()
        updateCustomItems()
    }

    fun assignPinnedSlot(id: String, slot: Int) {
        if (id !in repository.pinnedIds) repository.togglePinned(id)
        repository.assignSlot(id, slot)
        syncPinnedState()
        recompute(_state.value.query)
    }

    private fun syncPinnedState() {
        _state.update { it.copy(pinnedIds = repository.pinnedIds, pinnedOrder = repository.pinnedOrder,
            pinnedSlots = repository.pinnedSlots) }
    }

    fun deleteCustomShortcut(id: String) {
        com.stree.nexo.widget.WidgetStore.remove(getApplication(), "custom:$id")
        repository.unpin("custom:$id")
        repository.deleteCustomShortcut(id)
        _state.update { it.copy(pinnedIds = repository.pinnedIds, pinnedOrder = repository.pinnedOrder) }
        updateCustomItems()
    }

    /** Returns true when the item is now pinned, false when it was unpinned. */
    fun togglePinned(item: SearchItem): Boolean {
        val pinned = repository.togglePinned(item.id)
        _state.update { it.copy(pinnedIds = repository.pinnedIds, pinnedOrder = repository.pinnedOrder) }
        recompute(_state.value.query)
        return pinned
    }

    fun movePinned(id: String, targetId: String) {
        repository.movePinned(id, targetId)
        _state.update { it.copy(pinnedOrder = repository.pinnedOrder) }
        recompute(_state.value.query)
    }

    private fun updateCustomItems() {
        currentItems = currentItems.filterNot { it.id.startsWith("custom:") } + repository.customItems()
        _state.update { it.copy(allItems = currentItems) }
        recompute(_state.value.query)
    }

    private fun recompute(query: String) {
        syncPinnedState()
        val pinnedIds = _state.value.pinnedIds
        val pinnedOrder = _state.value.pinnedOrder
        val sections = if (query.isBlank()) {
            idleSections(currentItems, pinnedIds, pinnedOrder)
        } else {
            searchSections(currentItems, query, pinnedIds, pinnedOrder)
        }
        _state.update { it.copy(sections = sections) }
    }

    private fun idleSections(
        items: List<SearchItem>,
        pinnedIds: Set<String>,
        pinnedOrder: List<String>
    ): List<ResultSection> {
        if (items.isEmpty()) return emptyList()

        val byId = items.associateBy { it.id }
        val orderIndex = pinnedOrder.withIndex().associate { it.value to it.index }
        val pinned = items.filter { it.id in pinnedIds }
            .sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
        val pinnedShown = pinned.map { it.id }.toSet()

        val apps = items.filter { it.type == ItemType.APP }
        val settings = items.filter { it.type == ItemType.SETTING }
        val custom = items
            .filter { it.type == ItemType.CUSTOM && it.id !in pinnedShown }

        val ids = apps.map { it.id }

        val recent = repository.usage.recent(ids, ids.size)
            .mapNotNull { byId[it] }
            .filterNot { it.id in pinnedShown }
            .take(15)

        val hourly = repository.usage.forCurrentHour(ids, 4).mapNotNull { byId[it] }
            .filterNot { it.id in pinnedShown }

        val frequent = repository.usage.frequent(ids, ids.size)
            .mapNotNull { byId[it] }
            .filterNot { candidate -> candidate.id in pinnedShown || (recent + hourly).any { it.id == candidate.id } }
            .take(20)

        val highlighted = pinnedShown + (recent + frequent + hourly).map { it.id }
        val appSuggestions = apps
            .filterNot { it.id in highlighted }
            .take(30)

        val sections = mutableListOf<ResultSection>()

        if (pinned.isNotEmpty()) {
            sections += ResultSection("Ancladas", pinned)
        }
        if (hourly.isNotEmpty()) {
            sections += ResultSection("Para esta hora", hourly)
        }
        if (recent.isNotEmpty()) {
            sections += ResultSection("Recientes", recent.filterNot { candidate -> hourly.any { it.id == candidate.id } })
        }
        if (frequent.isNotEmpty()) {
            sections += ResultSection("Frecuentes", frequent)
        }
        if (appSuggestions.isNotEmpty()) {
            sections += ResultSection("Aplicaciones", appSuggestions)
        }
        if (custom.isNotEmpty()) {
            sections += ResultSection("Tus atajos", custom.take(6))
        }
        if (sections.isEmpty()) {
            sections += ResultSection("Aplicaciones", apps.take(10))
        }
        if (settings.isNotEmpty()) {
            sections += ResultSection("Ajustes rápidos", settings.take(4))
        }

        return sections
    }

    private fun searchSections(
        items: List<SearchItem>,
        query: String,
        pinnedIds: Set<String>,
        pinnedOrder: List<String>
    ): List<ResultSection> {
        val scored = SearchEngine.search(items, query, repository.usage, pinnedIds)
        val orderIndex = pinnedOrder.withIndex().associate { it.value to it.index }
        val pinned = scored.filter { it.id in pinnedIds }
            .sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
        val remaining = scored.filterNot { it.id in pinnedIds }

        val groups = listOf(
            ItemType.APP to "Aplicaciones",
            ItemType.SHORTCUT to "Atajos de apps",
            ItemType.CONTACT to "Contactos",
            ItemType.SETTING to "Ajustes",
            ItemType.CUSTOM to "Tus atajos"
        )

        val sections = mutableListOf<ResultSection>()

        if (pinned.isNotEmpty()) {
            sections += ResultSection("Ancladas", pinned)
        }

        groups.forEach { (type, title) ->
            val groupItems = remaining.filter { it.type == type }.take(12)
            if (groupItems.isNotEmpty()) {
                sections += ResultSection(title, groupItems)
            }
        }

        // Si el teléfono no tiene una app con ese nombre, la búsqueda ofrece
        // instalarla desde Play Store antes que abrir la web.
        if (query.isNotBlank() && scored.none { it.type == ItemType.APP }) {
            sections += ResultSection("Play Store", listOf(com.stree.nexo.data.PlayStore.item(query)))
        }

        sections += ResultSection(
            "Web",
            listOf(
                SearchItem(
                    id = "web:$query",
                    title = "Buscar \"$query\" en Google",
                    subtitle = "Búsqueda web",
                    type = ItemType.CUSTOM,
                    keywords = "web google buscar internet",
                    intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            )
        )

        return sections
    }
}
