package com.stree.nexo

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Coordinador de parejas basado únicamente en APIs públicas.
 * Android decide finalmente si coloca las actividades una junto a otra.
 */
class SplitScreenActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firstPackage = intent.getStringExtra(EXTRA_FIRST_PACKAGE).orEmpty()
        val firstClass = intent.getStringExtra(EXTRA_FIRST_CLASS).orEmpty()
        val secondPackage = intent.getStringExtra(EXTRA_SECOND_PACKAGE).orEmpty()
        val secondClass = intent.getStringExtra(EXTRA_SECOND_CLASS).orEmpty()
        val delay = intent.getLongExtra(EXTRA_DELAY, DEFAULT_DELAY_MS)
            .coerceIn(300L, 3_000L)

        val first = resolveApp(firstPackage, firstClass)
        val second = resolveApp(secondPackage, secondClass)
        if (first == null || second == null) {
            Toast.makeText(this, "No se pudo resolver una de las aplicaciones", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val firstOpened = runCatching {
            startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!firstOpened) {
            Toast.makeText(this, "No se pudo abrir la primera aplicación", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        handler.postDelayed({
            val secondOpened = runCatching {
                startActivity(second.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME
                ))
                true
            }.getOrDefault(false)
            if (!secondOpened) {
                Toast.makeText(this, "No se pudo abrir la segunda aplicación", Toast.LENGTH_LONG).show()
            }
            finish()
        }, delay)
    }

    private fun resolveApp(packageName: String, className: String): Intent? {
        if (packageName.isBlank()) return null
        if (className.isNotBlank()) {
            return Intent().setComponent(ComponentName(packageName, className))
        }
        return packageManager.getLaunchIntentForPackage(packageName)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_FIRST_PACKAGE = "packageNameFirstApp"
        private const val EXTRA_FIRST_CLASS = "classNameFirstApp"
        private const val EXTRA_SECOND_PACKAGE = "packageNameSecondApp"
        private const val EXTRA_SECOND_CLASS = "classNameSecondApp"
        private const val EXTRA_DELAY = "delay"
        private const val DEFAULT_DELAY_MS = 1_000L

        fun launch(
            context: Context,
            firstPackage: String,
            firstClass: String?,
            secondPackage: String,
            secondClass: String?,
            delay: Long = DEFAULT_DELAY_MS
        ): Boolean = runCatching {
            context.startActivity(Intent(context, SplitScreenActivity::class.java).apply {
                putExtra(EXTRA_FIRST_PACKAGE, firstPackage)
                putExtra(EXTRA_FIRST_CLASS, firstClass.orEmpty())
                putExtra(EXTRA_SECOND_PACKAGE, secondPackage)
                putExtra(EXTRA_SECOND_CLASS, secondClass.orEmpty())
                putExtra(EXTRA_DELAY, delay)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
            true
        }.getOrDefault(false)
    }
}
