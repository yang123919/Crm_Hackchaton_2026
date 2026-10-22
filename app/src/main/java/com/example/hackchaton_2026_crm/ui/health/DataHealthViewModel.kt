package com.example.hackchaton_2026_crm.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackchaton_2026_crm.ai.AiRouter
import com.example.hackchaton_2026_crm.data.HealthFix
import com.example.hackchaton_2026_crm.data.HealthIssue
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataHealthUiState(
    val loading: Boolean = true,
    val issues: List<HealthIssue> = emptyList(),
    /** AI explanation per issue id; an issue without one shows its built-in recommendation. */
    val advice: Map<String, String> = emptyMap(),
    val summary: String? = null,
    val aiLoading: Boolean = false,
    /** Set when the real AI failed and the built-in tips are shown instead. */
    val notice: String? = null,
    /** One-shot confirmation ("Merged 2 customers"), cleared once shown. */
    val message: String? = null,
) {
    val fixable: List<HealthIssue> get() = issues.filter { it.fix != null }
}

class DataHealthViewModel(
    private val repository: LeadRepository,
    private val ai: AiRouter,
) : ViewModel() {
    private val _state = MutableStateFlow(DataHealthUiState())
    val state: StateFlow<DataHealthUiState> = _state.asStateFlow()

    private var adviceJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            val issues = repository.scanHealth(Format.today())
            _state.update { it.copy(loading = false, issues = issues) }
            explain(issues)
        }
    }

    /** Asks the AI only about problems it has not explained yet, so re-scanning after a fix is instant. */
    private fun explain(issues: List<HealthIssue>) {
        val missing = issues.filter { it.id !in _state.value.advice }
        if (missing.isEmpty()) return
        adviceJob?.cancel()
        adviceJob = viewModelScope.launch {
            _state.update { it.copy(aiLoading = true) }
            val (result, notice) = try {
                ai.explainDataHealth(missing) to null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ai.demo.explainDataHealth(missing) to "AI unavailable, showing built-in tips"
            }
            _state.update { s ->
                s.copy(
                    aiLoading = false,
                    notice = notice,
                    summary = s.summary ?: result.summary.ifBlank { null },
                    advice = s.advice + missing.map { it.id }.zip(result.explanations).toMap(),
                )
            }
        }
    }

    fun fix(issue: HealthIssue) {
        val fix = issue.fix ?: return
        viewModelScope.launch {
            repository.applyFix(fix)
            _state.update { it.copy(message = doneMessage(fix)) }
            scan()
        }
    }

    fun fixAll() {
        val fixes = _state.value.fixable.mapNotNull { it.fix }
        if (fixes.isEmpty()) return
        viewModelScope.launch {
            fixes.forEach { repository.applyFix(it) }
            _state.update { it.copy(message = "Fixed ${fixes.size} problem${if (fixes.size == 1) "" else "s"}") }
            scan()
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun doneMessage(fix: HealthFix) = when (fix) {
        is HealthFix.MergeCustomers -> "Merged ${fix.dropIds.size + 1} customers into one"
        is HealthFix.RemoveEntries -> "Removed ${fix.entryIds.size} duplicate ${if (fix.entryIds.size == 1) "entry" else "entries"}"
    }

    companion object {
        val Factory = containerFactory { DataHealthViewModel(it.leadRepository, it.aiService) }
    }
}
