package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueDetailUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget

@Composable
internal fun FatigueDetailView(
    state: FatigueDetailUiState,
    onPeriodChange: (FatigueAnalysisPeriod) -> Unit,
    onFatigueTargetToggle: (FatigueTarget) -> Unit,
    onContributionTargetChange: (FatigueTarget) -> Unit,
    onContributionGroupingChange: (ContributionGrouping) -> Unit,
    onContributionSourcesApply: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FatiguePeriodSelector(state.selectedPeriod, onPeriodChange)
        Text("피로도 변화", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FatigueTargetSelector(state.selectedFatigueTargets, multiple = true, onFatigueTargetToggle)
        FatigueTrendChart(
            series = state.fatigueTrendSeries,
            selectedKeys = state.selectedFatigueTargets.map { it.name }.toSet()
        )
        if (state.usesWeeklyAggregation) {
            Text("주간 평균", style = MaterialTheme.typography.labelSmall)
        }

        Text("피로 기여도 추이", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FatigueTargetSelector(
            selected = setOf(state.contributionTarget),
            multiple = false,
            onToggle = onContributionTargetChange
        )
        ContributionGroupingSelector(state.contributionGrouping, onContributionGroupingChange)
        FatigueSourceMultiSelect(
            sources = state.contributionSeries,
            selected = state.selectedContributionSourceKeys,
            defaultSelection = state.defaultContributionSourceKeys,
            onApply = onContributionSourcesApply
        )
        FatigueContributionChart(
            series = state.contributionSeries,
            selectedKeys = state.selectedContributionSourceKeys,
            emptyMessage = if (state.selectedContributionSourceKeys.isEmpty()) {
                "표시할 항목을 선택하세요."
            } else {
                "운동 기록이 쌓이면 표시됩니다."
            }
        )
    }
}
