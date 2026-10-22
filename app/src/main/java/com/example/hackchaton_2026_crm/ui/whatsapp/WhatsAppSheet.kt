package com.example.hackchaton_2026_crm.ui.whatsapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.ui.Intents

/** 7. WhatsApp draft. [openId] makes each opening a fresh draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppSheet(leadId: Long, openId: Int, onDismiss: () -> Unit) {
    val viewModel: WhatsAppViewModel = viewModel(key = "whatsapp-$leadId-$openId", factory = WhatsAppViewModel.factory(leadId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Message to ${state.lead?.fullName.orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    MessageLanguage.entries.forEachIndexed { i, language ->
                        SegmentedButton(
                            selected = state.language == language,
                            onClick = { viewModel.setLanguage(language) },
                            shape = SegmentedButtonDefaults.itemShape(i, MessageLanguage.entries.size),
                            icon = {},
                        ) { Text(language.label) }
                    }
                }
            }

            Box {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    enabled = !state.loading,
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.notice ?: "Edit before sending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::generate, enabled = !state.loading) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Regenerate")
                }
            }

            Button(
                onClick = {
                    val lead = state.lead ?: return@Button
                    Intents.whatsapp(context, lead.mobile, state.text)
                    viewModel.logSent()
                    onDismiss()
                },
                enabled = !state.loading && state.text.isNotBlank() && state.lead != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, null)
                Spacer(Modifier.width(8.dp))
                Text("Open in WhatsApp", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
