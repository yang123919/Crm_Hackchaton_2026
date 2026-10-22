package com.example.hackchaton_2026_crm.ui.lead

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.Intents
import com.example.hackchaton_2026_crm.ui.components.LabeledDivider
import com.example.hackchaton_2026_crm.ui.components.StageChip
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(
    onBack: () -> Unit,
    onWhatsApp: (Long) -> Unit,
    onVoiceUpdate: (Long) -> Unit,
    viewModel: LeadDetailViewModel = viewModel(factory = LeadDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lead = state.lead
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lead?.fullName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                lead == null -> Text("Customer not found", Modifier.align(Alignment.Center))
                else -> LeadDetailContent(
                    lead = lead,
                    entries = state.entries,
                    onWhatsApp = { onWhatsApp(lead.id) },
                    onVoiceUpdate = { onVoiceUpdate(lead.id) },
                )
            }
        }
    }
}

@Composable
private fun LeadDetailContent(
    lead: Lead,
    entries: List<FollowUpEntry>,
    onWhatsApp: () -> Unit,
    onVoiceUpdate: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Text(
            "${Format.phone(lead.mobile)} · ${lead.product}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(lead.outlet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        StageChip(lead.salesStage)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Icons.Filled.Call, "Call", Modifier.weight(1f)) { Intents.dial(context, lead.mobile) }
            ActionButton(Icons.AutoMirrored.Filled.Chat, "WhatsApp", Modifier.weight(1f), onWhatsApp)
            ActionButton(Icons.Filled.Mic, "Update", Modifier.weight(1f), onVoiceUpdate)
        }

        NextStep(lead)

        LabeledDivider("Follow-up history")
        if (entries.isEmpty()) {
            Text("No history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { TimelineEntry(it) }

        LabeledDivider("Details")
        DetailRow("Budget", lead.budgetRm?.let(Format::rm))
        DetailRow(
            "Trade-in",
            if (lead.tradeInUsedCar) {
                listOfNotNull(lead.tradeInBrand, lead.tradeInModel, lead.tradeInYear?.toString())
                    .joinToString(" ").ifBlank { "Yes" }
            } else "No",
        )
        DetailRow("Close date", lead.expectedCloseDate?.let(Format::longDate))
        DetailRow("Email", lead.email)
        DetailRow("IC", lead.maskedIc)
        DetailRow("Gender", lead.gender)
        DetailRow("Address", lead.address)
        DetailRow("Status", lead.status)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    // Icon above the label so "WhatsApp" fits three-across even with large system fonts.
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NextStep(lead: Lead) {
    val date = lead.nextFollowUpAt ?: return
    val status = LocalStatusColors.current
    val today = Format.today()
    val color = when {
        date < today -> status.overdue
        date == today -> status.dueToday
        else -> MaterialTheme.colorScheme.onSurface
    }
    Spacer(Modifier.height(16.dp))
    Text(
        buildString {
            append("Next: ")
            if (!lead.nextAction.isNullOrBlank()) append(lead.nextAction).append(" · ")
            append(Format.shortDate(date)).append(" (").append(Format.dueLabel(date, today)).append(")")
        },
        style = MaterialTheme.typography.titleSmall,
        color = color,
    )
}

@Composable
private fun TimelineEntry(entry: FollowUpEntry) {
    Row(Modifier.padding(bottom = 12.dp)) {
        Box(
            Modifier.padding(top = 6.dp, end = 12.dp).size(10.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Column {
            Text(Format.longDate(entry.date), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
            if (entry.nextAction.isNotBlank()) {
                Text(
                    "Next: ${entry.nextAction}" + (entry.nextDate?.let { " · ${Format.shortDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.width(100.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—")
    }
}
