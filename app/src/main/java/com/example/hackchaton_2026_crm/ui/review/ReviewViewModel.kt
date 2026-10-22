package com.example.hackchaton_2026_crm.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.hackchaton_2026_crm.ai.AiRouter
import com.example.hackchaton_2026_crm.ai.LeadField
import com.example.hackchaton_2026_crm.ai.Normalizer
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.SettingsRepository
import com.example.hackchaton_2026_crm.ocr.IcInfo
import com.example.hackchaton_2026_crm.ocr.IcNumbers
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import com.example.hackchaton_2026_crm.ui.navigation.ReviewRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface ReviewPhase {
    /** [step] drives the "✓ Reading your notes…" checklist; [slow] shows the escape hatches. */
    data class Working(val step: Int = 0, val slow: Boolean = false) : ReviewPhase
    data class Failed(val message: String) : ReviewPhase
    data object Editing : ReviewPhase
}

data class ReviewUiState(
    val phase: ReviewPhase = ReviewPhase.Working(),
    val form: LeadForm = LeadForm(),
    val usedDemo: Boolean = false,
    /** Existing customer with the same mobile, unless the user chose "Save as new". */
    val duplicate: Lead? = null,
    val saving: Boolean = false,
) {
    val errors get() = form.errors(Format.today())
    val canSave get() = phase == ReviewPhase.Editing && !saving && errors.isEmpty()
}

class ReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: LeadRepository,
    private val settings: SettingsRepository,
    private val ai: AiRouter,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReviewRoute>()
    val transcript: String = route.transcript
    private val startedAt = Instant.ofEpochMilli(route.startedAtMillis)
    private val ic = IcInfo(
        fullName = route.icName, icNumber = route.icNumber, address = route.icAddress, gender = route.icGender,
    )

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val _saved = Channel<Long>(Channel.BUFFERED)
    /** Emits the new lead id once saved. */
    val saved = _saved.receiveAsFlow()

    private var aiJob: Job? = null
    private var duplicateJob: Job? = null
    private var dismissedDuplicateId: Long? = null

    init {
        // An IC scan alone is enough to start; there is nothing for the AI to read then.
        if (transcript.isBlank()) startFromIcOnly() else process(forceDemo = false)
    }

    private fun startFromIcOnly() {
        viewModelScope.launch {
            val form = LeadForm(outlet = settings.current().defaultOutlet).withIc(ic)
            _state.update { it.copy(phase = ReviewPhase.Editing, form = form) }
            checkDuplicate(form)
        }
    }

    fun process(forceDemo: Boolean) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _state.update { it.copy(phase = ReviewPhase.Working()) }
            val ticker = launch {
                for (step in 1..3) {
                    delay(700)
                    _state.update { s -> (s.phase as? ReviewPhase.Working)?.let { s.copy(phase = it.copy(step = step)) } ?: s }
                }
                delay(5_000)
                _state.update { s -> (s.phase as? ReviewPhase.Working)?.let { s.copy(phase = it.copy(slow = true)) } ?: s }
            }
            try {
                val demo = forceDemo || ai.usesDemo()
                val today = Format.today()
                val extraction = (if (demo) ai.demo else ai).extractLead(transcript, today)
                val form = LeadForm.from(Normalizer.normalizeLead(extraction, today), settings.current().defaultOutlet)
                    .withIc(ic)
                _state.update { it.copy(phase = ReviewPhase.Editing, form = form, usedDemo = demo) }
                checkDuplicate(form)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = ReviewPhase.Failed(e.message ?: "Couldn't reach AI.")) }
            } finally {
                ticker.cancel()
            }
        }
    }

    /** "Fill in manually": skip AI, keep the transcript visible as the note. */
    fun fillManually() {
        aiJob?.cancel()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = ReviewPhase.Editing,
                    form = LeadForm(outlet = settings.current().defaultOutlet, summary = transcript).withIc(ic),
                    usedDemo = false,
                )
            }
        }
    }

    fun edit(field: LeadField, change: LeadForm.() -> LeadForm) {
        _state.update { it.copy(form = it.form.change().let { f -> f.copy(lowConfidence = f.lowConfidence - field) }) }
        // Same phone or same IC as an existing customer: warn as soon as either is typed.
        if (field == LeadField.MOBILE || field == LeadField.IC_NUMBER) checkDuplicate(_state.value.form)
    }

    fun saveAsNew() {
        dismissedDuplicateId = _state.value.duplicate?.id
        _state.update { it.copy(duplicate = null) }
    }

    private fun checkDuplicate(form: LeadForm) {
        duplicateJob?.cancel()
        val mobile = form.normalizedMobile
        val ic = IcNumbers.normalize(form.icNumber)
        duplicateJob = viewModelScope.launch {
            val existing = (mobile?.let { repository.findByMobile(it) } ?: ic?.let { repository.findByIc(it) })
                ?.takeIf { it.id != dismissedDuplicateId }
            _state.update { it.copy(duplicate = existing) }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = Format.today()
            val id = repository.createLead(s.form.toLead(today), s.form.toFirstEntry(today, transcript))
            repository.recordMetric(LeadForm.metric(startedAt))
            _saved.send(id)
        }
    }

    companion object {
        val Factory = containerFactory {
            ReviewViewModel(createSavedStateHandle(), it.leadRepository, it.settingsRepository, it.aiService)
        }
    }
}
