package com.stree.nexo.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.SearchItem
import java.util.Locale

/**
 * Construye enlaces de WhatsApp infiriendo el país del teléfono.
 * No pide datos al usuario: el chat se abre directamente.
 */
object WhatsAppLinks {

    private val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
    private val phonePattern = Regex("[1-9][0-9]{7,14}")

    /** Código ISO de dos letras: SIM, red, idioma o configuración regional. */
    fun countryIso(context: Context): String {
        val candidates = buildList {
            runCatching {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                telephony?.simCountryIso?.takeIf { it.isNotBlank() }?.let(::add)
                telephony?.networkCountryIso?.takeIf { it.isNotBlank() }?.let(::add)
            }
            Locale.getDefault().country.takeIf { it.isNotBlank() }?.let(::add)
            context.resources.configuration.locales[0]?.country
                ?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return candidates.firstOrNull { it.matches(Regex("[A-Za-z]{2}")) }
            ?.uppercase(Locale.US)
            .orEmpty()
    }

    /** Número en formato E.164 sin el signo +, listo para wa.me. */
    fun digits(context: Context, raw: String): String? {
        val compact = raw.trim().replace(Regex("[\\s().\\-]"), "")
        if (compact.isBlank()) return null

        val international = when {
            compact.startsWith("+") -> compact.drop(1)
            compact.startsWith("00") -> compact.drop(2)
            else -> ""
        }
        if (international.matches(phonePattern)) return international

        val country = countryIso(context)
        if (country.isNotBlank()) {
            runCatching {
                PhoneNumberUtils.formatNumberToE164(compact, country)
            }.getOrNull()
                ?.removePrefix("+")
                ?.takeIf { it.matches(phonePattern) }
                ?.let { return it }
        }

        // Último recurso: si ya parece un número completo, se usa tal cual.
        return compact.takeIf { it.matches(phonePattern) }
    }

    fun e164(context: Context, raw: String): String? =
        digits(context, raw)?.let { "+$it" }

    fun link(context: Context, raw: String): String? =
        digits(context, raw)?.let { "https://wa.me/$it" }

    /** Intent directo a la app de WhatsApp; si no está, al enlace oficial. */
    fun intent(context: Context, raw: String): Intent? {
        val digits = digits(context, raw) ?: return null
        val packageName = whatsAppPackages.firstOrNull {
            context.packageManager.getLaunchIntentForPackage(it) != null
        }

        return if (packageName != null) {
            val uri = Uri.parse("whatsapp://send").buildUpon()
                .appendQueryParameter("phone", digits)
                .build()
            Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
        }
    }

    fun directChatItem(context: Context, contact: SearchItem): SearchItem? {
        val intent = intent(context, contact.phoneNumber.orEmpty()) ?: return null
        return SearchItem(
            id = "whatsapp:${contact.id}",
            title = "WhatsApp · ${contact.title}",
            subtitle = contact.phoneNumber.orEmpty(),
            type = ItemType.CUSTOM,
            intent = intent
        )
    }
}
