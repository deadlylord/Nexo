package com.stree.nexo.data

import android.content.Context
import android.net.Uri
import com.stree.nexo.search.WhatsAppLinks

object ContactShortcuts {
    fun target(context: Context, contact: SearchItem, whatsapp: Boolean): String? {
        val number = contact.phoneNumber?.takeIf { it.isNotBlank() } ?: return null
        val phone = if (whatsapp) WhatsAppLinks.e164(context, number) ?: return null else number
        return Uri.Builder().scheme("nexo-contact").authority("open")
            .appendQueryParameter("action", if (whatsapp) "whatsapp" else "call")
            .appendQueryParameter("phone", phone)
            .appendQueryParameter("photo", contact.photoUri.orEmpty())
            .build().toString()
    }
}
