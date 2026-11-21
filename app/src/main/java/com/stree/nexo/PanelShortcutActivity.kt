package com.stree.nexo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.stree.nexo.data.Haptics
import com.stree.nexo.ui.disableSystemTransitions
import com.stree.nexo.ui.finishNoSystemAnimation

/** Entrada pública y mínima para los accesos directos publicados al launcher. */
class PanelShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableSystemTransitions()
        Haptics.perform(this)
        startActivity(Intent(this, SearchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        finishNoSystemAnimation()
    }
}
