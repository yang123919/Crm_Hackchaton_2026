package com.example.hackchaton_2026_crm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.platform.LocalContext
import com.example.hackchaton_2026_crm.reminder.Reminders
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.data.Settings
import com.example.hackchaton_2026_crm.data.SettingsRepository
import com.example.hackchaton_2026_crm.ui.components.Dropdown
import com.example.hackchaton_2026_crm.ui.containerFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    /** False when no GEMINI_API_KEY was built in: Demo mode is then forced on. */
    val hasRealAi: Boolean,
) : ViewModel() {
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setName(name: String) = viewModelScope.launch { repository.setSalespersonName(name) }
    fun setOutlet(outlet: String?) = viewModelScope.launch { repository.setDefaultOutlet(outlet) }
    fun setDemo(on: Boolean) = viewModelScope.launch { repository.setDemoMode(on) }
    fun setLanguage(language: MessageLanguage) = viewModelScope.launch { repository.setWhatsappLanguage(language) }

    companion object {
        val Factory = containerFactory { SettingsViewModel(it.settingsRepository, it.aiService.hasRealAi) }
    }
}

private const val NO_OUTLET = "None"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = s.salespersonName,
                onValueChange = viewModel::setName,
                label = { Text("Salesperson name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Dropdown(
                label = "Default outlet",
                value = s.defaultOutlet ?: NO_OUTLET,
                options = listOf(NO_OUTLET) + ReferenceLists.outlets,
                ai = false,
                check = false,
                error = null,
            ) { viewModel.setOutlet(it.takeIf { o -> o != NO_OUTLET }) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demo mode (offline AI)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (viewModel.hasRealAi) "Canned AI answers, no network needed. Use if Wi-Fi fails on stage."
                        else "Always on: no Gemini API key in this build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = s.demoMode || !viewModel.hasRealAi,
                    onCheckedChange = viewModel::setDemo,
                    enabled = viewModel.hasRealAi,
                )
            }

            Column {
                Text("Follow-up reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Daily at 9:00 am for overdue and due-today customers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { Reminders.sendNow(context) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Send test reminder now")
                }
            }

            Column {
                Text("WhatsApp language", style = MaterialTheme.typography.titleMedium)
                Row {
                    MessageLanguage.entries.forEach { language ->
                        Row(
                            Modifier.selectable(s.whatsappLanguage == language, role = Role.RadioButton) {
                                viewModel.setLanguage(language)
                            }.padding(end = 24.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.whatsappLanguage == language, onClick = null)
                            Text(language.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
