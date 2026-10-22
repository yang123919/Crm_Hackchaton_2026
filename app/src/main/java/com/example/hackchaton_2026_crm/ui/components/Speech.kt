package com.example.hackchaton_2026_crm.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.hackchaton_2026_crm.speech.SpeechCorrector
import com.example.hackchaton_2026_crm.speech.SpeechRanker
import com.example.hackchaton_2026_crm.speech.SpeechVocabulary
import kotlinx.coroutines.delay

/**
 * Android speech-to-text (en-MY) that keeps listening until the user taps stop: the system
 * recognizer ends after a pause, so we restart it and append each finished phrase.
 * Must be used from the main thread (Compose callbacks are).
 */
@Stable
class SpeechController internal constructor(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    /** 0..1 microphone loudness, drives the pulse. */
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var permissionDenied by mutableStateOf(false)
        internal set

    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    /** Consecutive "busy"/client errors; the recognizer is rebuilt a few times before giving up. */
    private var retries = 0
    /** Text before the phrase currently being heard. */
    private var base = ""
    private var onText: (String) -> Unit = {}

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-MY")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
        // Several alternatives, so SpeechRanker can prefer the one with our car names or a valid phone number.
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        // Hints the recogniser to favour our vocabulary (honoured on Android 13+, ignored before).
        putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(SpeechVocabulary.biasingStrings))
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    /** Appends what is heard to [current], reporting the full text through [onText]. */
    fun start(current: String, onText: (String) -> Unit) {
        if (!available) {
            error = "Speech recognition isn't available on this phone. Type instead."
            return
        }
        base = current.trim()
        this.onText = onText
        error = null
        retries = 0
        listening = true
        listen()
    }

    fun stop() {
        if (!listening) return
        listening = false
        level = 0f
        recognizer?.stopListening()
    }

    fun release() {
        listening = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val existing = recognizer
        if (existing != null) {
            existing.cancel()
            existing.startListening(intent)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }.startListening(intent)
        }
    }

    /** Busy mic (e.g. a call) or a recognizer that wasn't ready: rebuild it and try again shortly. */
    private fun rebuildAndListen() {
        recognizer?.destroy()
        recognizer = null
        main.postDelayed({ if (listening) listen() }, 400)
    }

    private fun join(a: String, b: String) = listOf(a, b).filter { it.isNotBlank() }.joinToString(" ")

    /** The best of the recogniser's alternatives, with model names and digits corrected. */
    private fun Bundle?.bestResult(): String {
        val candidates = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        return SpeechRanker.pickBest(candidates, this?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES))
    }

    /** Live words while speaking: just the top guess, corrected. */
    private fun Bundle?.partialResult(): String =
        SpeechCorrector.fix(this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            onText(join(base, partialResults.partialResult()))
        }

        override fun onResults(results: Bundle?) {
            base = join(base, results.bestResult())
            onText(base)
            if (listening) listen()
        }

        override fun onError(code: Int) {
            when {
                // Silence or nothing understood: keep going until the user taps stop.
                listening && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) -> listen()
                listening && retries < 3 &&
                    (code == SpeechRecognizer.ERROR_CLIENT || code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) -> {
                    retries++
                    rebuildAndListen()
                }
                code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    listening = false
                    permissionDenied = true
                }
                listening -> {
                    listening = false
                    level = 0f
                    error = when (code) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER -> "No connection for speech. Type instead."
                        SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Mic is busy (call in progress?). Tap to try again."
                        else -> "Mic stopped (error $code). Tap to try again."
                    }
                }
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (listening) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onReadyForSpeech(params: Bundle?) {
            retries = 0
        }
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

@Composable
fun rememberSpeechController(): SpeechController {
    val context = LocalContext.current
    val controller = remember { SpeechController(context.applicationContext) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/**
 * Big round mic (wireframe 2): pulses with the voice and shows a timer while on.
 * Asks for the mic permission on first tap; if denied, offers a link to app settings.
 */
@Composable
fun MicButton(
    speech: SpeechController,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
) {
    val context = LocalContext.current
    val currentText by rememberUpdatedState(text)
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        speech.permissionDenied = !granted
        if (granted) speech.start(currentText, onTextChange)
    }

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(speech.listening) {
        seconds = 0
        while (speech.listening) {
            delay(1_000)
            seconds++
        }
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "halo",
    )
    val voice by animateFloatAsState(1f + speech.level * 0.12f, label = "voice")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.3f)) {
            if (speech.listening) {
                Box(
                    Modifier.size(size).scale(pulse * voice)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                )
            }
            FilledIconButton(
                onClick = {
                    when {
                        speech.listening -> speech.stop()
                        speech.hasPermission() -> speech.start(text, onTextChange)
                        else -> askPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(size),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (speech.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (speech.listening) "Stop" else "Speak",
                        modifier = Modifier.size(size * 0.36f),
                    )
                    if (speech.listening && size >= 96.dp) {
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        when {
            speech.permissionDenied -> {
                Text("Mic permission is off.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Enable mic in Settings") }
            }
            speech.error != null -> Text(
                speech.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            else -> Text(
                if (speech.listening) "Listening… tap to stop" else "Tap to speak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
