package com.training.trackplanner

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.v22.BBlockDefinition
import com.training.trackplanner.data.v22.BChangeAmplitude
import com.training.trackplanner.data.v22.BProgressionField
import com.training.trackplanner.data.v22.BVolumeAdjustment

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BBlockAdvancedControls(
    block: BBlockDefinition,
    onChange: (BBlockDefinition) -> Unit,
    enabled: Boolean
) {
    ProgramOptionRow {
        ProgramDropdown(
            modifier = Modifier.weight(1f).widthIn(min = 180.dp),
            label = "변화 폭",
            selected = block.amplitude,
            options = BChangeAmplitude.entries.toList(),
            optionLabel = { when (it) {
                BChangeAmplitude.AUTO -> "운동 기록 기반 자동"
                BChangeAmplitude.SMALL -> "소폭 · 주력 운동 중심"
                BChangeAmplitude.MEDIUM -> "중간 · 보조 운동까지"
            } },
            onSelect = { onChange(block.copy(amplitude = it)) },
            enabled = enabled
        )
        ProgramDropdown(
            modifier = Modifier.weight(1f).widthIn(min = 180.dp),
            label = "증가 조절 순서",
            selected = block.adjustment,
            options = BVolumeAdjustment.entries.toList(),
            optionLabel = { when (it) {
                BVolumeAdjustment.AUTO -> "목적별 자동"
                BVolumeAdjustment.REPS_FIRST -> "횟수 우선"
                BVolumeAdjustment.LOAD_FIRST -> "중량 우선"
                BVolumeAdjustment.SETS_FIRST -> "메인 세트 우선"
                BVolumeAdjustment.CUSTOM -> "직접 조합"
            } },
            onSelect = { onChange(block.copy(adjustment = it)) },
            enabled = enabled
        )
    }
    if (block.adjustment == BVolumeAdjustment.CUSTOM) {
        val available = listOf(BProgressionField.REPS, BProgressionField.LOAD, BProgressionField.SETS)
        ProgramOptionRow {
            ProgramDropdown(
                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                label = "조합 항목 수",
                selected = block.customPriority.size,
                options = (1..3).toList(),
                optionLabel = { it.toString() + "개" },
                onSelect = { count ->
                    val order = block.customPriority
                    onChange(block.copy(customPriority =
                        if (count < order.size) order.take(count)
                        else order + available.filterNot(order::contains).take(count - order.size)
                    ))
                },
                enabled = enabled
            )
        }
        block.customPriority.forEachIndexed { index, field ->
            ProgramOptionRow {
                ProgramDropdown(
                    modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                    label = "조절 " + (index + 1) + "순위",
                    selected = field,
                    options = available,
                    optionLabel = { when (it) {
                        BProgressionField.REPS -> "반복 횟수"
                        BProgressionField.LOAD -> "중량"
                        BProgressionField.SETS -> "메인 운동 세트"
                        BProgressionField.MOVEMENT_QUALITY -> "동작 품질"
                    } },
                    onSelect = { choice ->
                        val newOrder = block.customPriority.toMutableList()
                        val other = newOrder.indexOf(choice)
                        if (other >= 0) newOrder[other] = field
                        newOrder[index] = choice
                        onChange(block.copy(customPriority = newOrder))
                    },
                    enabled = enabled
                )
            }
        }
    }
    Text("변화 1단계는 중량 최소 단위, 세트 1개 또는 반복 1회입니다. 소폭·중간은 조절 운동 범위를 바꾸며 단계 크기를 배수로 늘리지 않습니다.")
}
