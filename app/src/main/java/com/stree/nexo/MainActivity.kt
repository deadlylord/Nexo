package com.stree.nexo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.data.Haptics
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.search.ItemLauncher
import com.stree.nexo.search.ContactCalls
import com.stree.nexo.search.SearchViewModel
import com.stree.nexo.ui.MainScreen
import com.stree.nexo.ui.theme.NexoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()
    private val contactCalls = ContactCalls(this)

    private var panelEnabledState by mutableStateOf(false)
    private var panelSideState by mutableStateOf(PanelSide.END)
    private var contactsEnabledState by mutableStateOf(false)
    private var hapticsState by mutableStateOf(true)
    private var settingsRequest by mutableStateOf(0)
    private var spotlightRequest by mutableStateOf(0)
    private var stoppedInBackground = false

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Prefs.setContactsEnabled(this, true)
            contactsEnabledState = true
            viewModel.refresh()
        } else {
            Prefs.setContactsEnabled(this, false)
            contactsEnabledState = false
            Toast.makeText(
                this,
                "Sin permiso de contactos no se pueden buscar contactos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Prefs.setPanelEnabled(this, true)
            panelEnabledState = true
            startPanelService()
            requestNotificationPermission()
        } else {
            Prefs.setPanelEnabled(this, false)
            panelEnabledState = false
            Toast.makeText(
                this,
                "Nexo necesita el permiso para mostrarse sobre otras apps",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Haptics.perform(this)
        syncFromPreferences()

        if (intent.getBooleanExtra("open_settings", false)) {
            settingsRequest++
        } else if (savedInstanceState == null) {
            spotlightRequest++
        }

        setContent {
            NexoTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                MainScreen(
                    settingsRequest = settingsRequest,
                    spotlightRequest = spotlightRequest,
                    state = state,
                    panelEnabled = panelEnabledState,
                    panelSide = panelSideState,
                    contactsEnabled = contactsEnabledState,
                    hapticsEnabled = hapticsState,
                    onQueryChange = viewModel::onQueryChange,
                    onItemClick = ::openItem,
                    onTogglePin = ::togglePin,
                    onOpenFloating = ::openInFloating,
                    onRequestContacts = { requestContactsPermission() },
                    onAddShortcut = { label, target ->
                        if (target.startsWith("nexo-contact://")) {
                            viewModel.addPinnedShortcut(label, target)
                        } else {
                            viewModel.addCustomShortcut(label, target)
                        }
                    },
                    onDeleteShortcut = viewModel::deleteCustomShortcut,
                    onTogglePanel = ::togglePanel,
                    onSideChange = ::changePanelSide,
                    onToggleContacts = ::toggleContacts,
                    onToggleHaptics = ::toggleHaptics,
                    onOpenOverlayPermission = ::openOverlayPermission,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onOpenHomeSettings = ::openHomeSettings,
                    onRefresh = {
                        viewModel.refresh(force = true)
                        Toast.makeText(this, "Actualizando índice…", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncFromPreferences()
        viewModel.refresh()

        if (stoppedInBackground) {
            stoppedInBackground = false
            if (!intent.getBooleanExtra("open_settings", false)) {
                spotlightRequest++
            }
        }

        if (panelEnabledState) {
            startPanelService()
        }
        EdgePanelService.setHandleVisible(false)
        consumePanelCall()
    }

    override fun onStop() {
        stoppedInBackground = true
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_settings", false)) {
            settingsRequest++
        } else if (intent.getStringExtra("call_contact_number") == null) {
            spotlightRequest++
        }
        window.decorView.post { consumePanelCall() }
    }

    private fun consumePanelCall() {
        val number = intent.getStringExtra("call_contact_number") ?: return
        val name = intent.getStringExtra("call_contact_name").orEmpty()
        intent.removeExtra("call_contact_number")
        intent.removeExtra("call_contact_name")
        if (number.isBlank()) return
        window.decorView.post {
            contactCalls.handle(com.stree.nexo.data.SearchItem(
                id = "panel-call:$number", title = name, subtitle = number,
                type = com.stree.nexo.data.ItemType.CONTACT, phoneNumber = number
            ))
        }
    }

    override fun onPause() {
        EdgePanelService.setHandleVisible(true)
        super.onPause()
    }

    private fun syncFromPreferences() {
        panelEnabledState = Prefs.isPanelEnabled(this) && Settings.canDrawOverlays(this)
        panelSideState = Prefs.panelSide(this)
        contactsEnabledState = Prefs.contactsEnabled(this) && hasContactsPermission()
        hapticsState = Prefs.hapticsEnabled(this)
    }

    private fun togglePin(item: com.stree.nexo.data.SearchItem) {
        val pinned = viewModel.togglePinned(item)

        if (hapticsState) Haptics.perform(this, strong = true)

        val message = if (pinned) "Fijado: ${item.title}" else "Quitado: ${item.title}"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openInFloating(item: com.stree.nexo.data.SearchItem) {
        viewModel.recordUsage(item.id)

        if (hapticsState) Haptics.perform(this)

        if (!ItemLauncher.launchFloating(this, item)) {
            val opened = ItemLauncher.launch(this, item)
            val message = if (opened) {
                "Ventana flotante no disponible; abriendo normal"
            } else {
                "No se pudo abrir ${item.title}"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openItem(item: com.stree.nexo.data.SearchItem) {
        viewModel.recordUsage(item.id)

        if (hapticsState) Haptics.perform(this)

        if (contactCalls.handle(item)) return
        val launched = ItemLauncher.launch(this, item)
        if (!launched) {
            Toast.makeText(this, "No se pudo abrir ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePanel(enabled: Boolean) {
        if (!enabled) {
            Prefs.setPanelEnabled(this, false)
            panelEnabledState = false
            EdgePanelService.stop(this)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            runCatching {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }.onFailure {
                Toast.makeText(
                    this,
                    "Abre los ajustes y permite \"Mostrar sobre otras apps\"",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        Prefs.setPanelEnabled(this, true)
        panelEnabledState = true
        startPanelService()
        requestNotificationPermission()
        requestBatteryExemptionIfNeeded()
    }

    private fun changePanelSide(side: PanelSide) {
        Prefs.setPanelSide(this, side)
        panelSideState = side

        if (Prefs.isPanelEnabled(this) && Settings.canDrawOverlays(this)) {
            EdgePanelService.stop(this)
            window.decorView.postDelayed({ startPanelService() }, 220)
        }
    }

    private fun toggleContacts(enabled: Boolean) {
        if (!enabled) {
            Prefs.setContactsEnabled(this, false)
            contactsEnabledState = false
            viewModel.refresh()
            return
        }

        if (hasContactsPermission()) {
            Prefs.setContactsEnabled(this, true)
            contactsEnabledState = true
            viewModel.refresh()
        } else {
            requestContactsPermission()
        }
    }

    private fun toggleHaptics(enabled: Boolean) {
        Prefs.setHapticsEnabled(this, enabled)
        hapticsState = enabled
    }

    private fun requestContactsPermission() {
        if (hasContactsPermission()) {
            Prefs.setContactsEnabled(this, true)
            contactsEnabledState = true
            viewModel.refresh()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startPanelService() {
        runCatching { EdgePanelService.start(this) }
            .onFailure {
                Toast.makeText(
                    this,
                    "No se pudo iniciar el panel lateral",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun openOverlayPermission() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    /**
     * Android may kill the foreground service while it is idle in the background,
     * and the activator bar disappears with it. Asking for the battery exemption
     * is the only supported way to keep it alive on most manufacturers.
     */
    private fun requestBatteryExemptionIfNeeded() {
        if (isBatteryUnrestricted()) return
        requestBatteryExemption()
    }

    private fun requestBatteryExemption() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure {
                    Toast.makeText(
                        this,
                        "Abre los ajustes de batería y elige Sin restricciones para Nexo",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        val manager = getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }
}
