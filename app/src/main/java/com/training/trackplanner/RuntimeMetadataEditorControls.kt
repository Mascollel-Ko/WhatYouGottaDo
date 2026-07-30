package com.training.trackplanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog

@Composable
internal fun MetadataSingleSelectField(
    label: String,
    value: String,
    options: List<String>,
    field: MetadataDisplayField? = null,
    onValueChange: (String) -> Unit
) {
    val catalogue = rememberMetadataDisplayCatalogue()
    val displayOptions = remember(catalogue, field, options, value) {
        displayOptions(catalogue, field, options + value)
    }
    val selected = displayOptions.firstOrNull { option -> option.code == value }
        ?: displayOption(catalogue, field, value)
    var open by remember { mutableStateOf(false) }

    MetadataSelectorSurface(
        label = label,
        summary = selected.label.ifBlank { stringResource(R.string.metadata_not_selected) },
        onClick = { open = true }
    )
    if (open) {
        MetadataOptionDialog(
            title = label,
            options = displayOptions,
            selected = setOf(value),
            multiple = false,
            onDismiss = { open = false },
            onApply = { selectedCodes ->
                onValueChange(selectedCodes.firstOrNull().orEmpty())
                open = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MetadataMultiSelectField(
    label: String,
    selected: List<String>,
    options: List<String>,
    field: MetadataDisplayField? = null,
    onValueChange: (List<String>) -> Unit
) {
    val catalogue = rememberMetadataDisplayCatalogue()
    val displayOptions = remember(catalogue, field, options, selected) {
        displayOptions(catalogue, field, options + selected)
    }
    val selectedOptions = displayOptions.filter { option -> option.code in selected }
    var open by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataSelectorSurface(
            label = label,
            summary = when {
                selectedOptions.isEmpty() -> stringResource(R.string.metadata_not_selected)
                selectedOptions.size == 1 -> selectedOptions.first().label
                else -> "${selectedOptions.first().label} · ${
                    stringResource(R.string.metadata_more_count, selectedOptions.size - 1)
                }"
            },
            onClick = { open = true }
        )
        if (selectedOptions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                selectedOptions.take(MAX_PREVIEW_CHIPS).forEach { option ->
                    AssistChip(onClick = { open = true }, label = { Text(option.label) })
                }
                if (selectedOptions.size > MAX_PREVIEW_CHIPS) {
                    AssistChip(
                        onClick = { open = true },
                        label = {
                            Text(
                                stringResource(
                                    R.string.metadata_more_count,
                                    selectedOptions.size - MAX_PREVIEW_CHIPS
                                )
                            )
                        }
                    )
                }
            }
        }
    }
    if (open) {
        MetadataOptionDialog(
            title = label,
            options = displayOptions,
            selected = selected.toSet(),
            multiple = true,
            onDismiss = { open = false },
            onApply = { selectedCodes ->
                onValueChange(
                    displayOptions
                        .filter { option -> option.code in selectedCodes }
                        .map(MetadataDisplayOption::code)
                )
                open = false
            }
        )
    }
}

@Composable
private fun MetadataSelectorSurface(
    label: String,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 64.dp)
            .semantics { contentDescription = "$label, $summary" },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = summary, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MetadataOptionDialog(
    title: String,
    options: List<MetadataDisplayOption>,
    selected: Set<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember(selected) {
        mutableStateOf(selected.filter(String::isNotBlank).toSet())
    }
    val filtered = remember(options, query) {
        options.filter { option -> option.matches(query) }
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
                    label = { Text(stringResource(R.string.metadata_search)) },
                    singleLine = true
                )
                if (multiple) {
                    TextButton(onClick = { draft = emptySet() }) {
                        Text(stringResource(R.string.metadata_clear_all))
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = MetadataDisplayOption::code) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = if (multiple) {
                                        if (option.code in draft) {
                                            draft - option.code
                                        } else {
                                            draft + option.code
                                        }
                                    } else {
                                        setOf(option.code)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = option.code in draft, onCheckedChange = null)
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) {
                Text(stringResource(R.string.metadata_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.metadata_cancel))
            }
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
    val expansionLabel = if (expanded) {
        stringResource(R.string.metadata_collapse)
    } else {
        stringResource(R.string.metadata_expand)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$title, $expansionLabel"
                },
            onClick = { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(expansionLabel)
            }
        }
        if (expanded) content()
    }
}

@Composable
internal fun rememberMetadataDisplayCatalogue(): MetadataDisplayCatalogue {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    return remember(locale) { MetadataDisplayCatalogue.from(context) }
}

private fun displayOptions(
    catalogue: MetadataDisplayCatalogue,
    field: MetadataDisplayField?,
    codes: Collection<String>
): List<MetadataDisplayOption> =
    if (field == null) {
        codes
            .filter(String::isNotBlank)
            .distinct()
            .map { value ->
                MetadataDisplayOption(
                    code = value,
                    label = value,
                    searchAliases = listOf(value)
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MetadataDisplayOption::label))
    } else {
        catalogue.options(field, codes)
    }

private fun displayOption(
    catalogue: MetadataDisplayCatalogue,
    field: MetadataDisplayField?,
    code: String
): MetadataDisplayOption =
    if (field == null) {
        MetadataDisplayOption(
            code = code,
            label = code,
            searchAliases = listOf(code)
        )
    } else {
        catalogue.option(field, code)
    }

private const val MAX_PREVIEW_CHIPS = 3
