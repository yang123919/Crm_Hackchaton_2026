package com.example.hackchaton_2026_crm.ui.whatsapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackchaton_2026_crm.ai.AiRouter
import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.data.SettingsRepository
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhatsAppUiState(
    val lead: Lead? = null,
    val language: MessageLanguage = MessageLanguage.EN,
    val text: String = "",
    val loading: Boolean = true,
    /** Set when the real AI failed and we fell back to the offline draft. */
    val notice: String? = null,
)

class WhatsAppViewModel(
    private val leadId: Long,
    private val repository: LeadRepository,
    private val settings: SettingsRepository,
    private val ai: AiRouter,
) : ViewModel() {
    private val _state = MutableStateFlow(WhatsAppUiState())
    val state: StateFlow<WhatsAppUiState> = _state.asStateFlow()

    private var lastEntry: FollowUpEntry? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val lead = repository.getLead(leadId)
            lastEntry = repository.latestEntry(leadId)
            _state.update { it.copy(lead = lead, language = settings.current().whatsappLanguage) }
            generate()
        }
    }

    fun setLanguage(language: MessageLanguage) {
        if (language == _state.value.language) return
        _state.update { it.copy(language = language) }
        generate()
    }

    fun setText(text: String) = _state.update { it.copy(text = text) }

    fun generate() {
        val lead = _state.value.lead ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, notice = null) }
            val language = _state.value.language
            val name = settings.current().salespersonName
            val (text, notice) = try {
                ai.draftWhatsApp(lead, lastEntry, language, name) to null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never leave the salesperson without a message on stage: use the offline template.
                ai.demo.draftWhatsApp(lead, lastEntry, language, name) to "AI unavailable, using offline draft"
            }
            _state.update { it.copy(loading = false, text = text, notice = notice) }
        }
    }

    /** Logs "WhatsApp sent" to the lead's history (wireframe 7). The lead's next step is unchanged. */
    fun logSent() {
        val s = _state.value
        val lead = s.lead ?: return
        viewModelScope.launch {
            val preview = s.text.trim().let { if (it.length > 80) it.take(80).trimEnd() + "…" else it }
            repository.addEntry(
                FollowUpEntry(
                    leadId = lead.id,
                    date = Format.today(),
                    summary = "WhatsApp sent: \"$preview\"",
                    nextAction = lead.nextAction.orEmpty(),
                    nextDate = lead.nextFollowUpAt,
                    rawTranscript = s.text,
                ),
            )
        }
    }

    companion object {
        fun factory(leadId: Long) = containerFactory {
            WhatsAppViewModel(leadId, it.leadRepository, it.settingsRepository, it.aiService)
        }
    }
}
