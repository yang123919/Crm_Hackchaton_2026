package com.example.hackchaton_2026_crm.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadGroup
import com.example.hackchaton_2026_crm.data.SeedData
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.Intents
import com.example.hackchaton_2026_crm.ui.components.StageChip
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import java.time.temporal.ChronoUnit

/** The customers behind one of the Today counters. Tap a customer for their full detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadListScreen(
    onBack: () -> Unit,
    onOpenLead: (Long) -> Unit,
    onWhatsApp: (Long) -> Unit,
    viewModel: LeadListViewModel = viewModel(factory = LeadListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.loading) viewModel.group.title else "${viewModel.group.title} (${state.leads.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.leads.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.EventAvailable, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Text(
                    viewModel.group.emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                items(state.leads, key = { it.id }) { lead ->
                    LeadSummaryCard(
                        lead = lead,
                        group = viewModel.group,
                        onOpen = { onOpenLead(lead.id) },
                        onCall = { Intents.dial(context, lead.mobile) },
                        onWhatsApp = { onWhatsApp(lead.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LeadSummaryCard(lead: Lead, group: LeadGroup, onOpen: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val status = LocalStatusColors.current
    val today = Format.today()
    val due = lead.nextFollowUpAt
    // The label on the right says what this list is about: when it was due, or when it was captured.
    val (tag, tagColor) = when (group) {
        LeadGroup.CAPTURED_THIS_WEEK -> capturedLabel(lead, today) to MaterialTheme.colorScheme.onSurfaceVariant
        else -> if (due != null) Format.dueLabel(due) to (if (due < today) status.overdue else status.dueToday)
        else "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lead.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tag.isNotEmpty()) {
                    Text(tag, style = MaterialTheme.typography.labelLarge, color = tagColor, modifier = Modifier.padding(end = 8.dp))
                }
            }
            Text(Format.phone(lead.mobile), style = MaterialTheme.typography.bodyMedium)
            Text(
                "${lead.product} · ${lead.outlet}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StageChip(lead.salesStage)
                lead.maskedIc?.let {
                    Text("  IC $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!lead.nextAction.isNullOrBlank()) {
                Text(
                    "Next: ${lead.nextAction}" + (due?.let { " · ${Format.shortDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCall) {
                    Icon(Icons.Filled.Call, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call")
                }
                TextButton(onClick = onWhatsApp) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp")
                }
            }
        }
    }
}

private fun capturedLabel(lead: Lead, today: java.time.LocalDate): String {
    val created = lead.createdAt.atZone(SeedData.ZONE).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(created, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "$days days ago"
    }
}
