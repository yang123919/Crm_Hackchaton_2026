package com.example.hackchaton_2026_crm.ui.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.hackchaton_2026_crm.ai.AiRouter
import com.example.hackchaton_2026_crm.ai.Normalizer
import com.example.hackchaton_2026_crm.data.CaptureMetric
import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.speech.SpeechCorrector
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import com.example.hackchaton_2026_crm.ui.navigation.FollowUpRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** The editable "AI suggestion" block of wireframe 6. */
data class FollowUpDraft(
    val summary: String = "",
    val nextAction: String = "",
    val nextDate: LocalDate? = null,
    val stage: String = "",
    val fromAi: Boolean = false,
    /** The AI gave a date we couldn't use: ask the user to pick one. */
    val checkDate: Boolean = false,
)

data class FollowUpUiState(
    val lead: Lead? = null,
    val transcript: String = "",
    val working: Boolean = false,
    val error: String? = null,
    /** Null until processed (or "Write it myself"). */
    val draft: FollowUpDraft? = null,
    val usedDemo: Boolean = false,
    val saving: Boolean = false,
) {
    val summaryError get() = if (draft != null && draft.summary.isBlank()) "Required" else null
    val dateError get() = draft?.nextDate?.takeIf { it.isBefore(Format.today()) }?.let { "Pick today or later" }
    val canSave get() = draft != null && !saving && !working && summaryError == null && dateError == null
}

class FollowUpViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: LeadRepository,
    private val ai: AiRouter,
) : ViewModel() {
    private val leadId = savedStateHandle.toRoute<FollowUpRoute>().leadId
    private val startedAt = Instant.now()

    private val _state = MutableStateFlow(FollowUpUiState())
    val state: StateFlow<FollowUpUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var aiJob: Job? = null

    init {
        viewModelScope.launch { _state.update { it.copy(lead = repository.getLead(leadId)) } }
    }

    fun setTranscript(text: String) = _state.update { it.copy(transcript = text) }

    fun process(forceDemo: Boolean) {
        val lead = _state.value.lead ?: return
        val transcript = _state.value.transcript.trim().takeIf { it.isNotEmpty() } ?: return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            try {
                val demo = forceDemo || ai.usesDemo()
                val today = Format.today()
                val raw = (if (demo) ai.demo else ai).extractFollowUp(SpeechCorrector.fix(transcript), lead, today)
                val n = Normalizer.normalizeFollowUp(raw, lead.salesStage, today)
                _state.update {
                    it.copy(
                        working = false,
                        usedDemo = demo,
                        draft = FollowUpDraft(n.summary, n.nextAction, n.nextDate, n.salesStage, fromAi = true, checkDate = n.dateDropped),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = e.message ?: "Couldn't reach AI.") }
            }
        }
    }

    /** Skip AI: start the draft from the typed notes. */
    fun writeManually() {
        aiJob?.cancel()
        val s = _state.value
        _state.update {
            it.copy(
                working = false,
                error = null,
                draft = FollowUpDraft(summary = s.transcript.trim(), stage = s.lead?.salesStage.orEmpty()),
            )
        }
    }

    fun editDraft(change: FollowUpDraft.() -> FollowUpDraft) =
        _state.update { s -> s.copy(draft = s.draft?.change()) }

    fun save() {
        val s = _state.value
        val lead = s.lead ?: return
        val draft = s.draft ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = Format.today()
            repository.addFollowUp(
                lead.copy(
                    salesStage = draft.stage.ifBlank { lead.salesStage },
                    nextFollowUpAt = draft.nextDate,
                    nextAction = draft.nextAction.trim().ifBlank { null },
                ),
                FollowUpEntry(
                    leadId = lead.id,
                    date = today,
                    summary = draft.summary.trim(),
                    nextAction = draft.nextAction.trim(),
                    nextDate = draft.nextDate,
                    rawTranscript = s.transcript.trim(),
                ),
            )
            repository.recordMetric(
                CaptureMetric(startedAt = startedAt, savedAt = Instant.now(), type = CaptureMetric.TYPE_FOLLOW_UP),
            )
            _saved.send(Unit)
        }
    }

    companion object {
        val Factory = containerFactory { FollowUpViewModel(createSavedStateHandle(), it.leadRepository, it.aiService) }
    }
}
