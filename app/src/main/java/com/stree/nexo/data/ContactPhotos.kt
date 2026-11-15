package com.stree.nexo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

object ContactPhotos {
    private val cache = LruCache<String, Bitmap>(64)
    fun load(context: Context, uri: String): Bitmap? {
        cache.get(uri)?.let { return it }
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.also { cache.put(uri, it) }
            }
        }.getOrNull()
    }
}
