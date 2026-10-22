package com.example.hackchaton_2026_crm.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import java.time.LocalDate

/** Label with ✨ ([ai] = filled by AI) and ! ([check] = please check) markers. */
@Composable
fun FieldLabel(text: String, ai: Boolean, check: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text)
        if (ai) {
            Spacer(Modifier.width(4.dp))
            AiMark()
        }
        if (check) {
            Text("  !", fontWeight = FontWeight.Bold, color = LocalStatusColors.current.dueToday)
        }
    }
}

/** Amber outline for low-confidence fields. */
@Composable
private fun fieldColors(check: Boolean) =
    if (check) {
        val amber = LocalStatusColors.current.dueToday
        OutlinedTextFieldDefaults.colors(unfocusedBorderColor = amber, focusedBorderColor = amber)
    } else OutlinedTextFieldDefaults.colors()

@Composable
fun FormTextField(
    label: String,
    value: String,
    ai: Boolean,
    check: Boolean,
    error: String?,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { FieldLabel(label, ai, check) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        colors = fieldColors(check),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(
    label: String,
    value: String?,
    options: List<String>,
    ai: Boolean,
    check: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "",
            onValueChange = {},
            readOnly = true,
            label = { FieldLabel(label, ai, check) },
            placeholder = { Text("Select…") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = fieldColors(check),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private const val DAY_MS = 86_400_000L

/** Read-only field that opens a date picker; [minDate] disables earlier days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    ai: Boolean,
    check: Boolean,
    error: String?,
    minDate: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value?.let(Format::shortDate)?.let { "$it ${value.year}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { FieldLabel(label, ai, check) },
            placeholder = { Text("Pick a date") },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            colors = fieldColors(check),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Read-only text fields swallow clicks; overlay a click target.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.toEpochDay()?.times(DAY_MS),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    minDate == null || utcTimeMillis / DAY_MS >= minDate.toEpochDay()
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onSelect(pickerState.selectedDateMillis?.let { LocalDate.ofEpochDay(it / DAY_MS) })
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { onSelect(null); open = false }) { Text("Clear") }
            },
        ) { DatePicker(pickerState) }
    }
}
