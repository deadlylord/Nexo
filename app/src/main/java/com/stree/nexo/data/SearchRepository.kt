package com.stree.nexo.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.stree.nexo.R

data class IndexResult(
    val items: List<SearchItem>,
    val shortcutsBlocked: Boolean,
    val contactsPermission: Boolean
)

class SearchRepository(context: Context) {
    companion object {
        @Volatile private var cachedBase: IndexResult? = null
    }

    private val appContext = context.applicationContext
    private val appIndexer = AppIndexer(appContext)
    private val contactsIndexer = ContactsIndexer(appContext)
    private val customStore = CustomShortcutStore(appContext)

    val usage = UsageStore(appContext)

    fun cachedIndex(): IndexResult? = cachedBase?.let { cached ->
        val canShowContacts = Prefs.contactsEnabled(appContext) && contactsIndexer.hasPermission()
        cached.copy(
            items = cached.items.filterNot { it.type == ItemType.CONTACT && !canShowContacts } + customItems(),
            contactsPermission = contactsIndexer.hasPermission()
        )
    }

    fun buildIndex(): IndexResult {
        runCatching { usage.importDeviceUsage() }
        val apps = appIndexer.loadApps()
        val shortcutResult = appIndexer.loadShortcuts(apps)
        val settings = SettingsActions.items()

        val contacts = if (Prefs.contactsEnabled(appContext)) {
            contactsIndexer.load()
        } else {
            emptyList()
        }

        val custom = customStore.all().map { it.toSearchItem(appContext) }

        val base = IndexResult(
            items = apps + shortcutResult.items + settings + contacts,
            shortcutsBlocked = shortcutResult.blocked,
            contactsPermission = contactsIndexer.hasPermission()
        )
        cachedBase = base
        return base.copy(items = base.items + custom)
    }

    fun customShortcuts(): List<CustomShortcut> = customStore.all()

    val pinnedIds: Set<String>
        get() = Prefs.pinnedIds(appContext)

    val pinnedOrder: List<String>
        get() = Prefs.pinnedOrder(appContext)

    val pinnedSlots: Map<String, Int>
        get() = Prefs.pinnedSlots(appContext)

    fun assignSlot(id: String, slot: Int) = Prefs.setPinnedSlot(appContext, id, slot)

    /** Returns true when the item is now pinned, false when it was unpinned. */
    fun togglePinned(id: String): Boolean {
        val current = Prefs.pinnedIds(appContext).toMutableSet()
        val pinned = if (!current.add(id)) {
            current.remove(id)
            false
        } else {
            true
        }
        Prefs.setPinnedIds(appContext, current)
        return pinned
    }

    fun unpin(id: String) {
        val current = Prefs.pinnedIds(appContext).toMutableSet()
        if (current.remove(id)) {
            Prefs.setPinnedIds(appContext, current)
        }
    }

    fun movePinned(id: String, targetId: String) {
        if (id == targetId) return
        pinnedSlots[targetId]?.let { assignSlot(id, it) }
    }

    fun customItems(): List<SearchItem> = customStore.all().map { it.toSearchItem(appContext) }

    fun addCustomShortcut(label: String, target: String): CustomShortcut =
        customStore.add(label, target)

    fun deleteCustomShortcut(id: String) {
        customStore.delete(id)
    }
}

private fun CustomShortcut.toSearchItem(context: Context): SearchItem {
    val uri = runCatching { Uri.parse(target) }.getOrNull()
    val isPair = uri?.scheme == "nexo-pair"
    val isFolder = uri?.scheme == "nexo-folder"
    val isAppShortcut = uri?.scheme == "nexo-app"
    val isTelephone = uri?.scheme == "tel"
    val isContact = uri?.scheme == "nexo-contact"
    val isWhatsAppContact = isContact && uri?.getQueryParameter("action") == "whatsapp"
    val number = if (isContact) uri?.getQueryParameter("phone") else if (isTelephone) uri?.schemeSpecificPart else null
    val intent = if (isContact) {
        if (isWhatsAppContact) com.stree.nexo.search.WhatsAppLinks.intent(context, number.orEmpty())
        else Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number.orEmpty(), null))
    } else if (isPair) {
        Intent("com.stree.nexo.OPEN_PAIR", uri)
    } else if (isFolder) {
        Intent(context, com.stree.nexo.FolderActivity::class.java).setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    } else if (isAppShortcut) {
        Intent("com.stree.nexo.OPEN_APP", uri)
    } else if (uri != null) {
        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        null
    }
    val icon = if (isPair) {
        val first = uri.getQueryParameter("first")
        val second = uri.getQueryParameter("second")
        if (!first.isNullOrBlank() && !second.isNullOrBlank()) {
            runCatching {
                AppPairDrawable(
                    context.packageManager.getApplicationIcon(first),
                    context.packageManager.getApplicationIcon(second)
                )
            }.getOrNull()
        } else null
    } else if (isFolder) {
        val icons = uri?.getQueryParameters("app").orEmpty().take(4).mapNotNull { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
        }
        if (icons.isEmpty()) ContextCompat.getDrawable(context, R.drawable.ic_folder)
        else FolderPreviewDrawable(icons)
    } else if (isAppShortcut) {
        uri?.getQueryParameter("package")?.let { packageName ->
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    } else null

    return SearchItem(
        id = "custom:$id",
        title = label,
        subtitle = when {
            isPair -> "Pareja de apps · Pantalla dividida"
            isFolder -> "Carpeta de aplicaciones"
            isAppShortcut -> "Aplicación"
            isTelephone -> uri?.schemeSpecificPart.orEmpty()
            isContact -> number.orEmpty()
            else -> target
        },
        type = if (isTelephone || (isContact && !isWhatsAppContact)) ItemType.CONTACT else ItemType.CUSTOM,
        keywords = "$label $target",
        icon = icon,
        intent = intent,
        phoneNumber = number,
        photoUri = if (isContact) uri?.getQueryParameter("photo")?.takeIf { it.isNotBlank() } else null
    )
}
