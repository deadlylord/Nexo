package com.stree.nexo.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.stree.nexo.data.ClipboardHistoryStore
import com.stree.nexo.data.Haptics

object AiLauncher {

    private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

    fun queryChatGpt(context: Context, text: String): Boolean {
        val prompt = text.trim()
        if (prompt.isEmpty()) return false
        Haptics.perform(context)

        val uri = Uri.parse("https://chatgpt.com/?q=${Uri.encode(prompt)}")
        val inApp = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(CHATGPT_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, inApp)) return true

        val shareToApp = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, prompt)
            .setPackage(CHATGPT_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, shareToApp)) return true

        val inBrowser = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, inBrowser)) return true

        Toast.makeText(context, "No se pudo abrir ChatGPT", Toast.LENGTH_SHORT).show()
        return false
    }

    fun queryGemini(context: Context, text: String): Boolean {
        val prompt = text.trim()
        if (prompt.isEmpty()) return false
        Haptics.perform(context)

        val shareToGemini = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, prompt)
            .setPackage(GEMINI_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, shareToGemini)) return true

        ClipboardHistoryStore.copyToClipboard(context, prompt)
        val appIntent = context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appIntent != null && launchSafe(context, appIntent)) {
            Toast.makeText(context, "Consulta copiada para pegar en Gemini", Toast.LENGTH_SHORT).show()
            return true
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, webIntent)) {
            Toast.makeText(context, "Consulta copiada para pegar en Gemini", Toast.LENGTH_SHORT).show()
            return true
        }

        Toast.makeText(context, "No se pudo abrir Gemini", Toast.LENGTH_SHORT).show()
        return false
    }

    fun startChatGptVoice(context: Context): Boolean {
        Haptics.perform(context)

        val voiceIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/voice"))
            .setPackage(CHATGPT_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, voiceIntent)) return true

        val appIntent = context.packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appIntent != null && launchSafe(context, appIntent)) return true

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, webIntent)) return true

        Toast.makeText(context, "Instala ChatGPT para usar el modo Voz", Toast.LENGTH_SHORT).show()
        return false
    }

    fun startGeminiLive(context: Context): Boolean {
        Haptics.perform(context)

        val voiceCandidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GEMINI_PACKAGE),
            Intent(Intent.ACTION_ASSIST).setPackage(GEMINI_PACKAGE),
            Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GOOGLE_PACKAGE),
            Intent(Intent.ACTION_ASSIST).setPackage(GOOGLE_PACKAGE)
        )
        for (candidate in voiceCandidates) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (candidate.resolveActivity(context.packageManager) != null && launchSafe(context, candidate)) {
                return true
            }
        }

        val appIntent = context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appIntent != null && launchSafe(context, appIntent)) return true

        val genericVoice = Intent(Intent.ACTION_VOICE_COMMAND)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, genericVoice)) return true

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchSafe(context, webIntent)) return true

        Toast.makeText(context, "No se encontró Gemini disponible", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun launchSafe(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
