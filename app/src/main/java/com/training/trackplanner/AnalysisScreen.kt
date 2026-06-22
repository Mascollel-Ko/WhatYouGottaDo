package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class AnalysisTab(val label: String) {
    COACH("코치 분석"),
    LAB("실험실")
}

@Composable
internal fun AnalysisScreen(viewModel: TrainingViewModel) {
    val stats by viewModel.analysisStats.collectAsState()
    val readiness by viewModel.todayReadinessSummary.collectAsState()
    val fatigueAnalysis by viewModel.fatigueAnalysisState.collectAsState()
    val badmintonTransfer by viewModel.badmintonTransferSummary.collectAsState()
    val coachInsight by viewModel.coachAnalysisInsight.collectAsState()
    val performanceTrend by viewModel.performanceTrendSummary.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AnalysisTab.COACH) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "분석",
                body = "코치 해석과 탐색 도구를 목적에 맞게 나누어 봅니다."
            )
        }
        item {
            AnalysisChipRow(
                labels = AnalysisTab.entries.map { tab -> tab.label },
                selected = selectedTab.ordinal,
                onSelect = { index -> selectedTab = AnalysisTab.entries[index] }
            )
        }
        when (selectedTab) {
            AnalysisTab.COACH -> item {
                CoachAnalysisContent(
                    stats = stats,
                    readiness = readiness,
                    fatigueAnalysis = fatigueAnalysis,
                    badmintonTransfer = badmintonTransfer,
                    coachInsight = coachInsight,
                    performanceTrend = performanceTrend,
                    onPeriodChange = viewModel::selectFatigueAnalysisPeriod,
                    onFatigueTargetToggle = viewModel::toggleFatigueTrendTarget,
                    onContributionTargetChange = viewModel::selectFatigueContributionTarget,
                    onContributionGroupingChange = viewModel::selectFatigueContributionGrouping,
                    onContributionSourcesApply = viewModel::selectFatigueContributionSources
                )
            }
            AnalysisTab.LAB -> item {
                performanceTrend?.let { summary -> AnalysisLabContent(summary) }
                    ?: Text("성과 지표를 계산하고 있습니다.")
            }
        }
    }
}
