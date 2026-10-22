package com.example.hackchaton_2026_crm.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import com.example.hackchaton_2026_crm.ui.components.MicButton
import com.example.hackchaton_2026_crm.ui.components.rememberSpeechController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Sample voice lines from docs/PLAN.md, one tap to fill for the demo. */
private val samples = listOf(
    "Just met Mr Tan Ah Kow, 012-345 6789, interested in the X50, budget around 120k, wants test drive next Tuesday, call him back Friday.",
    "Puan Aisyah, one seven three two two eight nine nine one zero, nak Saga, trade in Myvi 2018, follow up esok.",
    "Mr Lim from Butterworth, wants the Tiggo 8 Pro, no budget yet, send him quotation, WhatsApp him tomorrow.",
)

/** 2. Capture: speak (live, editable transcript) or type; then Process with AI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onProcess: (transcript: String, startedAtMillis: Long) -> Unit,
) {
    val startedAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var text by rememberSaveable { mutableStateOf("") }
    val speech = rememberSpeechController()
    val focus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New customer") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { speech.stop(); onProcess(text.trim(), startedAt) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                    .padding(16.dp).height(56.dp),
            ) {
                Text("Process with AI", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Tell me about the customer: name, phone, car, budget, next step.",
                style = MaterialTheme.typography.titleMedium,
            )
            MicButton(speech, text, onTextChange = { text = it }, modifier = Modifier.align(Alignment.CenterHorizontally))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    // Typing while the mic is on would be overwritten by the next phrase.
                    speech.stop()
                    text = it
                },
                label = { Text(if (speech.listening) "Live transcript" else "Notes (editable)") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            TextButton(onClick = { speech.stop(); focus.requestFocus() }) {
                Icon(Icons.Filled.Keyboard, null)
                Spacer(Modifier.width(8.dp))
                Text("Type instead")
            }
            Text("Try a sample", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            samples.forEach { sample ->
                SuggestionChip(
                    onClick = { text = sample },
                    label = { Text(sample, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
