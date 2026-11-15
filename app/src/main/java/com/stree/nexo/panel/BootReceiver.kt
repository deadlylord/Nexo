package com.stree.nexo.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.stree.nexo.data.Prefs

/**
 * Keeps the edge handle alive after a reboot or an app update. Without this the
 * foreground service only came back when Nexo was opened by hand, so the
 * activator bar looked like it had "closed" on its own.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        if (!Prefs.isPanelEnabled(context) || !Settings.canDrawOverlays(context)) return

        runCatching { EdgePanelService.start(context) }
    }
}
