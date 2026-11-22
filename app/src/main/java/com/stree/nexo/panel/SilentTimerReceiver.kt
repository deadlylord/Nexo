package com.stree.nexo.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restaura el sonido normal automáticamente cuando vence el silencio programado. */
class SilentTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SoundControl.restoreIfExpired(context.applicationContext)
    }
}
