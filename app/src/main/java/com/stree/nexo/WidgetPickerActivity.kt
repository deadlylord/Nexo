package com.stree.nexo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.widget.WidgetBinding
import com.stree.nexo.widget.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Selector y puente de autorización/configuración de widgets.
 * Usa AppWidgetHost.startAppWidgetConfigureActivityForResult para soportar
 * widgets modernos (Google Home, Keep, Samsung, etc.) cuya actividad de
 * configuración no es exportada o es opcional.
 */
class WidgetPickerActivity : ComponentActivity() {
    private var pendingId = -1
    private var reconfigureMode = false
    private var configureLaunchedAtMs = 0L
    private var listening = false
    private lateinit var itemId: String
    private val manager by lazy { AppWidgetManager.getInstance(this) }

    private val bind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && manager.getAppWidgetInfo(pendingId) != null) {
            applyInitialOptions(pendingId)
            configure()
        } else {
            discard()
        }
    }

    private val fallbackConfiguration = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleConfigureResult(result.resultCode)
    }

    private val systemPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && manager.getAppWidgetInfo(pendingId) != null) {
            applyInitialOptions(pendingId)
            configure()
        } else {
            discard()
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Este selector no permite añadir aquí. Elige una tarjeta de la lista.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = intent.getStringExtra("item_id") ?: run { finish(); return }
        reconfigureMode = intent.getBooleanExtra(EXTRA_RECONFIGURE, false)
        pendingId = savedInstanceState?.getInt("pending_id", -1) ?: -1

        // Modo reconfiguración directa de un widget ya existente
        if (reconfigureMode) {
            val existingId = intent.getIntExtra(EXTRA_EXISTING_WIDGET_ID, -1)
            if (existingId == -1 || manager.getAppWidgetInfo(existingId) == null) {
                Toast.makeText(this, "Este widget no se puede reconfigurar", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            pendingId = existingId
            if (savedInstanceState == null) {
                launchConfigureFor(existingId, allowOptionalSkip = false)
            }
            return
        }

        val providers = manager.installedProviders.sortedBy { it.loadLabel(packageManager).toString() }
        val applicationNames = providers.map { it.provider.packageName }.distinct().associateWith { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }
        setContent {
            NexoTheme {
                var filter by remember { mutableStateOf("") }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                        Text(
                            when {
                                itemId.startsWith("spotlight-widget:") -> "Añadir widget al panel Spotlight"
                                itemId.startsWith("panel-widget:") -> "Añadir al panel Widgets"
                                else -> "Widget al deslizar arriba"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text("Elige un widget. Android puede pedir autorización y configuración.")
                        TextButton(onClick = { openSystemPicker() }) { Text("Usar selector del teléfono") }
                        OutlinedTextField(filter, { filter = it }, label = { Text("Buscar widget o aplicación") }, modifier = Modifier.fillMaxWidth())
                        LazyColumn(Modifier.weight(1f)) {
                            items(providers.filter {
                                it.loadLabel(packageManager).toString().contains(filter, true) ||
                                    applicationNames[it.provider.packageName].orEmpty().contains(filter, true) ||
                                    it.provider.packageName.contains(filter, true)
                            }, key = { "${it.profile}:${it.provider.flattenToString()}" }) { provider ->
                                WidgetChoiceCard(provider) { select(provider) }
                            }
                        }
                        if (WidgetStore.get(this@WidgetPickerActivity, itemId) != null) {
                            TextButton(onClick = { WidgetStore.remove(this@WidgetPickerActivity, itemId); finish() }) { Text("Quitar widget") }
                        }
                        TextButton(onClick = { finish() }) { Text("Cancelar") }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!listening) {
            WidgetStore.startListening(this)
            listening = true
        }
    }

    override fun onStop() {
        if (listening) {
            WidgetStore.stopListening(this)
            listening = false
        }
        super.onStop()
    }

    private fun defaultOptionsFor(info: AppWidgetProviderInfo?): Bundle {
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val w = if (itemId.startsWith("spotlight-widget:")) {
            (resources.displayMetrics.widthPixels / density - 32).toInt().coerceAtLeast(280)
        } else {
            ((info?.minWidth ?: 220) / density + 24).toInt().coerceIn(180, 360)
        }
        val h = ((info?.minHeight ?: 160) / density + 24).toInt().coerceIn(120, 480)
        return WidgetStore.buildHostOptions(w, h)
    }

    private fun applyInitialOptions(appWidgetId: Int) {
        val info = manager.getAppWidgetInfo(appWidgetId)
        runCatching { manager.updateAppWidgetOptions(appWidgetId, defaultOptionsFor(info)) }
    }

    private fun openSystemPicker() {
        if (pendingId != -1) discard()
        if (!WidgetStore.isEmbeddedPanelWidget(itemId) && !android.provider.Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            }
            return
        }
        runCatching {
            pendingId = WidgetStore.host(this).allocateAppWidgetId()
            @Suppress("DEPRECATION")
            val pick = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
            systemPicker.launch(pick)
        }.onFailure {
            discard()
            Toast.makeText(this, "El teléfono no ofrece ese selector. Usa las tarjetas con vista previa.", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun WidgetChoiceCard(provider: AppWidgetProviderInfo, onClick: () -> Unit) {
        val density = resources.displayMetrics.densityDpi
        val preview by produceState<android.graphics.Bitmap?>(null, provider.provider) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    provider.loadPreviewImage(this@WidgetPickerActivity, density)?.let { drawable ->
                        val width = drawable.intrinsicWidth.coerceAtLeast(1)
                        val height = drawable.intrinsicHeight.coerceAtLeast(1)
                        val scale = minOf(1f, 600f / width, 240f / height)
                        drawable.toBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                    }
                }.getOrNull()
            }
        }
        val icon = remember(provider.provider) {
            runCatching {
                (provider.loadIcon(this, density) ?: packageManager.getApplicationIcon(provider.provider.packageName))
                    .toBitmap(72, 72).asImageBitmap()
            }.getOrNull()
        }
        val appName = remember(provider.provider) {
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(provider.provider.packageName, 0)).toString() }
                .getOrDefault(provider.provider.packageName)
        }
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { Image(it, appName, Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(provider.loadLabel(packageManager).toString(), style = MaterialTheme.typography.titleSmall)
                        Text(appName, style = MaterialTheme.typography.bodySmall)
                    }
                }
                preview?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), "Vista previa del widget", Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp), contentScale = ContentScale.Fit)
                } ?: Text(
                    "Sin vista previa del proveedor · mínimo aprox. ${(provider.minWidth / resources.displayMetrics.density).toInt()} × ${(provider.minHeight / resources.displayMetrics.density).toInt()} dp",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    private fun select(provider: AppWidgetProviderInfo) {
        if (!WidgetStore.isEmbeddedPanelWidget(itemId) && !android.provider.Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            }
            Toast.makeText(this, "Permite la superposición y vuelve a elegir el widget", Toast.LENGTH_LONG).show()
            return
        }
        if (pendingId != -1) discard()
        runCatching {
            pendingId = WidgetStore.host(this).allocateAppWidgetId()
            val options = defaultOptionsFor(provider)
            val bound = manager.bindAppWidgetIdIfAllowed(pendingId, provider.profile, provider.provider, options)
            if (bound) {
                applyInitialOptions(pendingId)
                configure()
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                bind.launch(bindIntent)
            }
        }.onFailure {
            discard()
            Toast.makeText(this, "No se pudo añadir este widget", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isConfigOptional(info: AppWidgetProviderInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0

    private fun configure() {
        val info = manager.getAppWidgetInfo(pendingId) ?: run { discard(); return }
        if (info.configure != null) {
            launchConfigureFor(pendingId, allowOptionalSkip = isConfigOptional(info))
        } else {
            commit()
        }
    }

    private fun launchConfigureFor(appWidgetId: Int, allowOptionalSkip: Boolean) {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: run {
            if (reconfigureMode) finish() else discard()
            return
        }
        configureLaunchedAtMs = SystemClock.elapsedRealtime()
        // 1. Método oficial de Android (pasa por AppWidgetService, permite configurar Google Home
        //    y widgets modernos cuya Activity de configuración tiene exported="false").
        val launchedViaHost = runCatching {
            WidgetStore.host(this).startAppWidgetConfigureActivityForResult(
                this,
                appWidgetId,
                0,
                REQUEST_CONFIGURE,
                null
            )
            true
        }.getOrDefault(false)
        if (launchedViaHost) return

        // 2. Respaldo clásico si el host falló en esta ROM
        val launchedDirect = info.configure?.let { component ->
            runCatching {
                fallbackConfiguration.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(component)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                true
            }.getOrDefault(false)
        } ?: false
        if (launchedDirect) return

        // 3. Si la configuración es opcional (Android 12+), añadimos el widget con valores por defecto
        if (!reconfigureMode && allowOptionalSkip) {
            commit()
        } else if (reconfigureMode) {
            Toast.makeText(this, "Este widget no permite reconfigurarse aquí", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            discard()
            Toast.makeText(this, "No se pudo abrir la configuración de este widget", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) {
            handleConfigureResult(resultCode)
        }
    }

    private fun handleConfigureResult(resultCode: Int) {
        if (reconfigureMode) {
            pendingId = -1
            finish()
            return
        }
        val info = manager.getAppWidgetInfo(pendingId)
        val elapsed = SystemClock.elapsedRealtime() - configureLaunchedAtMs
        when {
            resultCode == RESULT_OK -> commit()
            // Algunos widgets modernos con configuración opcional se autocerraron de inmediato
            // sin devolver RESULT_OK: los confirmamos con su configuración por defecto.
            info != null && isConfigOptional(info) && elapsed < 500L -> commit()
            else -> discard()
        }
    }

    private fun commit() {
        val info = manager.getAppWidgetInfo(pendingId) ?: run { discard(); return }
        val old = WidgetStore.get(this, itemId)
        if (old != null && old.id != pendingId) {
            runCatching { WidgetStore.host(this).deleteAppWidgetId(old.id) }
        }
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val defaultWidth = if (itemId.startsWith("spotlight-widget:")) {
            (resources.displayMetrics.widthPixels / density - 32).toInt().coerceAtLeast(280)
        } else {
            (info.minWidth / density + 24).toInt().coerceIn(160, 360)
        }
        val defaultHeight = if (itemId.startsWith("spotlight-widget:")) {
            (info.minHeight / density + 24).toInt().coerceIn(140, 520)
        } else {
            (info.minHeight / density + 24).toInt().coerceIn(120, 440)
        }
        WidgetStore.put(
            this,
            itemId,
            WidgetBinding(
                id = pendingId,
                width = old?.width ?: defaultWidth,
                height = old?.height ?: defaultHeight
            )
        )
        pendingId = -1
        if (!WidgetStore.isEmbeddedPanelWidget(itemId)) EdgePanelService.showWidget(this, itemId)
        finish()
    }

    private fun discard() {
        if (!reconfigureMode && pendingId != -1) {
            runCatching { WidgetStore.host(this).deleteAppWidgetId(pendingId) }
        }
        pendingId = -1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pending_id", pendingId)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        EdgePanelService.setPanelOpen(true)
    }

    override fun onPause() {
        EdgePanelService.setPanelOpen(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing && !reconfigureMode) discard()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CONFIGURE = 7701
        private const val EXTRA_RECONFIGURE = "reconfigure"
        private const val EXTRA_EXISTING_WIDGET_ID = "existing_widget_id"

        fun reconfigureIntent(context: Context, itemId: String, appWidgetId: Int): Intent =
            Intent(context, WidgetPickerActivity::class.java)
                .putExtra("item_id", itemId)
                .putExtra(EXTRA_RECONFIGURE, true)
                .putExtra(EXTRA_EXISTING_WIDGET_ID, appWidgetId)
    }
}
