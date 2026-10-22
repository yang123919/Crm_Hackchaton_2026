package com.example.hackchaton_2026_crm.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.ai.LeadField
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.components.AiMark
import com.example.hackchaton_2026_crm.ui.components.LabeledDivider
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenExisting: (Long) -> Unit,
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.saved.collect(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.phase == ReviewPhase.Editing) "Check details" else "New customer") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            if (state.phase == ReviewPhase.Editing) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(16.dp).height(56.dp),
                    ) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save customer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val phase = state.phase) {
                is ReviewPhase.Working -> Working(phase, onFillManually = viewModel::fillManually)
                is ReviewPhase.Failed -> Failed(
                    message = phase.message,
                    onRetry = { viewModel.process(forceDemo = false) },
                    onDemo = { viewModel.process(forceDemo = true) },
                    onFillManually = viewModel::fillManually,
                )
                ReviewPhase.Editing -> ReviewForm(
                    state = state,
                    transcript = viewModel.transcript,
                    onEdit = viewModel::edit,
                    onOpenExisting = onOpenExisting,
                    onSaveAsNew = viewModel::saveAsNew,
                )
            }
        }
    }
}

// ---- 3. AI working ------------------------------------------------------------------------

@Composable
private fun Working(phase: ReviewPhase.Working, onFillManually: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text("Converting to CRM format…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Reading your notes", "Matching product & outlet", "Setting follow-up date")
                .forEachIndexed { i, label -> WorkingStep(label, done = phase.step > i) }
        }
        if (phase.slow) {
            Spacer(Modifier.height(24.dp))
            Text("This is taking longer than usual.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onFillManually) { Text("Fill in manually") }
        }
    }
}

@Composable
private fun WorkingStep(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Icon(Icons.Filled.CheckCircle, null, tint = LocalStatusColors.current.ok, modifier = Modifier.size(20.dp))
        } else {
            CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onDemo: () -> Unit, onFillManually: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = LocalStatusColors.current.overdueContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.titleMedium)
                Text("Your notes are kept. Try again, or use the offline demo AI.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text("Retry") }
                    OutlinedButton(onClick = onFillManually) { Text("Fill in manually") }
                }
                TextButton(onClick = onDemo) { Text("Use demo AI") }
            }
        }
    }
}

// ---- 4. Review & confirm -------------------------------------------------------------------

@Composable
private fun ReviewForm(
    state: ReviewUiState,
    transcript: String,
    onEdit: (LeadField, LeadForm.() -> LeadForm) -> Unit,
    onOpenExisting: (Long) -> Unit,
    onSaveAsNew: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    val today = Format.today()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.duplicate?.let { DuplicateBanner(it, onOpen = { onOpenExisting(it.id) }, onSaveAsNew = onSaveAsNew) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            AiMark()
            Text(" = filled by AI    ", style = MaterialTheme.typography.bodySmall)
            Text("!", fontWeight = FontWeight.Bold, color = LocalStatusColors.current.dueToday)
            Text(" = please check", style = MaterialTheme.typography.bodySmall)
            if (state.usedDemo) {
                Spacer(Modifier.weight(1f))
                Text("Demo AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val field = @Composable { f: LeadField, label: String, value: String, keyboard: KeyboardType, set: LeadForm.(String) -> LeadForm ->
            FormTextField(label, value, form, f, errors[f], keyboard) { v -> onEdit(f) { set(v) } }
        }
        field(LeadField.FIRST_NAME, "First name *", form.firstName, KeyboardType.Text) { copy(firstName = it) }
        field(LeadField.LAST_NAME, "Last name", form.lastName, KeyboardType.Text) { copy(lastName = it) }
        field(LeadField.MOBILE, "Mobile *", form.mobile, KeyboardType.Phone) { copy(mobile = it) }
        field(LeadField.EMAIL, "Email", form.email, KeyboardType.Email) { copy(email = it) }
        Dropdown("Product *", form.product, ReferenceLists.products, form, LeadField.PRODUCT, errors[LeadField.PRODUCT]) {
            onEdit(LeadField.PRODUCT) { copy(product = it) }
        }
        Dropdown("Outlet *", form.outlet, ReferenceLists.outlets, form, LeadField.OUTLET, errors[LeadField.OUTLET]) {
            onEdit(LeadField.OUTLET) { copy(outlet = it) }
        }
        field(LeadField.BUDGET, "Budget (RM)", form.budget, KeyboardType.Number) { copy(budget = it) }
        DateField("Expected close date", form.closeDate, form, LeadField.CLOSE_DATE, null, minDate = null) {
            onEdit(LeadField.CLOSE_DATE) { copy(closeDate = it) }
        }

        TradeIn(form, errors[LeadField.TRADE_IN], onEdit)

        LabeledDivider("Follow-up")
        field(LeadField.NEXT_ACTION, "Next action", form.nextAction, KeyboardType.Text) { copy(nextAction = it) }
        DateField("Next date", form.nextDate, form, LeadField.NEXT_DATE, errors[LeadField.NEXT_DATE], minDate = today) {
            onEdit(LeadField.NEXT_DATE) { copy(nextDate = it) }
        }
        Dropdown("Stage", form.stage, ReferenceLists.salesStages, form, LeadField.STAGE, null) {
            onEdit(LeadField.STAGE) { copy(stage = it) }
        }
        FormTextField("Note", form.summary, form, LeadField.SUMMARY, null, KeyboardType.Text, singleLine = false) {
            onEdit(LeadField.SUMMARY) { copy(summary = it) }
        }

        Text(
            "You said: \"$transcript\"",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DuplicateBanner(existing: Lead, onOpen: () -> Unit, onSaveAsNew: () -> Unit) {
    val status = LocalStatusColors.current
    Card(colors = CardDefaults.cardColors(containerColor = status.dueTodayContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = status.dueToday)
                Spacer(Modifier.width(8.dp))
                Text("Existing customer: ${existing.fullName}", fontWeight = FontWeight.Bold)
            }
            Text(
                "${existing.product} · ${existing.salesStage}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 32.dp),
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSaveAsNew) { Text("Save as new") }
                Button(onClick = onOpen) { Text("Open existing") }
            }
        }
    }
}

@Composable
private fun TradeIn(form: LeadForm, error: String?, onEdit: (LeadField, LeadForm.() -> LeadForm) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Trade-in", form, LeadField.TRADE_IN, Modifier.weight(1f))
        listOf(false to "No", true to "Yes").forEach { (value, label) ->
            Row(
                Modifier.selectable(form.tradeIn == value, role = Role.RadioButton) {
                    onEdit(LeadField.TRADE_IN) { copy(tradeIn = value) }
                }.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = form.tradeIn == value, onClick = null)
                Text(label, Modifier.padding(start = 4.dp))
            }
        }
    }
    if (form.tradeIn) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField("Brand", form.tradeInBrand, form, LeadField.TRADE_IN, null, KeyboardType.Text, Modifier.weight(1f)) {
                onEdit(LeadField.TRADE_IN) { copy(tradeInBrand = it) }
            }
            FormTextField("Model", form.tradeInModel, form, LeadField.TRADE_IN, null, KeyboardType.Text, Modifier.weight(1f)) {
                onEdit(LeadField.TRADE_IN) { copy(tradeInModel = it) }
            }
        }
        FormTextField("Year", form.tradeInYear, form, LeadField.TRADE_IN, error, KeyboardType.Number) {
            onEdit(LeadField.TRADE_IN) { copy(tradeInYear = it.filter(Char::isDigit).take(4)) }
        }
    }
}

// ---- Field wrappers: read the ✨ / ! markers from the form ----------------------------------

@Composable
private fun FieldLabel(text: String, form: LeadForm, field: LeadField, modifier: Modifier = Modifier) =
    com.example.hackchaton_2026_crm.ui.components.FieldLabel(
        text, field in form.aiFilled, field in form.lowConfidence, modifier,
    )

@Composable
private fun FormTextField(
    label: String,
    value: String,
    form: LeadForm,
    field: LeadField,
    error: String?,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) = com.example.hackchaton_2026_crm.ui.components.FormTextField(
    label, value, field in form.aiFilled, field in form.lowConfidence, error, keyboard, modifier, singleLine, onChange,
)

@Composable
private fun Dropdown(
    label: String,
    value: String?,
    options: List<String>,
    form: LeadForm,
    field: LeadField,
    error: String?,
    onSelect: (String) -> Unit,
) = com.example.hackchaton_2026_crm.ui.components.Dropdown(
    label, value, options, field in form.aiFilled, field in form.lowConfidence, error, onSelect,
)

@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    form: LeadForm,
    field: LeadField,
    error: String?,
    minDate: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
) = com.example.hackchaton_2026_crm.ui.components.DateField(
    label, value, field in form.aiFilled, field in form.lowConfidence, error, minDate, onSelect,
)
