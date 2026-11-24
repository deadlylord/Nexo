package com.stree.nexo.panel

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Puente opcional para los atajos de dos apps.
 *
 * No lee contenido: solo usa la acción global del sistema para dividir la
 * pantalla y abre la segunda app con LAUNCH_ADJACENT. Es el mismo mecanismo
 * que usan los lanzadores de parejas: abrir A, esperar, dividir y abrir B.
 */
class SplitScreenService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val floatingWindowHandler = Handler(Looper.getMainLooper())
    private val observedWindowIds = mutableMapOf<String, Int>()
    private val observedOpenPkgs = mutableSetOf<String>()
    private val pendingMinimizeChecks = mutableMapOf<String, Runnable>()
    private var lastForegroundPackage: String? = null
    private var secondIntent: Intent? = null
    private var waitingFor: String? = null
    private var fallback: Runnable? = null
    private var busy = false
    private var assistance: View? = null

    override fun onServiceConnected() {
        instance = this
        com.stree.nexo.panel.EdgePanelService.syncFloatingBubble(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val text = event.text.joinToString(" ").lowercase()
            when {
                "copiado" in text || "copied" in text || "portapapeles" in text || "clipboard" in text -> {
                    EdgePanelService.notifyCopyDetected(applicationContext)
                }
                "captura" in text || "screenshot" in text -> {
                    EdgePanelService.notifyScreenshotDetected(applicationContext)
                }
            }
            return
        }
        val eventPkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !eventPkg.isNullOrBlank() &&
            eventPkg != "com.android.systemui"
        ) {
            lastForegroundPackage = eventPkg
        }
        // Solo se usa el nombre del paquete y la geometría de ventana (bounds), sin leer contenido.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            inspectFloatingWindows(eventPkg)
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val expected = waitingFor ?: return
        if (eventPkg != expected) return

        waitingFor = null
        fallback?.let { handler.removeCallbacks(it) }
        fallback = null
        // Margen para que la ventana termine de asentarse antes de dividir.
        handler.postDelayed({ proceedToSplit() }, SETTLE_DELAY_MS)
    }

    private data class ObservedAppWindow(
        val id: Int,
        val title: String,
        val bounds: android.graphics.Rect,
        val isFocused: Boolean,
        val isFreeform: Boolean
    )

    private fun snapshotAppWindows(): List<ObservedAppWindow> {
        val allWindows = runCatching { windows }.getOrNull() ?: return emptyList()
        val dm = resources.displayMetrics
        val minSizePx = (130 * dm.density.coerceAtLeast(1f)).toInt()
        val rect = android.graphics.Rect()
        val result = ArrayList<ObservedAppWindow>(allWindows.size)
        for (w in allWindows) {
            if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
            w.getBoundsInScreen(rect)
            if (rect.width() < minSizePx || rect.height() < minSizePx) continue
            val isFreeform = rect.width() < (dm.widthPixels * 0.96f) || rect.height() < (dm.heightPixels * 0.90f)
            result.add(
                ObservedAppWindow(
                    id = w.id,
                    title = w.title?.toString().orEmpty(),
                    bounds = android.graphics.Rect(rect),
                    isFocused = w.isFocused,
                    isFreeform = isFreeform
                )
            )
        }
        return result
    }

    private fun inspectFloatingWindows(eventPkg: String?) {
        val activeEntries = com.stree.nexo.floating.FloatingWindowRegistry.entries.value
            .filter { !it.minimized }
        val activePkgs = activeEntries.mapTo(mutableSetOf()) { it.packageName }
        observedWindowIds.keys.retainAll(activePkgs)
        observedOpenPkgs.retainAll(activePkgs)
        pendingMinimizeChecks.entries.removeAll { (pkg, runnable) ->
            if (pkg !in activePkgs) {
                floatingWindowHandler.removeCallbacks(runnable)
                true
            } else {
                false
            }
        }
        if (activeEntries.isEmpty()) return

        val appWindows = snapshotAppWindows()
        val freeform = appWindows.filter { it.isFreeform }
        val fullscreen = appWindows.filter { !it.isFreeform }
        val now = System.currentTimeMillis()
        val usedWindowIds = mutableSetOf<Int>()

        for (entry in activeEntries) {
            val pkg = entry.packageName
            val boundId = observedWindowIds[pkg]
            val match = freeform.firstOrNull { boundId != null && it.id == boundId && it.id !in usedWindowIds }
                ?: freeform.firstOrNull {
                    it.id !in usedWindowIds &&
                        it.title.isNotBlank() &&
                        it.title.contains(entry.label, ignoreCase = true)
                }
                ?: if (activeEntries.size == 1) {
                    freeform.firstOrNull { it.id !in usedWindowIds && it.isFocused }
                        ?: freeform.firstOrNull { it.id !in usedWindowIds }
                } else {
                    null
                }

            if (match != null) {
                usedWindowIds.add(match.id)
                observedWindowIds[pkg] = match.id
                observedOpenPkgs.add(pkg)
                cancelPendingMinimize(pkg)
                com.stree.nexo.floating.FloatingWindowRegistry.updateObservedBounds(
                    applicationContext,
                    pkg,
                    match.bounds
                )
                continue
            }

            // No vemos su ventana flotante en pantalla: ¿fue maximizada a pantalla completa?
            val isMaximizedFullscreen = fullscreen.any {
                (it.title.isNotBlank() && it.title.contains(entry.label, ignoreCase = true)) ||
                    (it.isFocused && lastForegroundPackage == pkg)
            }
            if (isMaximizedFullscreen) {
                cancelPendingMinimize(pkg)
                if (pkg in observedOpenPkgs) {
                    observedOpenPkgs.remove(pkg)
                    observedWindowIds.remove(pkg)
                    com.stree.nexo.floating.FloatingWindowRegistry.remove(applicationContext, pkg)
                }
                continue
            }

            // Ignorar transiciones internas de Nexo o lanzamientos todavía en curso
            if (eventPkg == packageName) continue
            val wasObserved = pkg in observedOpenPkgs
            if (!wasObserved && now - entry.lastActiveMs < 1_400L) continue

            scheduleMinimizeConfirmation(pkg)
        }
    }

    private fun cancelPendingMinimize(pkg: String) {
        pendingMinimizeChecks.remove(pkg)?.let { floatingWindowHandler.removeCallbacks(it) }
    }

    private fun scheduleMinimizeConfirmation(pkg: String) {
        if (pkg in pendingMinimizeChecks) return
        val check = Runnable {
            pendingMinimizeChecks.remove(pkg)
            val entry = com.stree.nexo.floating.FloatingWindowRegistry.entries.value
                .firstOrNull { it.packageName == pkg && !it.minimized } ?: return@Runnable
            val appWindows = snapshotAppWindows()
            val freeform = appWindows.filter { it.isFreeform }
            val fullscreen = appWindows.filter { !it.isFreeform }
            val boundId = observedWindowIds[pkg]
            val stillFreeform = freeform.any {
                (boundId != null && it.id == boundId) ||
                    (it.title.isNotBlank() && it.title.contains(entry.label, ignoreCase = true)) ||
                    (com.stree.nexo.floating.FloatingWindowRegistry.entries.value.count { e -> !e.minimized } == 1)
            }
            if (stillFreeform) return@Runnable
            val nowFullscreen = fullscreen.any {
                (it.title.isNotBlank() && it.title.contains(entry.label, ignoreCase = true)) ||
                    (it.isFocused && lastForegroundPackage == pkg)
            }
            if (nowFullscreen) return@Runnable

            observedOpenPkgs.remove(pkg)
            observedWindowIds.remove(pkg)
            com.stree.nexo.floating.FloatingWindowRegistry.markMinimized(applicationContext, pkg)
        }
        pendingMinimizeChecks[pkg] = check
        floatingWindowHandler.postDelayed(check, 260L)
    }

    /**
     * Flujo directo, igual que un lanzador de parejas:
     * 1. Se abre la primera app.
     * 2. Se espera a que tenga el foco (evento de ventana) con un respaldo
     *    por tiempo si el sistema no lo entrega.
     * 3. Se ejecuta GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN.
     * 4. Se abre la segunda con LAUNCH_ADJACENT.
     *
     * Si la acción global no está disponible, se ofrece el modo asistido
     * desde Recientes en lugar de fallar en silencio.
     */
    private fun prepare(firstPackage: String, second: Intent): Boolean {
        if (busy) {
            notifyUser("Espera a que termine de abrirse la pareja actual.")
            return false
        }
        busy = true
        secondIntent = Intent(second)
        waitingFor = firstPackage

        // Respaldo: si no llega ningún evento (p. ej. la app ya estaba
        // abierta), se intenta igualmente tras un margen prudente.
        val proceed = Runnable {
            waitingFor = null
            proceedToSplit()
        }
        fallback = proceed
        handler.postDelayed(proceed, FALLBACK_DELAY_MS)
        return true
    }

    private fun proceedToSplit() {
        if (secondIntent == null) return
        fallback?.let { handler.removeCallbacks(it) }
        fallback = null

        val split = runCatching {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        }.getOrDefault(false)

        if (!split) {
            showRecentsAssistance()
            return
        }

        handler.postDelayed({
            launchSecond()
            clearPending()
        }, SECOND_DELAY_MS)
    }

    private fun launchSecond() {
        val second = secondIntent ?: return
        runCatching {
            startActivity(Intent(second).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            ))
        }.onFailure { notifyUser("No se pudo abrir la segunda app.") }
    }

    private fun clearPending() {
        handler.removeCallbacksAndMessages(null)
        secondIntent = null
        waitingFor = null
        fallback = null
        busy = false
        assistance?.let { view ->
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        }
        assistance = null
    }

    /** Respaldo sin clics automáticos: el usuario elige dividir en Recientes. */
    private fun showRecentsAssistance() {
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            clearPending()
            notifyUser("Este sistema no permite dividir desde Nexo. Usa Recientes → Pantalla dividida.")
            return
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val appName = runCatching {
            val packageName = secondIntent?.`package`
                ?: secondIntent?.component?.packageName
                ?: ""
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault("segunda app")
        val windowManager = getSystemService(WindowManager::class.java)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(24, 24, 36))
            addView(TextView(this@SplitScreenService).apply {
                text = "Nexo · Pareja asistida\n1. Toca el icono de la primera app → Pantalla dividida (Split screen).\n2. Elige la segunda app en Recientes. En versiones anteriores también puedes usar el botón de abajo.\nSi la opción no aparece, esa app o el sistema no admite la división."
                setTextColor(Color.WHITE)
                textSize = 13f
            })
            addView(Button(this@SplitScreenService).apply {
                text = "Abrir segunda: $appName"
                setOnClickListener {
                    launchSecond()
                    clearPending()
                }
            })
            addView(Button(this@SplitScreenService).apply {
                text = "Cancelar"
                setOnClickListener { clearPending() }
            })
        }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(200)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(32)
        }
        assistance = panel
        runCatching { windowManager.addView(panel, params) }.onFailure {
            clearPending()
            notifyUser("En Recientes elige Pantalla dividida y luego selecciona $appName manualmente.")
            return
        }
        handler.postDelayed({ clearPending() }, 60_000)
    }

    private fun notifyUser(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() = clearPending()

    override fun onDestroy() {
        floatingWindowHandler.removeCallbacksAndMessages(null)
        pendingMinimizeChecks.clear()
        observedWindowIds.clear()
        observedOpenPkgs.clear()
        clearPending()
        if (instance === this) instance = null
        EdgePanelService.syncFloatingBubble(applicationContext)
        super.onDestroy()
    }

    companion object {
        private const val SETTLE_DELAY_MS = 350L
        private const val FALLBACK_DELAY_MS = 2_500L
        private const val SECOND_DELAY_MS = 700L
        private var instance: SplitScreenService? = null

        fun isRunning(): Boolean = instance != null

        fun performBack(): Boolean =
            runCatching { instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true }.getOrDefault(false)

        fun performHome(): Boolean =
            runCatching { instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true }.getOrDefault(false)

        fun performRecents(): Boolean =
            runCatching { instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true }.getOrDefault(false)

        fun performTakeScreenshot(): Boolean {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return false
            return runCatching {
                instance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) == true
            }.getOrDefault(false)
        }

        fun performToggleSplitScreen(): Boolean =
            runCatching { instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) == true }.getOrDefault(false)

        fun launchSplitPair(context: Context, firstPackage: String, secondPackage: String): Boolean {
            if (instance != null) {
                return launch(context, firstPackage, secondPackage)
            }
            return com.stree.nexo.SplitScreenActivity.launch(
                context = context,
                firstPackage = firstPackage,
                firstClass = null,
                secondPackage = secondPackage,
                secondClass = null
            )
        }

        fun launch(context: Context, firstPackage: String, secondPackage: String): Boolean {
            val first = context.packageManager.getLaunchIntentForPackage(firstPackage) ?: return false
            val second = context.packageManager.getLaunchIntentForPackage(secondPackage) ?: return false
            val service = instance
            if (service == null) {
                Toast.makeText(context,
                    "Activa Nexo · Parejas de apps en Accesibilidad y vuelve a tocar el atajo.",
                    Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
            if (!service.prepare(firstPackage, second)) return false
            return runCatching {
                context.startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrElse {
                service.clearPending()
                false
            }
        }
    }
}
