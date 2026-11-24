package com.stree.nexo.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/** Estado observable del dictado por voz de Nexo. */
@Stable
class NexoDictationState internal constructor() {
    var listening by mutableStateOf(false)
        internal set
    /** Nivel de voz normalizado 0..1 para animar el micrófono. */
    var level by mutableFloatStateOf(0f)
        internal set

    internal var startAction: () -> Unit = {}
    internal var stopAction: () -> Unit = {}

    fun start() {
        if (!listening) startAction()
    }

    fun toggle() = if (listening) stopAction() else startAction()
    fun stop() = stopAction()
}

/**
 * Dictado en tiempo real dentro de Nexo. Los resultados parciales se envían a
 * [onText] mientras el usuario habla. Si el equipo no tiene servicio de
 * reconocimiento en segundo plano, usa la ventana de dictado del sistema.
 */
@Composable
fun rememberNexoDictation(onText: (String) -> Unit): NexoDictationState {
    val context = LocalContext.current
    val state = remember { NexoDictationState() }
    val textSink by rememberUpdatedState(onText)
    val engine = remember { DictationEngine(context.applicationContext, state) { textSink(it) } }

    val systemDictation = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { textSink(it) }
        }
    }

    fun openSystemDictation() {
        runCatching { systemDictation.launch(engine.recognizerIntent(partial = false)) }
            .onFailure {
                Toast.makeText(context, "El dictado por voz no está disponible", Toast.LENGTH_SHORT).show()
            }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) engine.begin() else openSystemDictation()
    }

    state.startAction = {
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> openSystemDictation()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> engine.begin()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    state.stopAction = { engine.end() }

    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return state
}

private class DictationEngine(
    private val appContext: Context,
    private val state: NexoDictationState,
    private val emit: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun recognizerIntent(partial: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para buscar…")

    fun begin() {
        release()
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (created == null) {
            Toast.makeText(appContext, "No se pudo iniciar el micrófono", Toast.LENGTH_SHORT).show()
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        state.listening = true
        state.level = 0f
        runCatching { created.startListening(recognizerIntent(partial = true)) }
            .onFailure { finish() }
    }

    fun end() {
        runCatching { recognizer?.stopListening() }
        state.listening = false
        state.level = 0f
    }

    fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        state.listening = false
        state.level = 0f
    }

    private fun finish() {
        state.listening = false
        state.level = 0f
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { state.listening = true }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            // El rango típico va de -2 a 10 dB; se normaliza para la animación.
            state.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { state.level = 0f }
        override fun onError(error: Int) {
            finish()
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Toast.makeText(appContext, "Permite el micrófono para dictar", Toast.LENGTH_SHORT).show()
            }
        }
        override fun onResults(results: Bundle?) {
            firstResult(results)?.let(emit)
            finish()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(emit)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
