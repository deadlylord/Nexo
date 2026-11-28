package com.stree.nexo.data

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object IconPackManager {

    private const val ICON_PACK_STUDIO_EXPORTED = "ginlemon.iconpackstudio.exported"
    private const val COMPOSITE_SIZE_PX = 192

    private val ICON_PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "ginlemon.smartlauncher.THEMES"
    )

    private class LoadedPack(
        val packageName: String,
        val resources: Resources,
        val componentMap: Map<String, String>,
        val packageMap: Map<String, String>,
        val iconBacks: List<String>,
        val iconMasks: List<String>,
        val iconUpons: List<String>,
        val scale: Float
    )

    private val lock = Any()
    @Volatile
    private var cachedPack: LoadedPack? = null
    @Volatile
    private var cachedPackPackage: String? = null

    fun listInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashSet<String>()
        val packs = mutableListOf<IconPackInfo>()

        for (action in ICON_PACK_ACTIONS) {
            val resolved = runCatching { pm.queryIntentActivities(Intent(action), 0) }
                .getOrDefault(emptyList())
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || !seen.add(pkg)) continue
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = runCatching {
                    appInfo?.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        ?: info.loadLabel(pm)?.toString()?.trim()
                }.getOrNull().orEmpty().ifBlank { pkg }
                val icon = runCatching {
                    appInfo?.loadIcon(pm) ?: info.loadIcon(pm)
                }.getOrNull()
                packs += IconPackInfo(packageName = pkg, label = label, icon = icon)
            }
        }

        if (seen.add(ICON_PACK_STUDIO_EXPORTED)) {
            runCatching { pm.getApplicationInfo(ICON_PACK_STUDIO_EXPORTED, 0) }.getOrNull()?.let { appInfo ->
                val label = runCatching { appInfo.loadLabel(pm)?.toString()?.trim() }
                    .getOrNull().orEmpty().ifBlank { "Icon Pack Studio" }
                val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                packs += IconPackInfo(
                    packageName = ICON_PACK_STUDIO_EXPORTED,
                    label = label,
                    icon = icon
                )
            }
        }

        return packs.sortedBy { it.label.lowercase() }
    }

    fun clearCache() {
        synchronized(lock) {
            cachedPack = null
            cachedPackPackage = null
        }
    }

    fun resolveIcon(
        context: Context,
        packageName: String,
        activityName: String?,
        fallback: Drawable?
    ): Drawable? {
        val packPkg = Prefs.iconPackPackage(context)?.takeIf { it.isNotBlank() } ?: return fallback
        val pack = getOrLoadPack(context, packPkg) ?: return fallback

        return runCatching {
            val normalizedActivity = normalizeActivityName(packageName, activityName)
            val drawableName = normalizedActivity?.let { pack.componentMap["$packageName/$it"] }
                ?: pack.packageMap[packageName]

            if (!drawableName.isNullOrBlank()) {
                loadPackDrawable(pack, drawableName)?.let { return@runCatching it }
            }

            if (fallback != null && (pack.iconBacks.isNotEmpty() || pack.iconMasks.isNotEmpty() || pack.iconUpons.isNotEmpty())) {
                compositeWithMask(context, pack, packageName, fallback)?.let { return@runCatching it }
            }

            fallback
        }.getOrDefault(fallback)
    }

    private fun normalizeActivityName(packageName: String, activityName: String?): String? {
        val raw = activityName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (raw.startsWith(".")) "$packageName$raw" else raw
    }

    private fun getOrLoadPack(context: Context, packPkg: String): LoadedPack? {
        if (cachedPackPackage == packPkg) return cachedPack
        synchronized(lock) {
            if (cachedPackPackage == packPkg) return cachedPack
            val loaded = runCatching { loadPackInternal(context, packPkg) }.getOrNull()
            cachedPack = loaded
            cachedPackPackage = packPkg
            return loaded
        }
    }

    private fun loadPackInternal(context: Context, packPkg: String): LoadedPack? {
        val res = runCatching { context.packageManager.getResourcesForApplication(packPkg) }.getOrNull()
            ?: return null

        val componentMap = HashMap<String, String>(512)
        val packageMap = HashMap<String, String>(512)
        val iconBacks = mutableListOf<String>()
        val iconMasks = mutableListOf<String>()
        val iconUpons = mutableListOf<String>()
        var scale = 0.78f

        fun parse(parser: XmlPullParser) {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name?.lowercase()) {
                        "item" -> {
                            val component = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")?.trim()
                            if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                                parseComponentKey(component)?.let { (pkg, cls) ->
                                    componentMap["$pkg/$cls"] = drawable
                                    packageMap.putIfAbsent(pkg, drawable)
                                }
                            }
                        }
                        "iconback" -> collectImageAttrs(parser, iconBacks)
                        "iconmask" -> collectImageAttrs(parser, iconMasks)
                        "iconupon" -> collectImageAttrs(parser, iconUpons)
                        "scale" -> {
                            parser.getAttributeValue(null, "factor")
                                ?.toFloatOrNull()
                                ?.let { scale = it.coerceIn(0.35f, 1.5f) }
                        }
                    }
                }
                event = parser.next()
            }
        }

        val xmlId = res.getIdentifier("appfilter", "xml", packPkg)
        var parsed = false
        if (xmlId != 0) {
            parsed = runCatching {
                res.getXml(xmlId).use { parser -> parse(parser) }
                true
            }.getOrDefault(false)
        }
        if (!parsed) {
            runCatching {
                res.assets.open("appfilter.xml").use { stream ->
                    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                    val parser = factory.newPullParser()
                    parser.setInput(stream, "UTF-8")
                    parse(parser)
                }
            }
        }

        return LoadedPack(
            packageName = packPkg,
            resources = res,
            componentMap = componentMap,
            packageMap = packageMap,
            iconBacks = iconBacks,
            iconMasks = iconMasks,
            iconUpons = iconUpons,
            scale = scale
        )
    }

    private fun parseComponentKey(component: String): Pair<String, String>? {
        val start = component.indexOf('{')
        val end = component.lastIndexOf('}')
        if (start < 0 || end <= start + 1) return null
        val inner = component.substring(start + 1, end)
        val slash = inner.indexOf('/')
        if (slash <= 0 || slash >= inner.lastIndex) return null
        val pkg = inner.substring(0, slash).trim()
        val rawCls = inner.substring(slash + 1).trim()
        if (pkg.isEmpty() || rawCls.isEmpty()) return null
        val cls = if (rawCls.startsWith(".")) "$pkg$rawCls" else rawCls
        return pkg to cls
    }

    private fun collectImageAttrs(parser: XmlPullParser, out: MutableList<String>) {
        for (i in 0 until parser.attributeCount) {
            val value = parser.getAttributeValue(i)?.trim()
            if (!value.isNullOrEmpty()) out += value
        }
    }

    private fun loadPackDrawable(pack: LoadedPack, name: String): Drawable? {
        val drawableId = pack.resources.getIdentifier(name, "drawable", pack.packageName)
            .takeIf { it != 0 }
            ?: pack.resources.getIdentifier(name, "mipmap", pack.packageName).takeIf { it != 0 }
            ?: return null
        return runCatching { ResourcesCompat.getDrawable(pack.resources, drawableId, null) }.getOrNull()
    }

    private fun loadPackBitmap(pack: LoadedPack, names: List<String>, seed: Int): Bitmap? {
        if (names.isEmpty()) return null
        val name = names[abs(seed) % names.size]
        val drawable = loadPackDrawable(pack, name) ?: return null
        return runCatching {
            drawable.toBitmap(COMPOSITE_SIZE_PX, COMPOSITE_SIZE_PX, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun compositeWithMask(
        context: Context,
        pack: LoadedPack,
        packageName: String,
        fallback: Drawable
    ): Drawable? {
        val seed = packageName.hashCode()
        val backBmp = loadPackBitmap(pack, pack.iconBacks, seed)
        val maskBmp = loadPackBitmap(pack, pack.iconMasks, seed)
        val uponBmp = loadPackBitmap(pack, pack.iconUpons, seed)
        if (backBmp == null && maskBmp == null && uponBmp == null) return null

        val result = Bitmap.createBitmap(COMPOSITE_SIZE_PX, COMPOSITE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fullRect = Rect(0, 0, COMPOSITE_SIZE_PX, COMPOSITE_SIZE_PX)

        if (backBmp != null) {
            canvas.drawBitmap(backBmp, null, fullRect, paint)
        }

        val scaledSize = (COMPOSITE_SIZE_PX * pack.scale).toInt().coerceIn(32, COMPOSITE_SIZE_PX)
        val offset = (COMPOSITE_SIZE_PX - scaledSize) / 2
        val iconBmp = runCatching {
            fallback.toBitmap(scaledSize, scaledSize, Bitmap.Config.ARGB_8888)
        }.getOrNull()

        if (iconBmp != null) {
            val layer = canvas.saveLayer(0f, 0f, COMPOSITE_SIZE_PX.toFloat(), COMPOSITE_SIZE_PX.toFloat(), null)
            canvas.drawBitmap(iconBmp, offset.toFloat(), offset.toFloat(), paint)
            if (maskBmp != null) {
                val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
                canvas.drawBitmap(maskBmp, null, fullRect, maskPaint)
            }
            canvas.restoreToCount(layer)
        }

        if (uponBmp != null) {
            canvas.drawBitmap(uponBmp, null, fullRect, paint)
        }

        return BitmapDrawable(context.resources, result)
    }
}
