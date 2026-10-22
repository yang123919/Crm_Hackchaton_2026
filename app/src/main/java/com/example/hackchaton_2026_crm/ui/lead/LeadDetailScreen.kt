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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.hackchaton_2026_crm.data.LicenceRules
import com.example.hackchaton_2026_crm.data.LicenceType
import androidx.compose.material3.ButtonDefaults
import com.example.hackchaton_2026_crm.ui.components.Avatar
import com.example.hackchaton_2026_crm.ui.theme.Mint
import com.example.hackchaton_2026_crm.ui.theme.Navy
import com.example.hackchaton_2026_crm.ui.components.DelayedProgress
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.hackchaton_2026_crm.ui.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val HISTORY_WINDOW_HEIGHT = 170.dp

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
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    // Saveable: the camera app may take over the screen while the sheet scans a licence.
    var booking by rememberSaveable { mutableStateOf(false) }
    if (booking && lead != null) {
        BookingSheet(
            lead = lead,
            onLicenceScanned = viewModel::saveLicence,
            onTypeChange = viewModel::setLicenceType,
            onBook = { kind, date, time ->
                viewModel.book(kind, date, time) {
                    booking = false
                    Toast.makeText(context, "${kind.label} booked", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { booking = false },
        )
    }
    if (confirmDelete && lead != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${lead.fullName}?") },
            text = { Text("This also deletes all follow-up history. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lead?.fullName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (lead != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete customer")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> DelayedProgress(Modifier.align(Alignment.Center))
                lead == null -> Text("Customer not found", Modifier.align(Alignment.Center))
                else -> LeadDetailContent(
                    lead = lead,
                    entries = state.entries,
                    onWhatsApp = { onWhatsApp(lead.id) },
                    onVoiceUpdate = { onVoiceUpdate(lead.id) },
                    onBook = { booking = true },
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
    onBook: () -> Unit,
) {
    val context = LocalContext.current
    // LazyColumn is Compose's RecyclerView: history rows are only composed while on screen.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(lead.fullName)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(lead.fullName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            Format.phone(lead.mobile),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("${lead.product} · ${lead.outlet}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                StageChip(lead.salesStage)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(Icons.Filled.Call, "Call", Modifier.weight(1f)) { Intents.dial(context, lead.mobile) }
                    ActionButton(Icons.AutoMirrored.Filled.Chat, "WhatsApp", Modifier.weight(1f), emphasis = true, onClick = onWhatsApp)
                    ActionButton(Icons.Filled.Edit, "Update", Modifier.weight(1f), onClick = onVoiceUpdate)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBook,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.EventAvailable, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Book test drive / booking")
                }

                NextStep(lead)

                LabeledDivider("Follow-up history")
                if (entries.isEmpty()) {
                    Text("No history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (entries.isNotEmpty()) {
            item {
                // Fixed window of about two entries; the history scrolls inside it. A new entry is added at
                // the top, and a lazy list keeps its scroll on the old first row, so jump back to the newest.
                val historyState = rememberLazyListState()
                LaunchedEffect(entries.first().id) { historyState.scrollToItem(0) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = HISTORY_WINDOW_HEIGHT), state = historyState) {
                    items(entries, key = { it.id }) { TimelineEntry(it) }
                }
            }
        }
        item {
            Column {
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
                DetailRow(
                    "Licence",
                    if (lead.hasLicence) {
                        LicenceRules.summary(
                            LicenceType.fromName(lead.licenceType),
                            LicenceRules.parseClasses(lead.licenceClasses),
                            lead.licenceValidTo,
                        )
                    } else null,
                )
                DetailRow("Gender", lead.gender)
                DetailRow("Address", lead.address)
                DetailRow("Status", lead.status)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    // Icon above the label so "WhatsApp" fits three-across even with large system fonts.
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = if (emphasis) {
            ButtonDefaults.filledTonalButtonColors(containerColor = Mint, contentColor = Navy)
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
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
