package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        FatigueTarget.entries.forEach { target ->
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
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
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
