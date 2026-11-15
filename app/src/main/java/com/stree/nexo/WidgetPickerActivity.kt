package com.stree.nexo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.stree.nexo.widget.WidgetBinding
import com.stree.nexo.widget.WidgetStore
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.theme.NexoTheme

/** Conserva el ID asignado hasta que terminan la autorización y configuración. */
class WidgetPickerActivity : ComponentActivity() {
    private var pendingId = -1
    private lateinit var itemId: String
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private val bind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && manager.getAppWidgetInfo(pendingId) != null) configure()
        else discard()
    }
    private val configuration = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) commit() else discard()
    }
    private val systemPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && manager.getAppWidgetInfo(pendingId) != null) configure()
        else {
            discard()
            if (result.resultCode == RESULT_OK) Toast.makeText(this, "Este selector no permite añadir aquí. Elige una tarjeta de la lista.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = intent.getStringExtra("item_id") ?: run { finish(); return }
        pendingId = savedInstanceState?.getInt("pending_id", -1) ?: -1
        val providers = manager.installedProviders.sortedBy { it.loadLabel(packageManager).toString() }
        val applicationNames = providers.map { it.provider.packageName }.distinct().associateWith { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }
        setContent {
            NexoTheme {
                var filter by remember { mutableStateOf("") }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                        Text(if (itemId.startsWith("panel-widget:")) "Añadir al panel Widgets" else "Widget al deslizar arriba", style = MaterialTheme.typography.titleLarge)
                        Text("Elige un widget. Android puede pedir autorización y configuración.")
                        TextButton(onClick = { openSystemPicker() }) { Text("Usar selector del teléfono") }
                        OutlinedTextField(filter, { filter = it }, label = { Text("Buscar widget o aplicación") }, modifier = Modifier.fillMaxWidth())
                        LazyColumn(Modifier.weight(1f)) {
                            items(providers.filter {
                                it.loadLabel(packageManager).toString().contains(filter, true) ||
                                    applicationNames[it.provider.packageName].orEmpty().contains(filter, true) ||
                                    it.provider.packageName.contains(filter, true)
                            }, key = { it.provider.flattenToString() }) { provider ->
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

    private fun openSystemPicker() {
        if (pendingId != -1) return
        if (!itemId.startsWith("panel-widget:") && !android.provider.Settings.canDrawOverlays(this)) {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName"))) }
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
                runCatching { provider.loadPreviewImage(this@WidgetPickerActivity, density)?.let { drawable ->
                    val width = drawable.intrinsicWidth.coerceAtLeast(1)
                    val height = drawable.intrinsicHeight.coerceAtLeast(1)
                    val scale = minOf(1f, 600f / width, 240f / height)
                    drawable.toBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                } }.getOrNull()
            }
        }
        val icon = remember(provider.provider) {
            runCatching { (provider.loadIcon(this, density) ?: packageManager.getApplicationIcon(provider.provider.packageName))
                .toBitmap(72, 72).asImageBitmap() }.getOrNull()
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
                } ?: Text("Sin vista previa del proveedor · mínimo aprox. ${(provider.minWidth / resources.displayMetrics.density).toInt()} × ${(provider.minHeight / resources.displayMetrics.density).toInt()} dp", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    private fun select(provider: AppWidgetProviderInfo) {
        if (!itemId.startsWith("panel-widget:") && !android.provider.Settings.canDrawOverlays(this)) {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName"))) }
            Toast.makeText(this, "Permite la superposición y vuelve a elegir el widget", Toast.LENGTH_LONG).show()
            return
        }
        if (pendingId != -1) return
        runCatching {
            pendingId = WidgetStore.host(this).allocateAppWidgetId()
            if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.provider)) configure()
            else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider))
        }.onFailure { discard(); Toast.makeText(this, "No se pudo añadir este widget", Toast.LENGTH_SHORT).show() }
    }

    private fun configure() {
        val info = manager.getAppWidgetInfo(pendingId) ?: run { discard(); return }
        if (info.configure != null) {
            runCatching { configuration.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)) }
                .onFailure { discard(); Toast.makeText(this, "No se pudo configurar el widget", Toast.LENGTH_SHORT).show() }
        } else commit()
    }

    private fun commit() {
        val info = manager.getAppWidgetInfo(pendingId) ?: run { discard(); return }
        val old = WidgetStore.get(this, itemId)
        if (old != null) runCatching { WidgetStore.host(this).deleteAppWidgetId(old.id) }
        val density = resources.displayMetrics.density
        WidgetStore.put(this, itemId, WidgetBinding(pendingId,
            old?.width ?: (info.minWidth / density + 24).toInt().coerceIn(160, 360),
            old?.height ?: (info.minHeight / density + 24).toInt().coerceIn(120, 440)))
        pendingId = -1
        if (!itemId.startsWith("panel-widget:")) EdgePanelService.showWidget(this, itemId)
        finish()
    }

    private fun discard() {
        if (pendingId != -1) runCatching { WidgetStore.host(this).deleteAppWidgetId(pendingId) }
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
        if (isFinishing) discard()
        super.onDestroy()
    }
}
