package com.example.hackchaton_2026_crm.ui.lead

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.ui.containerFactory
import com.example.hackchaton_2026_crm.ui.navigation.LeadDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LeadDetailUiState(
    val loading: Boolean = true,
    val lead: Lead? = null,
    val entries: List<FollowUpEntry> = emptyList(),
)

class LeadDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: LeadRepository,
) : ViewModel() {
    val leadId: Long = savedStateHandle.toRoute<LeadDetailRoute>().leadId

    val state: StateFlow<LeadDetailUiState> = combine(
        repository.observeLead(leadId),
        repository.observeEntries(leadId),
    ) { lead, entries ->
        LeadDetailUiState(loading = false, lead = lead, entries = entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadDetailUiState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteLead(leadId)
            onDone()
        }
    }

    companion object {
        val Factory = containerFactory { LeadDetailViewModel(createSavedStateHandle(), it.leadRepository) }
    }
}
