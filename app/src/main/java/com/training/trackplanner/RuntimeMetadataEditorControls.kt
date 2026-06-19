package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MetadataSingleSelectField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { open = true }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value.ifBlank { "선택 안 함" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (open) {
        MetadataOptionDialog(
            title = label,
            options = options,
            selected = setOf(value),
            multiple = false,
            onDismiss = { open = false },
            onApply = { selected ->
                onValueChange(selected.firstOrNull().orEmpty())
                open = false
            }
        )
    }
}

@Composable
internal fun MetadataMultiSelectField(
    label: String,
    selected: List<String>,
    options: List<String>,
    onValueChange: (List<String>) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { open = true }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    when {
                        selected.isEmpty() -> "선택 안 함"
                        selected.size == 1 -> selected.first()
                        else -> "${selected.first()} 외 ${selected.size - 1}개"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selected.take(3).forEach { value ->
                    AssistChip(onClick = { open = true }, label = { Text(value) })
                }
            }
        }
    }
    if (open) {
        MetadataOptionDialog(
            title = label,
            options = options,
            selected = selected.toSet(),
            multiple = true,
            onDismiss = { open = false },
            onApply = { values ->
                onValueChange(values.sorted())
                open = false
            }
        )
    }
}

@Composable
private fun MetadataOptionDialog(
    title: String,
    options: List<String>,
    selected: Set<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember(selected) { mutableStateOf(selected.filter(String::isNotBlank).toSet()) }
    val effectiveOptions = remember(options, selected) {
        (options + selected).filter(String::isNotBlank).distinct().sorted()
    }
    val filtered = remember(effectiveOptions, query) {
        effectiveOptions.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("검색") },
                    singleLine = true
                )
                if (multiple) {
                    TextButton(onClick = { draft = emptySet() }) { Text("모두 해제") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = if (multiple) {
                                        if (option in draft) draft - option else draft + option
                                    } else {
                                        setOf(option)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = option in draft, onCheckedChange = null)
                            Text(option, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
internal fun MetadataEditorSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(if (expanded) "접기" else "펼치기")
            }
        }
        if (expanded) content()
    }
}
