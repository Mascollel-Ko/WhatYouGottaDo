package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.fatigue.FatigueContributionSeries

@Composable
internal fun FatigueSourceMultiSelect(
    sources: List<FatigueContributionSeries>,
    selected: Set<String>,
    defaultSelection: Set<String>,
    onApply: (Set<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var draftSelection by remember(selected) { mutableStateOf(selected) }
    val filteredSources = remember(sources, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            sources
        } else {
            sources.filter { source ->
                source.sourceLabel.contains(normalizedQuery, ignoreCase = true) ||
                    source.sourceKey.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                draftSelection = selected
                query = ""
                expanded = true
            }
        ) {
            Text(sourceSelectionSummary(sources, selected))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 300.dp, max = 420.dp)
                .heightIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("항목 검색") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { draftSelection = emptySet() }) {
                        Text("모두 해제")
                    }
                    TextButton(onClick = { draftSelection = defaultSelection }) {
                        Text("상위 3개 다시 선택")
                    }
                }
            }
            filteredSources.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(source.sourceLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "선택 기간 기여 ${source.periodContributionPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Checkbox(
                            checked = source.sourceKey in draftSelection,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        draftSelection = if (source.sourceKey in draftSelection) {
                            draftSelection - source.sourceKey
                        } else {
                            draftSelection + source.sourceKey
                        }
                    }
                )
            }
            if (filteredSources.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    text = "검색 결과가 없습니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { expanded = false }) {
                    Text("닫기")
                }
                Button(
                    onClick = {
                        onApply(draftSelection)
                        expanded = false
                    }
                ) {
                    Text("적용")
                }
            }
        }
    }
}

private fun sourceSelectionSummary(
    sources: List<FatigueContributionSeries>,
    selected: Set<String>
): String {
    if (selected.isEmpty()) return "항목 선택"
    val firstLabel = sources.firstOrNull { it.sourceKey in selected }?.sourceLabel
        ?: selected.first()
    return if (selected.size == 1) firstLabel else "$firstLabel 외 ${selected.size - 1}개"
}
