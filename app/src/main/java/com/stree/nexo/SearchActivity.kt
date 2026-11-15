package com.stree.nexo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.Haptics
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.search.SearchViewModel
import com.stree.nexo.ui.SearchPanel
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme

class SearchActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()
    private var pendingCallNumber: String? = null
    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingCallNumber?.let { launchCall(it, granted) }
        pendingCallNumber = null
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Prefs.setContactsEnabled(this, true)
            viewModel.refresh(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Resize keeps the pinned shortcuts above the keyboard even on devices
        // that do not dispatch IME insets while the window is adjust-nothing.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        EdgePanelService.setPanelOpen(true)

        val side = Prefs.panelSide(this)

        setContent {
            NexoTheme(darkTheme = true) {
                PanelTheme(Prefs.panelStyle(this)) {
                    val state by viewModel.state.collectAsStateWithLifecycle()

                    SearchPanel(
                    state = state,
                    side = side,
                    onQueryChange = viewModel::onQueryChange,
                    onItemClick = ::openItem,
                    onCallContact = ::callContact,
                    onTogglePin = ::togglePin,
                    onMovePinned = viewModel::movePinned,
                    onOpenFloating = ::openInFloating,
                    onAddShortcut = viewModel::addCustomShortcut,
                    onAddPinnedShortcut = viewModel::addPinnedShortcut,
                    onAddPinnedAtSlot = viewModel::addPinnedShortcutAtSlot,
                    onAssignSlot = viewModel::assignPinnedSlot,
                    onExpand = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                        finishAndRemoveTask()
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            putExtra("open_settings", true)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                        finishAndRemoveTask()
                    },
                    onRequestContacts = {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            Prefs.setContactsEnabled(this, true)
                            viewModel.refresh(force = true)
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    onClose = { finishAndRemoveTask() }
                    )
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

    private fun togglePin(item: com.stree.nexo.data.SearchItem) {
        val pinned = viewModel.togglePinned(item)

        Haptics.perform(this, strong = true)

        val message = if (pinned) "Fijado: ${item.title}" else "Quitado: ${item.title}"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openInFloating(item: com.stree.nexo.data.SearchItem) {
        viewModel.recordUsage(item.id)

        Haptics.perform(this)

        val launched = ItemLauncher.launchFloating(this, item)
        if (launched) {
            finishAndRemoveTask()
            return
        }

        val opened = ItemLauncher.launch(this, item)
        if (opened) {
            Toast.makeText(
                this,
                "Ventana flotante no disponible; abriendo normal",
                Toast.LENGTH_SHORT
            ).show()
            finishAndRemoveTask()
        } else {
            Toast.makeText(this, "No se pudo abrir ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openItem(item: com.stree.nexo.data.SearchItem) {
        Haptics.perform(this)

        if (item.type == com.stree.nexo.data.ItemType.CONTACT) {
            callContact(item)
            return
        }
        val storeQuery = com.stree.nexo.data.PlayStore.queryOf(item)
        if (storeQuery != null) {
            if (com.stree.nexo.data.PlayStore.open(this, storeQuery)) {
                finishAndRemoveTask()
            } else {
                Toast.makeText(this, "No se pudo abrir Play Store", Toast.LENGTH_SHORT).show()
            }
            return
        }
        viewModel.recordUsage(item.id)
        val launched = ItemLauncher.launch(this, item)
        if (launched) {
            finishAndRemoveTask()
        } else {
            Toast.makeText(this, "No se pudo abrir ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callContact(item: com.stree.nexo.data.SearchItem) {
        val number = item.phoneNumber?.takeIf { it.isNotBlank() } ?: return
        viewModel.recordUsage(item.id)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            launchCall(number, true)
        } else {
            pendingCallNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun launchCall(number: String, direct: Boolean) {
        runCatching {
            startActivity(Intent(
                if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                android.net.Uri.fromParts("tel", number, null)
            ))
        }.onFailure {
            Toast.makeText(this, "No se pudo iniciar la llamada", Toast.LENGTH_SHORT).show()
        }
    }
}
