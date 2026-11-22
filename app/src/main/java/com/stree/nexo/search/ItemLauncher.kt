package com.stree.nexo.search

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Process
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.SearchItem
import com.stree.nexo.SplitScreenActivity

object ItemLauncher {

    fun launch(context: Context, item: SearchItem): Boolean = runCatching {
        if (item.intent?.action == "com.stree.nexo.OPEN_PAIR") {
            val uri = item.intent.data ?: return false
            val first = uri.getQueryParameter("first") ?: return false
            val second = uri.getQueryParameter("second") ?: return false
            return SplitScreenActivity.launch(
                context = context,
                firstPackage = first,
                firstClass = uri.getQueryParameter("firstClass"),
                secondPackage = second,
                secondClass = uri.getQueryParameter("secondClass"),
                delay = uri.getQueryParameter("delay")?.toLongOrNull() ?: 1_000L
            )
        }
        if (item.intent?.action == "com.stree.nexo.OPEN_APP") {
            val packageName = item.intent.data?.getQueryParameter("package") ?: return false
            return launchApp(context, packageName)
        }
        when (item.type) {
            ItemType.SHORTCUT -> launchShortcut(context, item)
            ItemType.APP -> launchApp(context, item.packageName)
            else -> launchIntent(context, item.intent)
        }
    }.getOrDefault(false)

    /** Apps, atajos propios y web se pueden intentar abrir en ventana flotante. */
    fun canOpenFloating(item: SearchItem): Boolean = when (item.type) {
        ItemType.APP -> true
        ItemType.CUSTOM -> item.intent != null && !item.id.startsWith("store:") &&
            item.intent.action != "com.stree.nexo.OPEN_PAIR" &&
            item.intent.component?.className != "com.stree.nexo.FolderActivity"
        else -> false
    }

    /**
     * Intenta abrir el elemento en una ventana flotante (freeform).
     * Si el fabricante no lo soporta, el sistema lo abrirá normalmente.
     */
    fun launchFloating(context: Context, item: SearchItem): Boolean = runCatching {
        if (!canOpenFloating(item)) return false
        val intent = intentFor(context, item) ?: return false
        launchIntentFloating(context, intent)
    }.getOrDefault(false)

    /** Abre una aplicación por paquete en ventana flotante (freeform). */
    fun launchPackageFloating(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return launchIntentFloating(context, intent)
    }

    /**
     * Abre una notificación (su PendingIntent o la app origen) directamente en
     * ventana flotante, con respaldo automático a modo normal si el sistema lo bloquea.
     */
    fun launchNotificationFloating(
        context: Context,
        packageName: String,
        contentIntent: android.app.PendingIntent?
    ): Boolean {
        if (contentIntent != null) {
            val sentFloating = runCatching {
                val options = buildFloatingOptions(context, allowBackgroundPendingIntent = true)
                contentIntent.send(context, 0, null, null, null, null, options)
                true
            }.getOrDefault(false)
            if (sentFloating) return true
        }

        if (launchPackageFloating(context, packageName)) return true

        if (contentIntent != null) {
            val sentNormal = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        ).toBundle()
                    contentIntent.send(context, 0, null, null, null, null, opts)
                } else {
                    contentIntent.send()
                }
                true
            }.getOrDefault(false)
            if (sentNormal) return true
        }

        return launchApp(context, packageName)
    }

    private fun launchIntentFloating(context: Context, rawIntent: Intent): Boolean = runCatching {
        val intent = Intent(rawIntent)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Sin MULTIPLE_TASK el sistema reutiliza la tarea existente (en pantalla
        // completa) y se ignora el tamaño de ventana.
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(intent, buildFloatingOptions(context))
        true
    }.getOrDefault(false)

    private fun buildFloatingOptions(
        context: Context,
        allowBackgroundPendingIntent: Boolean = false
    ): android.os.Bundle {
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels * 0.84f).toInt()
        val height = (metrics.heightPixels * 0.62f).toInt()
        val left = (metrics.widthPixels - width) / 2
        val top = (metrics.heightPixels * 0.18f).toInt()

        val options = ActivityOptions.makeBasic()
        options.setLaunchBounds(Rect(left, top, left + width, top + height))

        // setLaunchWindowingMode() es @TestApi: no está en el SDK público, pero
        // existe en el framework. Se intenta por reflexión.
        runCatching {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            method.invoke(options, WINDOWING_MODE_FREEFORM)
        }

        if (allowBackgroundPendingIntent &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            runCatching {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
        }

        val bundle = options.toBundle()
        // Respaldo: clave interna que usa el propio ActivityOptions.
        bundle.putInt(EXTRA_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
        return bundle
    }

    private fun intentFor(context: Context, item: SearchItem): Intent? = when (item.type) {
        ItemType.APP -> item.packageName
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }

        else -> item.intent?.let { Intent(it) }
    }

    private fun launchShortcut(context: Context, item: SearchItem): Boolean {
        val packageName = item.packageName
        val shortcutId = item.shortcutId

        if (packageName != null && shortcutId != null) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (launcherApps != null) {
                launcherApps.startShortcut(
                    packageName,
                    shortcutId,
                    null,
                    null,
                    Process.myUserHandle()
                )
                return true
            }
        }

        return launchApp(context, packageName)
    }

    private fun launchApp(context: Context, packageName: String?): Boolean {
        if (packageName == null) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun launchIntent(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        val base = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(base)
            true
        }.getOrElse {
            // Si la app elegida (p. ej. WhatsApp) no acepta el enlace directo,
            // se reintenta sin paquete para que lo resuelva el navegador.
            if (base.`package` == null) return false
            base.`package` = null
            runCatching {
                context.startActivity(base)
                true
            }.getOrDefault(false)
        }
    }

    private const val EXTRA_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"

    /** WindowConfiguration.WINDOWING_MODE_FREEFORM (oculto en el SDK público). */
    private const val WINDOWING_MODE_FREEFORM = 5
}
