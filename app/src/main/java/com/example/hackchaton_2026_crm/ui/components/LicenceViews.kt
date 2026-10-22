package com.example.hackchaton_2026_crm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hackchaton_2026_crm.data.LicenceRules
import com.example.hackchaton_2026_crm.data.LicenceType
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import java.time.LocalDate

/**
 * A driving licence as read from a photo (or saved on the customer), and whether it allows a test drive.
 * The CDL / PDL / LDL chips only work when the photo couldn't tell the type ([typeLocked] false); a type
 * read from the photo (e.g. the red "P") can only change by rescanning, so a P licence can't be made a CDL.
 */
@Composable
fun LicencePanel(
    title: String,
    maskedIc: String?,
    type: LicenceType?,
    typeLocked: Boolean,
    classes: List<String>,
    validTo: LocalDate?,
    warnings: List<String>,
    onTypeChange: (LicenceType) -> Unit,
    modifier: Modifier = Modifier,
    sameIc: Boolean = true,
    onRemove: (() -> Unit)? = null,
) {
    val status = LocalStatusColors.current
    val rule = LicenceRules.check(type, classes, validTo, Format.today(), sameIc)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Badge, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove scanned licence") }
            }
            Text(
                listOfNotNull(
                    maskedIc?.takeIf { it.isNotBlank() },
                    classes.takeIf { it.isNotEmpty() }?.let { "Class ${it.joinToString(" ")}" },
                    validTo?.let { "Valid to ${Format.longDate(it)}" },
                ).joinToString(" · ").ifEmpty { "Class and validity not read" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Licence type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LicenceType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { if (!typeLocked) onTypeChange(t) },
                        enabled = !typeLocked,
                        label = { Text(t.name) },
                    )
                }
            }
            Text(
                when {
                    typeLocked -> "Read from the photo. Rescan the licence if this is wrong."
                    type == null -> "The photo couldn't tell. Check the card and pick the type."
                    else -> "Picked by the salesperson (the photo couldn't tell)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                Icon(
                    if (rule.allowed) Icons.Filled.CheckCircle else Icons.Filled.Block, null,
                    tint = if (rule.allowed) status.ok else status.overdue, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (rule.allowed) "Can book a test drive" else rule.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.allowed) status.ok else status.overdue,
                )
            }
            warnings.filterNot { it.startsWith("Licence type") || it == "Licence expired" }.forEach {
                Text("! $it", style = MaterialTheme.typography.bodySmall, color = status.dueToday, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
}
