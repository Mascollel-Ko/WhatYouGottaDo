package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ProgramDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun EquipmentToggleGrid(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "사용 가능한 운동기구",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        defaultProgramEquipment.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { equipment ->
                    val isSelected = equipment.token in selected
                    val buttonModifier = Modifier.weight(1f)
                    if (isSelected) {
                        Button(
                            modifier = buttonModifier,
                            enabled = enabled,
                            onClick = { onChange(selected - equipment.token) }
                        ) {
                            Text(equipment.label)
                        }
                    } else {
                        OutlinedButton(
                            modifier = buttonModifier,
                            enabled = enabled,
                            onClick = { onChange(selected + equipment.token) }
                        ) {
                            Text(equipment.label)
                        }
                    }
                }
                if (row.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
internal fun ProgramNumberField(
    modifier: Modifier,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { if (it.isUnsignedInt()) onChange(it) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
internal fun ProgramDecimalField(
    modifier: Modifier,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { if (isDecimalInput(it)) onChange(it) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

private data class ProgramEquipmentOption(
    val token: String,
    val label: String
)

internal fun ProgramGoal.displayLabel(): String =
    when (this) {
        ProgramGoal.BADMINTON_SUPPORT -> "배드민턴 지원 웨이트"
        ProgramGoal.STRENGTH -> "스트렝스"
        ProgramGoal.BODYBUILDING -> "보디빌딩"
        ProgramGoal.FUNCTIONAL_CONDITIONING -> "기능성/컨디셔닝"
    }

internal fun String?.toProgramGoal(): ProgramGoal =
    runCatching { ProgramGoal.valueOf(orEmpty()) }.getOrNull() ?: ProgramGoal.BADMINTON_SUPPORT

internal fun ProgramPeriodizationType.displayLabel(): String =
    when (this) {
        ProgramPeriodizationType.AUTO -> "자동 추천"
        ProgramPeriodizationType.STEP_DELOAD -> "3주 누적 + 1주 디로드"
        ProgramPeriodizationType.BADMINTON_WAVE -> "배드민턴 병행 파형"
        ProgramPeriodizationType.DAILY_UNDULATING -> "일간 변동 주기화"
        ProgramPeriodizationType.LINEAR_STRENGTH -> "선형 스트렝스형"
    }

internal fun String?.toProgramPeriodizationType(): ProgramPeriodizationType =
    runCatching { ProgramPeriodizationType.valueOf(orEmpty()) }.getOrNull() ?: ProgramPeriodizationType.AUTO

internal fun String?.toEquipmentSet(): Set<String> =
    orEmpty()
        .split('|', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

internal val badmintonRatioOptions = (0..9).reversed().associate { step ->
    val badminton = step * 10
    (badminton / 100.0) to "$badminton:${100 - badminton}"
}

internal val sportStrengthRatioOptions = listOf(
    "AUTO",
    "90/10",
    "70/30",
    "50/50",
    "30/70",
    "0/100"
)

private val defaultProgramEquipment = listOf(
    ProgramEquipmentOption("BARBELL", "바벨"),
    ProgramEquipmentOption("DUMBBELL", "덤벨"),
    ProgramEquipmentOption("CABLE", "케이블"),
    ProgramEquipmentOption("MACHINE", "머신"),
    ProgramEquipmentOption("KETTLEBELL", "케틀벨"),
    ProgramEquipmentOption("LANDMINE", "랜드마인"),
    ProgramEquipmentOption("BAND", "밴드"),
    ProgramEquipmentOption("BODYWEIGHT", "맨몸")
)

internal val defaultProgramEquipmentTokens = defaultProgramEquipment.map { it.token }.toSet()
