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
import com.example.hackchaton_2026_crm.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import com.example.hackchaton_2026_crm.ui.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hackchaton_2026_crm.ocr.IcInfo
import com.example.hackchaton_2026_crm.ocr.LicenceInfo

/** Example notes, one tap to fill for a quick try. */
private val samples = listOf(
    "Just met Mr Tan Ah Kow, 012-345 6789, interested in the X50, budget around 120k, wants test drive next Tuesday, call him back Friday.",
    "Puan Aisyah, 017-322 8991, nak Saga, trade in Myvi 2018, follow up esok.",
    "Mr Lim from Butterworth, wants the Tiggo 8 Pro, no budget yet, send him quotation, WhatsApp him tomorrow.",
)

/**
 * 2. Capture: scan an IC and/or a customer form, or type notes; then Process with AI.
 * Scanned form text lands in the notes box, where it can be corrected first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onProcess: (transcript: String, startedAtMillis: Long, ic: IcInfo?, licence: LicenceInfo?) -> Unit,
) {
    val startedAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var text by rememberSaveable { mutableStateOf("") }
    val scan = rememberScan(onFormText = { scanned ->
        text = listOf(text.trim(), scanned).filter { it.isNotEmpty() }.joinToString("\n")
    })
    val scannedId = scan.ic != null || scan.licence != null
    val canProcess = (text.isNotBlank() || scannedId) && !scan.busy

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
                onClick = { onProcess(text.trim(), startedAt, scan.ic, scan.licence) },
                enabled = canProcess,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                    .padding(16.dp).height(56.dp),
            ) {
                Text(
                    if (text.isBlank() && scannedId) "Review details" else "Process with AI",
                    style = MaterialTheme.typography.titleMedium,
                )
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
                "Scan an IC or driving licence, or a customer form, or type your notes.",
                style = MaterialTheme.typography.titleMedium,
            )
            ScanSection(scan)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Notes (editable)") },
                supportingText = { Text("Name, phone, car, budget, next step. Scanned form text appears here.") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
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
