package com.stree.nexo.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

class ContactsIndexer(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    fun load(limit: Int = Int.MAX_VALUE): List<SearchItem> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        // Cada número distinto de una persona debe seguir siendo accesible.
        data class Accumulator(
            val name: String,
            val phone: String,
            val photo: String?
        )

        val people = LinkedHashMap<String, Accumulator>()

        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val contactIdIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val photoIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                )

                while (cursor.moveToNext() && people.size < limit) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (name.isBlank() || number.isBlank()) continue

                    val contactId = cursor.getString(contactIdIndex).orEmpty()
                    val normalizedNumber = PhoneNumberUtils.normalizeNumber(number)
                    val identity = "${contactId.ifBlank { normalizeName(name) }}|$normalizedNumber"
                    val current = people[identity]
                    if (current == null) {
                        // La foto se carga solo al aparecer en pantalla, no toda la agenda.
                        val photo = cursor.getString(photoIndex)
                        people[identity] = Accumulator(name, number, photo)
                    }
                }
            }
        }

        // Respaldo para agendas que repiten la misma persona en varias cuentas.
        val seenNumbers = HashSet<String>()
        return people.values.mapNotNull { person ->
            val nameKey = normalizeName(person.name)
            val numberKey = PhoneNumberUtils.normalizeNumber(person.phone)
            if (nameKey.isBlank() || !seenNumbers.add("$nameKey|$numberKey")) return@mapNotNull null

            SearchItem(
                id = "contact:$nameKey|$numberKey",
                title = person.name,
                subtitle = person.phone,
                type = ItemType.CONTACT,
                keywords = "${person.name} ${person.phone}",
                photoUri = person.photo,
                intent = Intent(
                    Intent.ACTION_CALL,
                    Uri.fromParts("tel", person.phone, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                phoneNumber = person.phone
            )
        }
    }

    private fun normalizeName(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
}
