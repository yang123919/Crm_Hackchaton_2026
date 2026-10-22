package com.example.hackchaton_2026_crm.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.components.DateField
import com.example.hackchaton_2026_crm.ui.components.Dropdown
import com.example.hackchaton_2026_crm.ui.components.FormTextField
import com.example.hackchaton_2026_crm.ui.components.LabeledDivider
import com.example.hackchaton_2026_crm.ui.components.MicButton
import com.example.hackchaton_2026_crm.ui.components.rememberSpeechController
import com.example.hackchaton_2026_crm.ui.components.StageChip
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors

/** Sample line 4 from docs/PLAN.md. */
private const val SAMPLE = "Called Mr Tan, he's ok with the price, booking this weekend."

/** 6. Voice update: speak or type what happened, AI turns it into a log entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: FollowUpViewModel = viewModel(factory = FollowUpViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    val speech = rememberSpeechController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Update · ${state.lead?.fullName.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            if (state.draft != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(16.dp).height(56.dp),
                    ) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add to history", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.lead?.let { lead ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${lead.product} · now ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StageChip(lead.salesStage)
                }
                lead.nextAction?.let {
                    Text(
                        "Last next step: $it" + (lead.nextFollowUpAt?.let { d -> " · ${Format.shortDate(d)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MicButton(
                speech, state.transcript, onTextChange = viewModel::setTranscript,
                modifier = Modifier.align(Alignment.CenterHorizontally), size = 88.dp,
            )
            OutlinedTextField(
                value = state.transcript,
                onValueChange = { speech.stop(); viewModel.setTranscript(it) },
                label = { Text("What happened?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.transcript.isBlank()) {
                SuggestionChip(
                    onClick = { viewModel.setTranscript(SAMPLE) },
                    label = { Text("Try: \"$SAMPLE\"", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { speech.stop(); viewModel.process(forceDemo = false) },
                    enabled = state.transcript.isNotBlank() && !state.working,
                ) {
                    Text(if (state.draft?.fromAi == true) "Process again" else "Process with AI")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
                if (state.draft == null && !state.working) {
                    TextButton(onClick = viewModel::writeManually, enabled = state.transcript.isNotBlank()) {
                        Text("Write it myself")
                    }
                }
            }

            when {
                state.working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Writing the log entry…")
                }
                state.error != null -> ErrorCard(
                    message = state.error!!,
                    onRetry = { viewModel.process(forceDemo = false) },
                    onDemo = { viewModel.process(forceDemo = true) },
                    onManual = viewModel::writeManually,
                )
            }

            state.draft?.let { draft ->
                DraftEditor(
                    draft = draft,
                    currentStage = state.lead?.salesStage.orEmpty(),
                    usedDemo = state.usedDemo,
                    summaryError = state.summaryError,
                    dateError = state.dateError,
                    onEdit = viewModel::editDraft,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DraftEditor(
    draft: FollowUpDraft,
    currentStage: String,
    usedDemo: Boolean,
    summaryError: String?,
    dateError: String?,
    onEdit: (FollowUpDraft.() -> FollowUpDraft) -> Unit,
) {
    val ai = draft.fromAi
    LabeledDivider(if (ai) "AI suggestion" + if (usedDemo) " (demo)" else "" else "Log entry")
    FormTextField("Summary", draft.summary, ai, false, summaryError, KeyboardType.Text, singleLine = false) {
        onEdit { copy(summary = it) }
    }
    FormTextField("Next action", draft.nextAction, ai, false, null, KeyboardType.Text) {
        onEdit { copy(nextAction = it) }
    }
    DateField("Next date", draft.nextDate, ai, draft.checkDate, dateError, minDate = Format.today()) {
        onEdit { copy(nextDate = it, checkDate = false) }
    }
    if (draft.stage != currentStage) {
        Text(
            "Stage: $currentStage → ${draft.stage}",
            fontWeight = FontWeight.Bold,
            color = LocalStatusColors.current.ok,
        )
    }
    Dropdown("Stage", draft.stage, ReferenceLists.salesStages, ai, false, null) {
        onEdit { copy(stage = it) }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onDemo: () -> Unit, onManual: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = LocalStatusColors.current.overdueContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onDemo) { Text("Use demo AI") }
                TextButton(onClick = onManual) { Text("Write it myself") }
            }
        }
    }
}
