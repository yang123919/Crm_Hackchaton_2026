package com.example.hackchaton_2026_crm.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.data.SeedData
import com.example.hackchaton_2026_crm.data.SettingsRepository
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

data class TodayUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val overdue: List<Lead> = emptyList(),
    val dueToday: List<Lead> = emptyList(),
    val capturedThisWeek: Int = 0,
)

class TodayViewModel(
    repository: LeadRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<TodayUiState> = combine(
        repository.observeLeads(),
        repository.observeCapturedThisWeek(),
        settings.settings,
    ) { leads, captured, prefs ->
        val today = Format.today()
        val open = leads.filter { it.nextFollowUpAt != null && it.salesStage !in ReferenceLists.closedStages }
        TodayUiState(
            loading = false,
            greeting = "${Format.greeting(LocalTime.now(SeedData.ZONE).hour)}, ${prefs.salespersonName}",
            overdue = open.filter { it.nextFollowUpAt!! < today }.sortedBy { it.nextFollowUpAt },
            dueToday = open.filter { it.nextFollowUpAt == today }.sortedBy { it.fullName },
            capturedThisWeek = captured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    companion object {
        val Factory = containerFactory { TodayViewModel(it.leadRepository, it.settingsRepository) }
    }
}
