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
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Timer
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
import com.example.hackchaton_2026_crm.data.LeadGroup
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.Intents
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.hackchaton_2026_crm.ui.components.DelayedProgress
import com.example.hackchaton_2026_crm.ui.components.QuickLeadWordmark
import com.example.hackchaton_2026_crm.ui.components.SectionHeader
import com.example.hackchaton_2026_crm.ui.components.TopAppBar
import com.example.hackchaton_2026_crm.ui.theme.LinkTeal
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import com.example.hackchaton_2026_crm.ui.theme.Mint
import com.example.hackchaton_2026_crm.ui.theme.MintLight
import com.example.hackchaton_2026_crm.ui.theme.Navy
import com.example.hackchaton_2026_crm.ui.theme.Teal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onCapture: () -> Unit,
    onOpenLead: (Long) -> Unit,
    onWhatsApp: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDataHealth: () -> Unit,
    onOpenLeads: (() -> Unit)?,
    onOpenGroup: (LeadGroup) -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { QuickLeadWordmark() },
                actions = {
                    if (state.demoMode) {
                        Text(
                            "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (onOpenLeads != null) {
                        IconButton(onClick = onOpenLeads) { Icon(Icons.Filled.Search, "Customers") }
                    }
                    IconButton(onClick = onOpenDataHealth) { Icon(Icons.Filled.HealthAndSafety, "Data health") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(Icons.Filled.PersonAdd, null) },
                text = { Text("Capture customer", style = MaterialTheme.typography.titleMedium) },
                containerColor = Mint,
                contentColor = Navy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(64.dp),
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                DelayedProgress()
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Navy, Teal)), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                ) {
                    Column {
                        Text(
                            state.greeting,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            Format.longDate(Format.today()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                CounterRow(state, onOpenGroup)
                state.avgCaptureSeconds?.let { TimeSaved(it) }
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
private fun TimeSaved(seconds: Int) {
    val label = if (seconds < 90) "$seconds s" else "${(seconds + 30) / 60} min"
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Timer, null, tint = LocalStatusColors.current.ok, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        // One Text so it wraps as a sentence on narrow screens / large fonts.
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Avg $label per lead") }
                withStyle(SpanStyle(color = muted)) { append("  (vs ~5 min on laptop)") }
            },
        )
    }
}

@Composable
private fun CounterRow(state: TodayUiState, onOpenGroup: (LeadGroup) -> Unit) {
    val status = LocalStatusColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Counter(
            state.overdue.size, "Overdue", Icons.Filled.Warning,
            status.overdue, status.overdueContainer, Modifier.weight(1f),
        ) { onOpenGroup(LeadGroup.OVERDUE) }
        Counter(
            state.dueToday.size, "Today", Icons.Filled.EventAvailable,
            status.dueToday, status.dueTodayContainer, Modifier.weight(1f),
        ) { onOpenGroup(LeadGroup.DUE_TODAY) }
        Counter(
            state.capturedThisWeek, "This wk\ncaptured", Icons.Filled.PersonAdd,
            LinkTeal, MintLight, Modifier.weight(1f),
        ) { onOpenGroup(LeadGroup.CAPTURED_THIS_WEEK) }
    }
}

@Composable
private fun Counter(
    value: Int,
    label: String,
    icon: ImageVector,
    color: Color,
    container: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
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
                Box(
                    Modifier.size(10.dp)
                        .background(if (overdue) status.overdue else status.dueToday, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
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
