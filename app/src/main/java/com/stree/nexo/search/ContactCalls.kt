package com.stree.nexo.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.stree.nexo.data.ItemType
import com.stree.nexo.data.SearchItem

/** A phone call is only requested after an explicit contact tap or Enter. */
class ContactCalls(
    private val activity: ComponentActivity,
    private val onLaunched: (Boolean) -> Unit = {}
) {
    private var pending: SearchItem? = null
    private val permission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val item = pending
        pending = null
        if (item != null) launch(item, granted)
    }

    fun handle(item: SearchItem): Boolean {
        if (item.type != ItemType.CONTACT || item.phoneNumber.isNullOrBlank()) return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            launch(item, true)
        } else {
            pending = item
            permission.launch(Manifest.permission.CALL_PHONE)
        }
        return true
    }

    private fun launch(item: SearchItem, direct: Boolean) {
        val number = item.phoneNumber ?: return
        if (!direct) Toast.makeText(activity, "Sin permiso de llamadas: abriendo el marcador", Toast.LENGTH_LONG).show()
        var opened = runCatching {
            activity.startActivity(Intent(
                if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                Uri.fromParts("tel", number, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!opened && direct) {
            opened = runCatching {
                activity.startActivity(Intent(Intent.ACTION_DIAL,
                    Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (opened) Toast.makeText(activity,
                "El sistema rechazó la llamada directa; abriendo el marcador", Toast.LENGTH_LONG).show()
        }
        if (!opened) Toast.makeText(activity, "No se pudo abrir la llamada", Toast.LENGTH_SHORT).show()
        onLaunched(opened)
    }
}
