package com.example.hackchaton_2026_crm.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.LeadGroups
import com.example.hackchaton_2026_crm.data.SeedData
import com.example.hackchaton_2026_crm.data.SettingsRepository
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.example.hackchaton_2026_crm.data.CaptureMetric
import java.time.Duration
import java.time.LocalTime
import kotlin.math.roundToInt

data class TodayUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val overdue: List<Lead> = emptyList(),
    val dueToday: List<Lead> = emptyList(),
    val capturedThisWeek: Int = 0,
    val demoMode: Boolean = false,
    /** Average mic-tap → save time for new customers; null until the first capture. */
    val avgCaptureSeconds: Int? = null,
)

/** Captures longer than 30 min were abandoned and resumed, not real capture time. */
fun averageCaptureSeconds(metrics: List<CaptureMetric>): Int? = metrics
    .filter { it.type == CaptureMetric.TYPE_NEW_LEAD }
    .map { Duration.between(it.startedAt, it.savedAt).seconds }
    .filter { it in 1..1_800 }
    .takeIf { it.isNotEmpty() }
    ?.average()?.roundToInt()

class TodayViewModel(
    repository: LeadRepository,
    settings: SettingsRepository,
    hasRealAi: Boolean,
) : ViewModel() {
    val state: StateFlow<TodayUiState> = combine(
        repository.observeLeads(),
        settings.settings,
        repository.observeMetrics(),
    ) { leads, prefs, metrics ->
        val today = Format.today()
        TodayUiState(
            loading = false,
            greeting = "${Format.greeting(LocalTime.now(SeedData.ZONE).hour)}, ${prefs.salespersonName}",
            overdue = LeadGroups.overdue(leads, today),
            dueToday = LeadGroups.dueToday(leads, today),
            capturedThisWeek = LeadGroups.capturedThisWeek(leads, today).size,
            demoMode = prefs.demoMode || !hasRealAi,
            avgCaptureSeconds = averageCaptureSeconds(metrics),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    companion object {
        val Factory = containerFactory { TodayViewModel(it.leadRepository, it.settingsRepository, it.aiService.hasRealAi) }
    }
}
