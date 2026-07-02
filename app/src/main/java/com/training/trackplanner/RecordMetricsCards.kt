package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.SmashSpeedRecord
import com.training.trackplanner.data.SmashSpeedSummary


@Composable
internal fun SmashSpeedCard(
    date: String,
    records: List<SmashSpeedRecord>,
    onAdd: (Double) -> Unit,
    onDelete: (Long) -> Unit
) {
    val summary = remember(date, records) { SmashSpeedSummary.from(date, records) }
    var expanded by rememberSaveable(date) { mutableStateOf(records.isNotEmpty()) }
    var speedText by rememberSaveable(date) { mutableStateOf("") }
    val speed = speedText.toDoubleOrNull()
    val canSave = speed != null && speed in 1.0..500.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("스매시 속도", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = smashSpeedSummaryText(summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "접기" else "기록")
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = speedText,
                        onValueChange = { if (isDecimalInput(it)) speedText = it },
                        label = { Text("속도") },
                        suffix = { Text("km/h") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Button(
                        enabled = canSave,
                        onClick = {
                            speed?.let(onAdd)
                            speedText = ""
                        }
                    ) {
                        Text("추가")
                    }
                }
                if (records.isEmpty()) {
                    Text(
                        text = "기록 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    records.forEach { record ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${record.attemptIndex ?: "-"}회 · ${formatDecimal(record.speedKmh)} km/h",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { onDelete(record.id) }) {
                                Text("삭제")
                            }
                        }
                    }
                }
            }
        }
    }
}



private fun smashSpeedSummaryText(summary: SmashSpeedSummary): String =
    if (summary.attemptCount == 0) {
        "기록 없음"
    } else {
        "최고 ${formatDecimal(summary.bestSpeedKmh ?: 0.0)} · Top3 ${formatDecimal(summary.top3AverageSpeedKmh ?: 0.0)} · ${summary.attemptCount}회"
    }
