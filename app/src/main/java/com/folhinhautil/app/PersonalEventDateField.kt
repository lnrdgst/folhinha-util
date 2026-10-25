package com.folhinhautil.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.folhinhautil.app.domain.formatPersonalDate
import java.time.LocalDate

/** A single keyboard-accessible, read-only date control. The dialog owns its unconfirmed selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalEventDateField(date: LocalDate, enabled: Boolean = true, onSelected: (LocalDate) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick = { pickerOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Data do evento", style = MaterialTheme.typography.labelMedium)
                Text(formatPersonalDate(date), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null,
                modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (pickerOpen) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.toPickerDateMillis(),
            // Keep strict four-digit dates, including historical personal dates; no past-date restriction.
            yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { pickerOpen = false }, confirmButton = {
            TextButton(enabled = enabled && picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onSelected(pickerDateFromMillis(it)) }
                pickerOpen = false
            }) { Text("Confirmar") }
        }, dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") } }) {
            DatePicker(state = picker, showModeToggle = false,
                title = { Text("Data do evento", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}
