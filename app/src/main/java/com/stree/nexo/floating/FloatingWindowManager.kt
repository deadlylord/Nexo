package com.stree.nexo.floating

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import com.stree.nexo.data.Prefs
import com.stree.nexo.panel.EdgePanelService
import com.stree.nexo.search.ItemLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Esquemas de acople disponibles para ventanas flotantes en Nexo. */
enum class FloatingDockLayout(
    val label: String,
    val slotCount: Int,
    val minApps: Int
) {
    CENTER("Centrada", 1, 1),
    SPLIT_VERTICAL("Arriba / Abajo", 2, 2),
    SPLIT_HORIZONTAL("Lado a lado", 2, 2),
    MAIN_PLUS_TWO("Principal + 2", 3, 3),
    GRID_2X2("Cuadrícula 2×2", 4, 4),
    CASCADE("Cascada", 4, 2)
}

/** Estado de una app registrada en el gestor de ventanas flotantes y burbujas. */
data class FloatingAppEntry(
    val packageName: String,
    val label: String,
    val minimized: Boolean = false,
    val lastBounds: Rect? = null,
    val lastActiveMs: Long = System.currentTimeMillis()
)

object FloatingWindowRegistry {

    private const val MAX_ENTRIES = 6
    private const val STAGGER_LAUNCH_MS = 170L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _entries = MutableStateFlow<List<FloatingAppEntry>>(emptyList())
    val entries: StateFlow<List<FloatingAppEntry>> = _entries.asStateFlow()

    private val _activeLayout = MutableStateFlow(FloatingDockLayout.CENTER)
    val activeLayout: StateFlow<FloatingDockLayout> = _activeLayout.asStateFlow()

    fun resolveAppLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    fun registerLaunched(context: Context, packageName: String, bounds: Rect? = null) {
        if (packageName.isBlank() || packageName == context.packageName) return
        if (!Prefs.floatingBubbleEnabled(context)) return
        val label = resolveAppLabel(context, packageName)
        _entries.update { current ->
            val existing = current.firstOrNull { it.packageName == packageName }
            val updated = FloatingAppEntry(
                packageName = packageName,
                label = label,
                minimized = false,
                lastBounds = bounds ?: existing?.lastBounds,
                lastActiveMs = System.currentTimeMillis()
            )
            (listOf(updated) + current.filterNot { it.packageName == packageName }).take(MAX_ENTRIES)
        }
        EdgePanelService.syncFloatingBubble(context)
    }

    fun registerMinimized(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        val label = resolveAppLabel(context, packageName)
        _entries.update { current ->
            val existing = current.firstOrNull { it.packageName == packageName }
            val updated = FloatingAppEntry(
                packageName = packageName,
                label = label,
                minimized = true,
                lastBounds = existing?.lastBounds,
                lastActiveMs = System.currentTimeMillis()
            )
            (listOf(updated) + current.filterNot { it.packageName == packageName }).take(MAX_ENTRIES)
        }
        EdgePanelService.syncFloatingBubble(context)
    }

    fun markMinimized(context: Context, packageName: String) {
        _entries.update { list ->
            list.map { if (it.packageName == packageName) it.copy(minimized = true) else it }
        }
        EdgePanelService.syncFloatingBubble(context)
    }

    fun markAllMinimized(context: Context) {
        _entries.update { list -> list.map { it.copy(minimized = true) } }
        EdgePanelService.syncFloatingBubble(context)
    }

    fun updateObservedBounds(context: Context, packageName: String, bounds: Rect) {
        val current = _entries.value.firstOrNull { it.packageName == packageName && !it.minimized } ?: return
        if (current.lastBounds == bounds) return
        _entries.update { list ->
            list.map { if (it.packageName == packageName) it.copy(lastBounds = Rect(bounds)) else it }
        }
        EdgePanelService.syncFloatingBubble(context)
    }

    fun moveEntry(fromIndex: Int, toIndex: Int) {
        _entries.update { list ->
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return@update list
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable
        }
    }

    fun remove(context: Context, packageName: String) {
        _entries.update { list -> list.filterNot { it.packageName == packageName } }
        EdgePanelService.syncFloatingBubble(context)
    }

    fun clearAll(context: Context) {
        mainHandler.removeCallbacksAndMessages(null)
        _entries.value = emptyList()
        _activeLayout.value = FloatingDockLayout.CENTER
        EdgePanelService.syncFloatingBubble(context)
    }

    /**
     * Cierra definitivamente una ventana flotante: la quita del registro y de la burbuja,
     * sale de la ventana hacia el fondo y limpia su proceso en segundo plano.
     */
    fun closeAppDefinitively(context: Context, packageName: String) {
        val appContext = context.applicationContext
        _entries.update { list -> list.filterNot { it.packageName == packageName } }
        EdgePanelService.syncFloatingBubble(appContext)

        val nextActive = _entries.value.firstOrNull { !it.minimized }
        if (nextActive != null) {
            ItemLauncher.launchPackageFloatingInBounds(
                context = appContext,
                packageName = nextActive.packageName,
                bounds = nextActive.lastBounds,
                registerInDock = false
            )
        } else {
            EdgePanelService.minimizeToBackground(appContext)
        }

        mainHandler.postDelayed({
            runCatching {
                val am = appContext.getSystemService(android.app.ActivityManager::class.java)
                am?.killBackgroundProcesses(packageName)
            }
        }, 260L)
    }

    /**
     * Pone las apps seleccionadas en pantalla dividida del sistema y limpia sus burbujas.
     */
    fun openSplitScreen(context: Context, firstPackage: String, secondPackage: String?): Boolean {
        val appContext = context.applicationContext
        if (!secondPackage.isNullOrBlank() && secondPackage != firstPackage) {
            _entries.update { list ->
                list.filterNot { it.packageName == firstPackage || it.packageName == secondPackage }
            }
            EdgePanelService.syncFloatingBubble(appContext)
            return com.stree.nexo.panel.SplitScreenService.launchSplitPair(appContext, firstPackage, secondPackage)
        }

        _entries.update { list -> list.filterNot { it.packageName == firstPackage } }
        EdgePanelService.syncFloatingBubble(appContext)
        ItemLauncher.launchPackageFullscreen(appContext, firstPackage)
        mainHandler.postDelayed({
            if (!com.stree.nexo.panel.SplitScreenService.performToggleSplitScreen()) {
                android.widget.Toast.makeText(
                    appContext,
                    "Activa Nexo en Accesibilidad para dividir automáticamente o elige 2 apps en el menú",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }, 320L)
        return true
    }

    /**
     * Calcula el rectángulo exacto de un slot dentro de la zona segura de pantalla.
     */
    fun computeSlotBounds(
        context: Context,
        layout: FloatingDockLayout,
        slotIndex: Int,
        totalApps: Int = layout.slotCount
    ): Rect {
        val dm = context.resources.displayMetrics
        val d = dm.density.coerceAtLeast(1f)
        fun dp(v: Int) = (v * d).toInt()

        val statusRes = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusPx = if (statusRes > 0) context.resources.getDimensionPixelSize(statusRes) else dp(32)
        val navRes = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navPx = if (navRes > 0) context.resources.getDimensionPixelSize(navRes) else dp(28)

        val margin = dp(8)
        val gap = dp(8)
        val left = margin
        val right = (dm.widthPixels - margin).coerceAtLeast(left + dp(180))
        val top = (statusPx + dp(8)).coerceIn(dp(28), dp(72))
        val bottom = (dm.heightPixels - navPx - dp(10)).coerceAtLeast(top + dp(260))

        val availW = right - left
        val availH = bottom - top

        return when (layout) {
            FloatingDockLayout.CENTER -> {
                val w = (dm.widthPixels * 0.84f).toInt()
                val h = (dm.heightPixels * 0.62f).toInt()
                val l = (dm.widthPixels - w) / 2
                val t = (dm.heightPixels * 0.18f).toInt().coerceAtLeast(top)
                Rect(l, t, l + w, (t + h).coerceAtMost(bottom))
            }

            FloatingDockLayout.SPLIT_VERTICAL -> {
                val h = (availH - gap) / 2
                if (slotIndex % 2 == 0) Rect(left, top, right, top + h)
                else Rect(left, top + h + gap, right, bottom)
            }

            FloatingDockLayout.SPLIT_HORIZONTAL -> {
                val w = (availW - gap) / 2
                if (slotIndex % 2 == 0) Rect(left, top, left + w, bottom)
                else Rect(left + w + gap, top, right, bottom)
            }

            FloatingDockLayout.MAIN_PLUS_TWO -> {
                val topH = ((availH - gap) * 0.54f).toInt()
                val bottomTop = top + topH + gap
                val halfW = (availW - gap) / 2
                when (slotIndex % 3) {
                    0 -> Rect(left, top, right, top + topH)
                    1 -> Rect(left, bottomTop, left + halfW, bottom)
                    else -> Rect(left + halfW + gap, bottomTop, right, bottom)
                }
            }

            FloatingDockLayout.GRID_2X2 -> {
                val halfW = (availW - gap) / 2
                val halfH = (availH - gap) / 2
                when (slotIndex % 4) {
                    0 -> Rect(left, top, left + halfW, top + halfH)
                    1 -> Rect(left + halfW + gap, top, right, top + halfH)
                    2 -> Rect(left, top + halfH + gap, left + halfW, bottom)
                    else -> Rect(left + halfW + gap, top + halfH + gap, right, bottom)
                }
            }

            FloatingDockLayout.CASCADE -> {
                val count = totalApps.coerceIn(2, 4)
                val stepX = dp(22)
                val stepY = dp(46)
                val winW = (availW - stepX * (count - 1)).coerceAtLeast((availW * 0.72f).toInt())
                val winH = (availH * 0.52f).toInt()
                val idx = slotIndex.coerceIn(0, count - 1)
                val l = (left + stepX * idx).coerceAtMost(right - winW)
                val t = (top + stepY * idx).coerceAtMost(bottom - winH)
                Rect(l, t, l + winW, t + winH)
            }
        }
    }

    /**
     * Aplica un layout acoplando las apps indicadas en sus slots correspondientes.
     */
    fun applyLayout(
        context: Context,
        layout: FloatingDockLayout,
        packages: List<String>
    ) {
        val clean = packages.distinct().take(layout.slotCount)
        if (clean.isEmpty()) return
        _activeLayout.value = layout
        mainHandler.removeCallbacksAndMessages(null)

        val appContext = context.applicationContext
        clean.forEachIndexed { index, pkg ->
            val bounds = computeSlotBounds(appContext, layout, index, clean.size)
            _entries.update { list ->
                val label = resolveAppLabel(appContext, pkg)
                val updated = FloatingAppEntry(
                    packageName = pkg,
                    label = label,
                    minimized = false,
                    lastBounds = bounds,
                    lastActiveMs = System.currentTimeMillis() + index
                )
                val others = list.filterNot { it.packageName == pkg }
                (others + updated).takeLast(MAX_ENTRIES)
            }
            mainHandler.postDelayed({
                ItemLauncher.launchPackageFloatingInBounds(
                    context = appContext,
                    packageName = pkg,
                    bounds = bounds,
                    registerInDock = false
                )
            }, index * STAGGER_LAUNCH_MS)
        }
        // Reordenar para que las apps del layout queden al frente en orden de slot
        _entries.update { list ->
            val byPkg = list.associateBy { it.packageName }
            val ordered = clean.mapNotNull { byPkg[it] }
            val rest = list.filterNot { it.packageName in clean }
            (ordered + rest).take(MAX_ENTRIES)
        }
        EdgePanelService.syncFloatingBubble(appContext)
    }
}
