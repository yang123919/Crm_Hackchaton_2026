package com.example.hackchaton_2026_crm.ui.today

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.Intents
import com.example.hackchaton_2026_crm.ui.components.SectionHeader
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onCapture: () -> Unit,
    onOpenLead: (Long) -> Unit,
    onWhatsApp: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLeads: (() -> Unit)?,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("QuickLead", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                },
                actions = {
                    if (onOpenLeads != null) {
                        IconButton(onClick = onOpenLeads) { Icon(Icons.Filled.Search, "Customers") }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(Icons.Filled.Mic, null) },
                text = { Text("Capture customer", style = MaterialTheme.typography.titleMedium) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(64.dp),
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                Text(state.greeting, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                CounterRow(state)
            }
            if (state.overdue.isEmpty() && state.dueToday.isEmpty()) {
                item { EmptyToday() }
            }
            if (state.overdue.isNotEmpty()) {
                item { SectionHeader("Overdue") }
                items(state.overdue, key = { it.id }) { lead ->
                    FollowUpCard(
                        lead = lead,
                        onOpen = { onOpenLead(lead.id) },
                        onCall = { Intents.dial(context, lead.mobile) },
                        onWhatsApp = { onWhatsApp(lead.id) },
                    )
                }
            }
            if (state.dueToday.isNotEmpty()) {
                item { SectionHeader("Due today") }
                items(state.dueToday, key = { it.id }) { lead ->
                    FollowUpCard(
                        lead = lead,
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
private fun CounterRow(state: TodayUiState) {
    val status = LocalStatusColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Counter(state.overdue.size, "Overdue", status.overdue, status.overdueContainer, Modifier.weight(1f))
        Counter(state.dueToday.size, "Today", status.dueToday, status.dueTodayContainer, Modifier.weight(1f))
        Counter(
            state.capturedThisWeek, "This wk\ncaptured",
            MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceContainer,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Counter(value: Int, label: String, color: Color, container: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = color)
        }
    }
}

@Composable
private fun FollowUpCard(lead: Lead, onOpen: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val status = LocalStatusColors.current
    val due = lead.nextFollowUpAt
    val overdue = due != null && due < Format.today()
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
                if (due != null) {
                    Text(
                        Format.dueLabel(due),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (overdue) status.overdue else status.dueToday,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text(
                "${lead.product} · ${lead.salesStage}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!lead.nextAction.isNullOrBlank()) {
                Text(
                    "“${lead.nextAction}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp),
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

@Composable
private fun EmptyToday() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.EventAvailable, null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No follow-ups today.\nCapture your next customer.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
