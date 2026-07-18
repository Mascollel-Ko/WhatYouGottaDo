package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.training.trackplanner.QuietChoiceChip
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueTarget

@Composable
internal fun FatiguePeriodSelector(
    selected: FatigueAnalysisPeriod,
    onSelect: (FatigueAnalysisPeriod) -> Unit
) {
    SelectorRow {
        FatigueAnalysisPeriod.entries.forEach { period ->
            FatigueSelectorChip(period.label, period == selected) { onSelect(period) }
        }
    }
}

@Composable
internal fun FatigueTargetSelector(
    selected: Set<FatigueTarget>,
    multiple: Boolean,
    onToggle: (FatigueTarget) -> Unit
) {
    SelectorRow {
        FatigueTarget.displayed.forEach { target ->
            FatigueSelectorChip(target.label, target in selected) {
                if (multiple || target !in selected) onToggle(target)
            }
        }
    }
}

@Composable
internal fun ContributionGroupingSelector(
    selected: ContributionGrouping,
    onSelect: (ContributionGrouping) -> Unit
) {
    SelectorRow {
        ContributionGrouping.entries.forEach { grouping ->
            FatigueSelectorChip(grouping.label, grouping == selected) { onSelect(grouping) }
        }
    }
}

@Composable
internal fun FatigueSelectorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    QuietChoiceChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun SelectorRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        content()
    }
}
