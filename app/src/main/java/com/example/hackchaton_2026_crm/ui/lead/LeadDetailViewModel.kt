package com.example.hackchaton_2026_crm.ui.lead

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.BookingKind
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.LicenceRules
import com.example.hackchaton_2026_crm.data.LicenceType
import com.example.hackchaton_2026_crm.ocr.IcNumbers
import com.example.hackchaton_2026_crm.ocr.LicenceInfo
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.containerFactory
import java.time.LocalDate
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

    /**
     * Stores a licence scanned in the booking sheet. A licence whose IC number differs from the
     * customer's belongs to someone else, so it is refused; [onResult] gets the reason, or null.
     */
    fun saveLicence(info: LicenceInfo, onResult: (String?) -> Unit) {
        val lead = state.value.lead ?: return
        val scannedIc = IcNumbers.normalize(info.icNumber)
        val customerIc = lead.icNumber?.let { IcNumbers.normalize(it) }
        if (scannedIc != null && customerIc != null && scannedIc != customerIc) {
            onResult("This licence belongs to someone else: its IC number doesn't match ${lead.fullName}'s.")
            return
        }
        viewModelScope.launch {
            repository.saveLicence(lead, info.type, info.typeFromPhoto, info.classes, info.validTo, scannedIc)
            onResult(null)
        }
    }

    /** Only when the photo couldn't tell the type; a type read from the photo changes only by rescanning. */
    fun setLicenceType(type: LicenceType) {
        val lead = state.value.lead ?: return
        if (lead.licenceTypeLocked) return
        viewModelScope.launch {
            repository.saveLicence(
                lead, type, typeFromPhoto = false,
                classes = LicenceRules.parseClasses(lead.licenceClasses), validTo = lead.licenceValidTo,
            )
        }
    }

    fun book(kind: BookingKind, date: LocalDate, time: String, onBooked: () -> Unit) {
        val lead = state.value.lead ?: return
        viewModelScope.launch {
            if (repository.book(lead, kind, date, time, Format.today())) onBooked()
        }
    }

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
