package com.example.hackchaton_2026_crm.ui.lead

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hackchaton_2026_crm.data.BookingKind
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.LicenceRules
import com.example.hackchaton_2026_crm.data.LicenceType
import com.example.hackchaton_2026_crm.ocr.LicenceInfo
import com.example.hackchaton_2026_crm.ocr.LicenceScanner
import com.example.hackchaton_2026_crm.ocr.ScanException
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.capture.ScanButton
import com.example.hackchaton_2026_crm.ui.capture.rememberPhotoSource
import com.example.hackchaton_2026_crm.ui.components.Button
import com.example.hackchaton_2026_crm.ui.components.DateField
import com.example.hackchaton_2026_crm.ui.components.LicencePanel
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

private val TIME_SLOTS = listOf("10:00 am", "12:00 pm", "2:00 pm", "4:00 pm")

/**
 * Book a test drive or a vehicle booking. A driving licence is compulsory: if none is on file the
 * salesperson scans it here, and only a valid CDL with a car class unlocks the Confirm button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSheet(
    lead: Lead,
    onLicenceScanned: (LicenceInfo, onResult: (String?) -> Unit) -> Unit,
    onTypeChange: (LicenceType) -> Unit,
    onBook: (BookingKind, LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var kind by rememberSaveable { mutableStateOf(BookingKind.TEST_DRIVE) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var time by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    val licenceSource = rememberPhotoSource { uri ->
        scanning = true
        scanMessage = null
        try {
            val info = LicenceScanner.scan(context, uri)
            if (info.isEmpty) {
                scanMessage = "Couldn't read a driving licence in that photo. Hold the card upright, fill the frame and avoid glare."
            } else {
                onLicenceScanned(info) { error -> scanMessage = error }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScanException) {
            scanMessage = e.message
        } finally {
            scanning = false
        }
    }

    val type = LicenceType.fromName(lead.licenceType)
    val classes = LicenceRules.parseClasses(lead.licenceClasses)
    val rule = LicenceRules.check(type, classes, lead.licenceValidTo, Format.today())
    val canConfirm = lead.hasLicence && rule.allowed && date != null && time != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Book for ${lead.fullName}", style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BookingKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, BookingKind.entries.size),
                    ) { Text(k.label) }
                }
            }

            Text("Driving licence (required)", style = MaterialTheme.typography.titleSmall)
            if (lead.hasLicence) {
                LicencePanel(
                    title = lead.fullName,
                    maskedIc = lead.maskedIc,
                    type = type,
                    typeLocked = lead.licenceTypeLocked,
                    classes = classes,
                    validTo = lead.licenceValidTo,
                    warnings = emptyList(),
                    onTypeChange = onTypeChange,
                )
            } else {
                Text(
                    "No licence on file. Scan the customer's driving licence: only a CDL can book.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScanButton(
                label = if (lead.hasLicence) "Rescan licence" else "Scan licence",
                icon = Icons.Filled.Badge,
                source = licenceSource,
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            )
            if (scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading the licence on your phone…")
                }
            }
            scanMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

            DateField("Date", date, ai = false, check = false, error = null, minDate = Format.today()) { date = it }
            Text("Time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIME_SLOTS.forEach { slot ->
                    FilterChip(selected = time == slot, onClick = { time = slot }, label = { Text(slot) })
                }
            }

            Button(
                onClick = { onBook(kind, date ?: return@Button, time ?: return@Button) },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Confirm ${kind.label.lowercase()}", style = MaterialTheme.typography.titleMedium) }
            if (!canConfirm) {
                Text(
                    when {
                        !lead.hasLicence -> "Scan the driving licence first."
                        !rule.allowed -> rule.reason
                        date == null -> "Pick a date."
                        else -> "Pick a time."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
