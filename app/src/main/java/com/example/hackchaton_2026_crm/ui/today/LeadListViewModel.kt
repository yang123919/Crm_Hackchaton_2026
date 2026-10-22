package com.example.hackchaton_2026_crm.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadGroup
import com.example.hackchaton_2026_crm.data.LeadGroups
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import com.example.hackchaton_2026_crm.ui.navigation.LeadListRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LeadListUiState(val loading: Boolean = true, val leads: List<Lead> = emptyList())

class LeadListViewModel(savedStateHandle: SavedStateHandle, repository: LeadRepository) : ViewModel() {
    val group: LeadGroup = savedStateHandle.toRoute<LeadListRoute>().group

    val state: StateFlow<LeadListUiState> = repository.observeLeads()
        .map { leads -> LeadListUiState(loading = false, leads = LeadGroups.of(group, leads, Format.today())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadListUiState())

    companion object {
        val Factory = containerFactory { LeadListViewModel(createSavedStateHandle(), it.leadRepository) }
    }
}
