package com.stree.nexo

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stree.nexo.data.ClipboardHistoryStore
import com.stree.nexo.data.Haptics
import com.stree.nexo.data.PanelSide
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.ui.PanelMotion
import com.stree.nexo.ui.PanelSurface
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation
import com.stree.nexo.ui.gradientTint
import com.stree.nexo.ui.theme.NexoTheme
import com.stree.nexo.ui.theme.PanelTheme
import com.stree.nexo.ui.theme.visualSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mini-tarjeta lateral rápida para actuar sobre texto copiado o la última captura. */
class QuickClipActivity : ComponentActivity() {

    private var refreshTick by mutableIntStateOf(0)
    private var pendingDeleteUri: Uri? = null

    private val imagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ClipboardHistoryStore.queryLatestScreenshot(this)
            EdgePanelService.refresh(this)
        }
        refreshTick++
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingDeleteUri?.let { ClipboardHistoryStore.clearScreenshot(it) }
            ClipboardHistoryStore.queryLatestScreenshot(this)
            Toast.makeText(this, "Captura eliminada", Toast.LENGTH_SHORT).show()
            refreshTick++
        }
        pendingDeleteUri = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ClipboardHistoryStore.readSystemClipboard(this)
            if (ClipboardHistoryStore.hasScreenshotPermission(this)) {
                ClipboardHistoryStore.queryLatestScreenshot(this)
            }
            refreshTick++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        disableSystemTransitions()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val initialTab = if (intent.getStringExtra("mode") == "screenshot") 1 else 0
        val side = Prefs.panelSide(this)

        setContent {
            NexoTheme(darkTheme = true) {
                val style = remember(Prefs.themeRevision) { Prefs.panelStyle(this) }
                val light = remember(Prefs.themeRevision) { Prefs.panelLightBackground(this) }
                val themeSpec = remember(style, light) { style.visualSpec(light = light) }
                val iconBrush = remember(themeSpec) { themeSpec.actionIconBrush }

                PanelTheme(style) {
                    val scope = rememberCoroutineScope()
                    var shown by remember { mutableStateOf(false) }
                    var closing by remember { mutableStateOf(false) }
                    var selectedTab by remember { mutableIntStateOf(initialTab) }
                    val clips by ClipboardHistoryStore.clips.collectAsStateWithLifecycle()
                    val latestShot by ClipboardHistoryStore.latestScreenshot.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        shown = true
                        delay(120)
                        ClipboardHistoryStore.readSystemClipboard(this@QuickClipActivity)
                        if (ClipboardHistoryStore.hasScreenshotPermission(this@QuickClipActivity)) {
                            withContext(Dispatchers.IO) {
                                ClipboardHistoryStore.queryLatestScreenshot(this@QuickClipActivity)
                            }
                        }
                        refreshTick++
                    }

                    val dismiss: () -> Unit = {
                        if (!closing) {
                            closing = true
                            shown = false
                            scope.launch {
                                delay(PanelMotion.EXIT_DURATION_MS.toLong())
                                finishNoSystemAnimation(removeTask = true)
                            }
                        }
                    }

                    BackHandler { dismiss() }

                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (shown) 0.22f else 0f,
                        animationSpec = tween(
                            durationMillis = if (shown) PanelMotion.SCRIM_ENTER_MS else PanelMotion.SCRIM_EXIT_MS,
                            easing = if (shown) PanelMotion.EnterEasing else PanelMotion.ExitEasing
                        ),
                        label = "clipScrim"
                    )

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = dismiss
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
                                contentAlignment = if (side == PanelSide.END) Alignment.TopEnd else Alignment.TopStart
                            ) {
                                val density = LocalDensity.current
                                val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                                val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                                val topSafeMargin = maxOf(statusBarTop, 24.dp) + 8.dp
                                val bottomSafeMargin = navBarBottom + 16.dp

                                val panelWidth = minOf(maxWidth * 0.72f, 296.dp)
                                val panelHeight = minOf(maxHeight * 0.42f, 344.dp).coerceAtLeast(258.dp)

                                val handleHeight = Prefs.activationHeight(this@QuickClipActivity).dp
                                    .coerceAtMost((maxHeight - 56.dp).coerceAtLeast(48.dp))
                                val maxHandleOffset = ((maxHeight / 2f) - (handleHeight / 2f) - 28.dp).coerceAtLeast(0.dp)
                                val triggerCenterY = (maxHeight / 2f) +
                                    maxHandleOffset * Prefs.panelYFraction(this@QuickClipActivity)
                                val maxTopMargin = (maxHeight - bottomSafeMargin - panelHeight - 8.dp)
                                    .coerceAtLeast(topSafeMargin)
                                val panelTopMargin = (triggerCenterY - panelHeight / 2f)
                                    .coerceIn(topSafeMargin, maxTopMargin)

                                PanelSurface(
                                    modifier = Modifier
                                        .padding(top = panelTopMargin)
                                        .padding(horizontal = 8.dp)
                                        .width(panelWidth)
                                        .height(panelHeight)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {}
                                        )
                                ) {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        AnimatedContent(
                                            targetState = selectedTab,
                                            transitionSpec = {
                                                fadeIn(tween(150, easing = PanelMotion.StandardEasing)) togetherWith
                                                    fadeOut(tween(110, easing = PanelMotion.StandardEasing))
                                            },
                                            label = "clipTabContent"
                                        ) { tab ->
                                            if (tab == 0) {
                                                ClipboardTabContent(
                                                    clips = clips,
                                                    themeSurface = themeSpec.surfaceVariant.copy(alpha = 0.44f),
                                                    themeBorder = themeSpec.outlineVariant.copy(alpha = 0.30f),
                                                    iconBrush = iconBrush,
                                                    onCopy = { text ->
                                                        Haptics.perform(this@QuickClipActivity)
                                                        ClipboardHistoryStore.copyToClipboard(this@QuickClipActivity, text)
                                                        Toast.makeText(this@QuickClipActivity, "Copiado", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onShare = { text ->
                                                        Haptics.perform(this@QuickClipActivity)
                                                        shareText(text)
                                                        dismiss()
                                                    },
                                                    onSearch = { text ->
                                                        Haptics.perform(this@QuickClipActivity)
                                                        searchWeb(text)
                                                        dismiss()
                                                    },
                                                    onOpenUrl = { url ->
                                                        Haptics.perform(this@QuickClipActivity)
                                                        openUrl(url)
                                                        dismiss()
                                                    }
                                                )
                                            } else {
                                                val hasPerm = remember(refreshTick) {
                                                    ClipboardHistoryStore.hasScreenshotPermission(this@QuickClipActivity)
                                                }
                                                ScreenshotTabContent(
                                                    hasPermission = hasPerm,
                                                    entry = latestShot,
                                                    themeSurface = themeSpec.surfaceVariant.copy(alpha = 0.44f),
                                                    themeBorder = themeSpec.outlineVariant.copy(alpha = 0.30f),
                                                    iconBrush = iconBrush,
                                                    onRequestPermission = {
                                                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                            Manifest.permission.READ_MEDIA_IMAGES
                                                        } else {
                                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                                        }
                                                        imagePermissionLauncher.launch(perm)
                                                    },
                                                    onView = { uri ->
                                                        Haptics.perform(this@QuickClipActivity)
                                                        viewImage(uri)
                                                        dismiss()
                                                    },
                                                    onShare = { uri ->
                                                        Haptics.perform(this@QuickClipActivity)
                                                        shareImage(uri)
                                                        dismiss()
                                                    },
                                                    onDelete = { uri ->
                                                        Haptics.perform(this@QuickClipActivity, strong = true)
                                                        deleteImage(uri)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                        thickness = 0.5.dp,
                                        color = themeSpec.outlineVariant.copy(alpha = 0.28f)
                                    )
                                    val activePillColor = themeSpec.primary.copy(alpha = 0.16f)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(
                                            Icons.Filled.ContentPaste to "Portapapeles",
                                            Icons.Filled.Image to "Última captura"
                                        ).forEachIndexed { idx, (icon, desc) ->
                                            IconButton(
                                                onClick = {
                                                    Haptics.perform(this@QuickClipActivity)
                                                    selectedTab = idx
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(if (selectedTab == idx) activePillColor else Color.Transparent),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = desc,
                                                        modifier = Modifier.size(20.dp).gradientTint(iconBrush),
                                                        tint = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        EdgePanelService.setPanelOpen(true)
    }

    override fun onPause() {
        EdgePanelService.setPanelOpen(false)
        super.onPause()
    }

    private fun shareText(text: String) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, "Compartir texto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun searchWeb(query: String) {
        runCatching {
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query.take(240))}")
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openUrl(url: String) {
        val normalized = if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun viewImage(uri: Uri) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure {
            Toast.makeText(this, "No se pudo abrir la captura", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(uri: Uri) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Compartir captura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun deleteImage(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                pendingDeleteUri = uri
                val sender = MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }.onFailure {
                pendingDeleteUri = null
                Toast.makeText(this, "No se pudo eliminar la captura", Toast.LENGTH_SHORT).show()
            }
        } else {
            val deleted = runCatching { contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            if (deleted) {
                ClipboardHistoryStore.clearScreenshot(uri)
                ClipboardHistoryStore.queryLatestScreenshot(this)
                Toast.makeText(this, "Captura eliminada", Toast.LENGTH_SHORT).show()
                refreshTick++
            } else {
                Toast.makeText(this, "No se pudo eliminar la captura", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private val urlPattern = Regex("""(https?://\S+|www\.\S+)""", RegexOption.IGNORE_CASE)

@Composable
private fun ClipboardTabContent(
    clips: List<com.stree.nexo.data.ClipEntry>,
    themeSurface: Color,
    themeBorder: Color,
    iconBrush: androidx.compose.ui.graphics.Brush,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val active = clips.firstOrNull()?.text
    if (active == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.ContentPaste, null, Modifier.size(28.dp).gradientTint(iconBrush), tint = Color.White)
                Text(
                    "Copia cualquier texto y aparecerá aquí para compartirlo o buscarlo al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val detectedUrl = remember(active) { urlPattern.find(active)?.value }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = themeSurface,
            border = BorderStroke(0.9.dp, themeBorder)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = active,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (detectedUrl != null) {
                        IconButton(onClick = { onOpenUrl(detectedUrl) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.OpenInBrowser, "Abrir enlace", Modifier.size(20.dp).gradientTint(iconBrush), tint = Color.White)
                        }
                    }
                    IconButton(onClick = { onSearch(active) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Search, "Buscar en web", Modifier.size(20.dp).gradientTint(iconBrush), tint = Color.White)
                    }
                    IconButton(onClick = { onShare(active) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Share, "Compartir", Modifier.size(20.dp).gradientTint(iconBrush), tint = Color.White)
                    }
                    IconButton(onClick = { onCopy(active) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, "Copiar", Modifier.size(19.dp).gradientTint(iconBrush), tint = Color.White)
                    }
                }
            }
        }

        if (clips.size > 1) {
            Text(
                text = "RECIENTES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(clips.drop(1), key = { "${it.timestamp}:${it.text.hashCode()}" }) { item ->
                    Surface(
                        onClick = { onCopy(item.text) },
                        shape = RoundedCornerShape(12.dp),
                        color = themeSurface.copy(alpha = 0.65f),
                        border = BorderStroke(0.6.dp, themeBorder.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.ContentCopy,
                                "Copiar",
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotTabContent(
    hasPermission: Boolean,
    entry: com.stree.nexo.data.ScreenshotEntry?,
    themeSurface: Color,
    themeBorder: Color,
    iconBrush: androidx.compose.ui.graphics.Brush,
    onRequestPermission: () -> Unit,
    onView: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onDelete: (Uri) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!hasPermission) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Image, null, Modifier.size(30.dp).gradientTint(iconBrush), tint = Color.White)
                Text(
                    "Autoriza el acceso a imágenes para previsualizar, compartir o borrar tu última captura al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onRequestPermission) {
                    Text("Permitir capturas")
                }
            }
        }
        return
    }

    if (entry == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Image, null, Modifier.size(28.dp).gradientTint(iconBrush), tint = Color.White)
                Text(
                    "No se encontraron capturas recientes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val previewBitmap by produceState<Bitmap?>(null, entry.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, entry.uri)
                    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        val maxDim = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                        val sample = (maxDim / 520).coerceAtLeast(1)
                        decoder.setTargetSampleSize(sample)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, entry.uri)
                }
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = { onView(entry.uri) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = themeSurface,
            border = BorderStroke(0.9.dp, themeBorder)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = entry.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { onView(entry.uri) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = themeSurface,
                border = BorderStroke(0.8.dp, themeBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.OpenInNew, "Ver captura", Modifier.size(20.dp).gradientTint(iconBrush), tint = Color.White)
                }
            }
            Surface(
                onClick = { onShare(entry.uri) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = themeSurface,
                border = BorderStroke(0.8.dp, themeBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Share, "Compartir captura", Modifier.size(20.dp).gradientTint(iconBrush), tint = Color.White)
                }
            }
            Surface(
                onClick = { onDelete(entry.uri) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = themeSurface,
                border = BorderStroke(0.8.dp, themeBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, "Eliminar captura", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
