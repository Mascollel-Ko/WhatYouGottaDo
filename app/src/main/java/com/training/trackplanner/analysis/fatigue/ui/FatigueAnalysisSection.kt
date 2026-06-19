package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget

private enum class FatigueAnalysisMode(val label: String) {
    SIMPLE("간단히"),
    DETAIL("자세히")
}

@Composable
fun FatigueAnalysisSection(
    state: FatigueAnalysisUiState,
    onPeriodChange: (FatigueAnalysisPeriod) -> Unit,
    onFatigueTargetToggle: (FatigueTarget) -> Unit,
    onContributionTargetChange: (FatigueTarget) -> Unit,
    onContributionGroupingChange: (ContributionGrouping) -> Unit,
    onContributionSourcesApply: (Set<String>) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(FatigueAnalysisMode.SIMPLE) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("피로도 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FatigueAnalysisMode.entries.forEach { option ->
                    FatigueSelectorChip(option.label, option == mode) { mode = option }
                }
            }
            when {
                state.isLoading -> Text("피로도를 계산하고 있습니다.", style = MaterialTheme.typography.bodySmall)
                state.errorMessage != null -> Text(state.errorMessage, style = MaterialTheme.typography.bodySmall)
                mode == FatigueAnalysisMode.SIMPLE -> FatigueSimpleView(state.simple)
                else -> FatigueDetailView(
                    state = state.detail,
                    onPeriodChange = onPeriodChange,
                    onFatigueTargetToggle = onFatigueTargetToggle,
                    onContributionTargetChange = onContributionTargetChange,
                    onContributionGroupingChange = onContributionGroupingChange,
                    onContributionSourcesApply = onContributionSourcesApply
                )
            }
        }
    }
}
