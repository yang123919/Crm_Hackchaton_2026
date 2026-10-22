package com.example.hackchaton_2026_crm.ui.health

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.HealthFix
import com.example.hackchaton_2026_crm.data.HealthIssue
import com.example.hackchaton_2026_crm.data.Severity

/** A fix waiting for the salesperson to confirm; merging removes customer records, so it asks first. */
private sealed interface Confirm {
    data class One(val issue: HealthIssue) : Confirm
    data object All : Confirm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHealthScreen(
    onBack: () -> Unit,
    onOpenLead: (Long) -> Unit,
    viewModel: DataHealthViewModel = viewModel(factory = DataHealthViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    confirm?.let { c ->
        val merges = when (c) {
            is Confirm.One -> listOfNotNull(c.issue.fix as? HealthFix.MergeCustomers)
            Confirm.All -> state.fixable.mapNotNull { it.fix as? HealthFix.MergeCustomers }
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (c) {
                        is Confirm.One -> c.issue.title
                        Confirm.All -> "Fix ${state.fixable.size} problems?"
                    },
                )
            },
            text = {
                Text(
                    if (merges.isEmpty()) "The duplicates are removed. The latest entry is kept."
                    else "Duplicate customers are merged into one record and their history is combined. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (c) {
                        is Confirm.One -> viewModel.fix(c.issue)
                        Confirm.All -> viewModel.fixAll()
                    }
                    confirm = null
                }) { Text("Fix") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data health") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::scan) { Icon(Icons.Filled.Refresh, "Scan again") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Summary(state = state, onFixAll = { confirm = Confirm.All })
            }
            items(state.issues, key = { it.id }) { issue ->
                IssueCard(
                    issue = issue,
                    advice = state.advice[issue.id] ?: issue.todo,
                    onOpen = { onOpenLead(issue.leadId) },
                    onFix = {
                        if (issue.fix is HealthFix.MergeCustomers) confirm = Confirm.One(issue) else viewModel.fix(issue)
                    },
                )
            }
        }
    }
}

@Composable
private fun Summary(state: DataHealthUiState, onFixAll: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.issues.isEmpty()) {
                Text("All clear", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("No duplicates or wrong details found in your customers.")
            } else {
                val n = state.issues.size
                Text(
                    "$n problem${if (n == 1) "" else "s"} found",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${state.fixable.size} can be fixed in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.summary != null -> Text("AI: ${state.summary}", style = MaterialTheme.typography.bodyMedium)
                state.aiLoading -> Text("AI is reviewing your data…", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.aiLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.fixable.isNotEmpty()) {
                Button(onClick = onFixAll, Modifier.fillMaxWidth()) { Text("Fix all ${state.fixable.size}") }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: HealthIssue, advice: String, onOpen: () -> Unit, onFix: () -> Unit) {
    val dot = when (issue.severity) {
        Severity.HIGH -> MaterialTheme.colorScheme.error
        Severity.MEDIUM -> MaterialTheme.colorScheme.tertiary
        Severity.LOW -> MaterialTheme.colorScheme.outline
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    issue.kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(issue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(issue.detail, style = MaterialTheme.typography.bodyMedium)
            Text(advice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Open customer") }
                issue.fix?.let { FilledTonalButton(onClick = onFix) { Text(it.label) } }
            }
        }
    }
}
